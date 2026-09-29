package gh.edu.clet.sfl.facilities.buildingsystems.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.facilities.buildingsystems.application.ports.BuildingSystemsRepository;
import gh.edu.clet.sfl.facilities.buildingsystems.application.ports.BuildingWorkOrderPort;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.AlertPriority;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.AlertStatus;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.AlertType;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.BmsAlert;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.BmsDevice;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.ChannelState;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.CriticalFaultType;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.DeviceStatus;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.MeasuredQuantity;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.RuleCondition;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.TelemetryReading;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.ThresholdRule;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.policy.CriticalFaultPolicy;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.policy.RuleEvaluationPolicy;
import gh.edu.clet.sfl.facilities.shared.application.FacilitiesAuthorization;
import gh.edu.clet.sfl.facilities.shared.application.ServiceOutbox;
import gh.edu.clet.sfl.facilities.shared.application.port.AuditPort;
import gh.edu.clet.sfl.facilities.shared.application.port.SecurityEventForwarderPort;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditAction;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.security.SecurityEvent;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns accepted readings into alerts and work orders - SRS-SFL-S156-02, -03.
 *
 * <h2>The workflow the SRS gives, step for step</h2>
 *
 * <p>"Telemetry breaches configured threshold → debounce window elapses → correlation check against open
 * work orders → new or linked S153 work order raised → facilities/maintenance notified."
 *
 * <ol>
 *   <li><strong>Breach.</strong> {@link RuleEvaluationPolicy} picks the stricter rule when rules overlap.</li>
 *   <li><strong>Debounce.</strong> The first breaching reading starts a clock on the channel's state; a
 *       later breaching reading, or the sustained-breach sweep, raises once the rule's window has passed.
 *       A reading back in range before then resets the clock and nothing is raised - no alert row, no
 *       event, no work order. That is what "debounced against transient noise" means here.</li>
 *   <li><strong>Correlation.</strong> Before raising, the device's alerts that carry a work order are
 *       checked against S153 itself ({@code BuildingWorkOrderPort.find}) - not against a status S156
 *       remembers, because a technician may have closed the order since. Open means link; closed means a
 *       new alert and a new order.</li>
 *   <li><strong>Work order.</strong> Raised through S153's automated intake with category
 *       {@code BMS_TELEMETRY}, the triggering reading ids as evidence, the rule's suggested priority, and
 *       idempotency key {@code bms-alert:<alertId>} so a retried transaction replays the same order.</li>
 *   <li><strong>Notified.</strong> {@code bms-alert-raised} / {@code bms-alert-correlated} through the
 *       outbox; S153 itself notifies the maintenance queue as it does for any work order.</li>
 * </ol>
 *
 * <h2>Critical faults skip steps two and three</h2>
 *
 * <p>Total power loss, lift entrapment and generator failed-start during an outage are recognised by
 * {@link CriticalFaultPolicy} on the reading that shows them, with no debounce and no correlation: a
 * second entrapment must never be folded into an earlier work order and lost. They publish
 * {@code building-critical-fault-detected}, go to the SIEM, are audited as
 * {@code BMS_CRITICAL_FAULT_ESCALATED}, and still raise a CRITICAL work order. Repeated readings of the
 * same critical condition do not re-escalate until it has cleared.
 *
 * <p>Everything here joins the caller's transaction: an alert that was recorded but whose work order then
 * failed rolls back with it, and the vendor's retry replays the whole message.
 */
@Service
public class BuildingAlertService {

    static final String WORK_ORDER_CATEGORY = "BMS_TELEMETRY";

    private final BuildingSystemsRepository repository;
    private final BuildingSystemsConfiguration configuration;
    private final BuildingWorkOrderPort workOrders;
    private final FacilitiesAuthorization authorization;
    private final AuditPort audit;
    private final ServiceOutbox outbox;
    private final SecurityEventForwarderPort siem;
    private final Clock clock;

    public BuildingAlertService(BuildingSystemsRepository repository, BuildingSystemsConfiguration configuration,
            BuildingWorkOrderPort workOrders, FacilitiesAuthorization authorization, AuditPort audit,
            ServiceOutbox outbox, SecurityEventForwarderPort siem, Clock clock) {
        this.repository = repository;
        this.configuration = configuration;
        this.workOrders = workOrders;
        this.authorization = authorization;
        this.audit = audit;
        this.outbox = outbox;
        this.siem = siem;
        this.clock = clock;
    }

    // =============================================================================================
    // Per reading - called by TelemetryIngestionService inside its transaction
    // =============================================================================================

    /**
     * Evaluates one accepted, resolved reading and returns the channel state after it (already saved).
     *
     * @param state the channel's state with this reading already observed into it
     */
    ChannelState evaluate(BmsDevice device, ChannelState state, TelemetryReading reading, ActorContext actor,
            SourceChannel channel) {
        ChannelState current = evaluateCritical(device, state, reading, actor, channel);
        current = evaluateRules(device, current, reading, actor, channel);
        return repository.saveChannelState(current);
    }

    /** A reading arrived from a device with an active offline alert: it is back. */
    void deviceReporting(BmsDevice device, ActorContext actor, SourceChannel channel) {
        repository.findActiveAlert(device.id(), AlertType.SENSOR_OFFLINE).ifPresent(offline -> {
            BmsAlert cleared = repository.saveAlert(offline.clear(actor.actorId(), clock.instant(), channel,
                    actor.correlationId()));
            audit.record(actor, channel, AuditAction.BMS_SENSOR_RECOVERED, "BmsAlert", cleared.id().toString(),
                    cleared.siteCode(), offline, cleared);
            BuildingSystemsEvents.publish(outbox, BuildingSystemsEvents.ALERT_CLEARED, "BmsAlert", cleared.id(),
                    cleared.siteCode(), actor, BuildingSystemsEvents.alertPayload(cleared));
        });
    }

    private ChannelState evaluateCritical(BmsDevice device, ChannelState state, TelemetryReading reading,
            ActorContext actor, SourceChannel channel) {
        boolean outage = reading.quantity() == MeasuredQuantity.GENERATOR_RUN_STATE && outageInProgress(device);
        Optional<CriticalFaultType> critical = CriticalFaultPolicy.classify(device.systemType(), reading.quantity(),
                reading.value(), configuration.liftEntrapmentCodes(device.siteCode()), outage);
        Optional<BmsAlert> active = Optional.ofNullable(state.criticalAlertId())
                .flatMap(repository::findAlert)
                .filter(BmsAlert::isActive);
        if (critical.isPresent()) {
            if (active.isPresent()) {
                return state;
            }
            BmsAlert alert = escalateCritical(device, critical.get(), reading, actor, channel);
            return state.withCriticalAlert(alert.id());
        }
        active.ifPresent(alert -> clear(alert, actor, channel));
        return state.criticalAlertId() == null ? state : state.withCriticalAlert(null);
    }

    private ChannelState evaluateRules(BmsDevice device, ChannelState state, TelemetryReading reading,
            ActorContext actor, SourceChannel channel) {
        RuleEvaluationPolicy.DeviceFacts facts = new RuleEvaluationPolicy.DeviceFacts(device.id(),
                device.systemType(), device.buildingCode(), device.roomId());
        Optional<RuleEvaluationPolicy.Breach> breach = RuleEvaluationPolicy.evaluate(facts, reading.quantity(),
                reading.value(), repository.findCurrentRules(device.siteCode()));
        if (breach.isEmpty()) {
            if (state.alertId() != null) {
                repository.findAlert(state.alertId()).filter(BmsAlert::isActive)
                        .ifPresent(alert -> clear(alert, actor, channel));
            }
            // Back in range. A breach that never outlasted its debounce disappears here without trace -
            // the transient case, by construction.
            return state.inBreach() || state.alertId() != null ? state.breachCleared() : state;
        }
        ThresholdRule rule = breach.get().rule();
        ChannelState breaching = state.breach(rule.ruleId(), reading.id(), reading.observedAt());
        if (breaching.alertId() == null
                && sustained(breaching.breachStartedAt(), reading.observedAt(), rule.debounce())) {
            return promote(device, breaching, rule, breach.get().alertType(), breach.get().description(), actor,
                    channel);
        }
        return breaching;
    }

    // =============================================================================================
    // Sweeps - on platform threads from BuildingSystemsScheduledJobs
    // =============================================================================================

    /**
     * Raises breaches that have outlasted their debounce window with no further reading to notice it.
     *
     * <p>A sensor reporting every fifteen minutes against a five-minute debounce would otherwise wait for
     * its next reading to raise. The last reading was a breach and nothing has said otherwise since, so the
     * breach is sustained on the evidence available. A rule disabled in the meantime raises nothing.
     */
    @Transactional
    public int promoteSustainedBreaches(ActorContext actor) {
        Instant now = clock.instant();
        int raised = 0;
        for (ChannelState state : repository.findPendingBreaches(configuration.sweepBatch(null))) {
            Optional<ThresholdRule> rule = repository.findCurrentRule(state.breachRuleId())
                    .filter(ThresholdRule::isActive);
            Optional<BmsDevice> device = repository.findDevice(state.deviceId()).filter(BmsDevice::isActive);
            if (rule.isEmpty() || device.isEmpty()) {
                repository.saveChannelState(state.breachCleared());
                continue;
            }
            if (!sustained(state.breachStartedAt(), now, rule.get().debounce())) {
                continue;
            }
            AlertType type = rule.get().condition() == RuleCondition.CODE_MATCH ? AlertType.FAULT_CODE
                    : AlertType.THRESHOLD_BREACH;
            String description = state.quantity() + " on " + state.channel() + " has breached rule '"
                    + rule.get().name() + "' v" + rule.get().ruleVersion() + " since " + state.breachStartedAt();
            repository.saveChannelState(promote(device.get(), state, rule.get(), type, description, actor,
                    SourceChannel.SCHEDULER));
            raised++;
        }
        return raised;
    }

    /**
     * Raises an offline alert for every active device silent for longer than the offline window -
     * SRS-SFL-S156-03: "a sensor/gateway offline for longer than a configured window raises its own alert
     * distinct from a telemetry fault".
     *
     * <p>A device that has never reported is measured from its registration: a gateway installed on Monday
     * and silent since is offline, not "not yet started". No work order is raised - an offline alert is a
     * connectivity problem for the IoT engineer, surfaced on the dashboard and as an event; the gap report
     * records that as a decision a site may want to reverse.
     */
    @Transactional
    public int sweepOfflineDevices(ActorContext actor) {
        Instant now = clock.instant();
        List<BmsDevice> devices = repository.findDevices(null, DeviceStatus.ACTIVE);
        Map<UUID, Instant> lastHeard = repository.findChannelStates(devices.stream().map(BmsDevice::id).toList())
                .stream()
                .filter(state -> state.lastReceivedAt() != null)
                .collect(Collectors.toMap(ChannelState::deviceId, ChannelState::lastReceivedAt,
                        (left, right) -> left.isAfter(right) ? left : right));
        int raised = 0;
        for (BmsDevice device : devices) {
            Instant reference = lastHeard.getOrDefault(device.id(), device.metadata().createdAt());
            Duration window = configuration.offlineWindow(device.siteCode());
            if (!reference.plus(window).isBefore(now)
                    || repository.findActiveAlert(device.id(), AlertType.SENSOR_OFFLINE).isPresent()) {
                continue;
            }
            BmsAlert alert = repository.saveAlert(BmsAlert.raise(UUID.randomUUID(), device, AlertType.SENSOR_OFFLINE,
                    null, null, AlertPriority.MEDIUM,
                    device.kind() + " " + device.deviceCode() + " has sent no telemetry since " + reference
                            + " (offline window " + window + ")",
                    List.of(), actor.actorId(), now, SourceChannel.SCHEDULER, actor.correlationId()));
            audit.record(actor, SourceChannel.SCHEDULER, AuditAction.BMS_SENSOR_OFFLINE_DETECTED, "BmsAlert",
                    alert.id().toString(), alert.siteCode(), null, alert);
            Map<String, Object> payload = BuildingSystemsEvents.alertPayload(alert);
            payload.put("lastHeardAt", reference.toString());
            payload.put("deviceKind", device.kind().name());
            BuildingSystemsEvents.publish(outbox, BuildingSystemsEvents.SENSOR_OFFLINE, "BmsAlert", alert.id(),
                    alert.siteCode(), actor, payload);
            raised++;
        }
        return raised;
    }

    // =============================================================================================
    // Queries
    // =============================================================================================

    @Transactional(readOnly = true)
    public List<BmsAlert> alerts(String siteCode, AlertStatus status, ActorContext actor, SourceChannel channel) {
        authorization.require(actor, SflPermission.FACILITIES_BMS_READ, channel, "BmsAlert", "list", siteCode);
        authorization.requireRequestedSite(actor, siteCode, channel, "BmsAlert");
        return authorization.filterBySite(actor, repository.findAlerts(siteCode, status), BmsAlert::siteCode);
    }

    @Transactional(readOnly = true)
    public BmsAlert alert(UUID alertId, ActorContext actor, SourceChannel channel) {
        BmsAlert alert = repository.findAlert(alertId)
                .orElseThrow(() -> new FacilitiesException.RecordNotFoundException("BmsAlert", alertId));
        authorization.require(actor, SflPermission.FACILITIES_BMS_READ, alert.siteCode(), channel, "BmsAlert",
                alertId.toString());
        return alert;
    }

    // =============================================================================================
    // Internals
    // =============================================================================================

    private ChannelState promote(BmsDevice device, ChannelState state, ThresholdRule rule, AlertType type,
            String description, ActorContext actor, SourceChannel channel) {
        List<UUID> evidence = state.breachReadingIds();
        Instant at = clock.instant();
        for (BmsAlert existing : repository.findAlertsWithWorkOrder(device.id())) {
            if (existing.type() == AlertType.SENSOR_OFFLINE) {
                continue;
            }
            boolean open = workOrders.find(existing.workOrderId())
                    .map(BuildingWorkOrderPort.WorkOrderReference::open)
                    .orElse(false);
            if (open) {
                BmsAlert correlated = repository.saveAlert(existing.correlate(evidence, actor.actorId(), at, channel,
                        actor.correlationId()));
                repository.holdAsEvidence(evidence);
                Map<String, Object> facts = BuildingSystemsEvents.alertPayload(correlated);
                facts.put("correlatedRuleId", rule.ruleId().toString());
                facts.put("correlatedRuleVersion", rule.ruleVersion());
                audit.record(actor, channel, AuditAction.BMS_ALERT_CORRELATED, "BmsAlert", correlated.id().toString(),
                        correlated.siteCode(), existing, facts);
                BuildingSystemsEvents.publish(outbox, BuildingSystemsEvents.ALERT_CORRELATED, "BmsAlert",
                        correlated.id(), correlated.siteCode(), actor, facts);
                return state.withAlert(correlated.id());
            }
        }
        BmsAlert alert = raiseWithWorkOrder(BmsAlert.raise(UUID.randomUUID(), device, type, null, rule,
                rule.priority(), description, evidence, actor.actorId(), at, channel, actor.correlationId()),
                description, actor, channel);
        return state.withAlert(alert.id());
    }

    private BmsAlert escalateCritical(BmsDevice device, CriticalFaultType critical, TelemetryReading reading,
            ActorContext actor, SourceChannel channel) {
        Instant at = clock.instant();
        String summary = critical + " at " + device.buildingCode()
                + (device.roomCode() == null ? "" : "/" + device.roomCode()) + " reported by " + device.deviceCode()
                + " (" + reading.quantity() + " = " + reading.value().toPlainString() + ")";
        BmsAlert alert = raiseWithWorkOrder(BmsAlert.raise(UUID.randomUUID(), device, AlertType.CRITICAL_FAULT,
                critical, null, AlertPriority.CRITICAL, summary, List.of(reading.id()), actor.actorId(), at, channel,
                actor.correlationId()), summary, actor, channel);

        Map<String, Object> payload = BuildingSystemsEvents.alertPayload(alert);
        payload.put("readingId", reading.id().toString());
        payload.put("observedAt", reading.observedAt().toString());
        payload.put("fastLane", "S162a/S174");
        audit.record(actor, channel, AuditAction.BMS_CRITICAL_FAULT_ESCALATED, "BmsAlert", alert.id().toString(),
                alert.siteCode(), null, payload);
        BuildingSystemsEvents.publish(outbox, BuildingSystemsEvents.CRITICAL_FAULT_DETECTED, "BmsAlert", alert.id(),
                alert.siteCode(), actor, payload);
        siem.forward(new SecurityEvent("S156", "BMS_CRITICAL_FAULT", SecurityEvent.Severity.HIGH, alert.siteCode(),
                critical + " at " + alert.buildingCode(), alert.id().toString(), at));
        return alert;
    }

    private BmsAlert raiseWithWorkOrder(BmsAlert raised, String description, ActorContext actor,
            SourceChannel channel) {
        BmsAlert alert = repository.saveAlert(raised);
        repository.holdAsEvidence(alert.evidenceReadingIds());
        String evidence = "bms-readings:" + alert.evidenceReadingIds().stream().map(UUID::toString)
                .collect(Collectors.joining(","));
        BuildingWorkOrderPort.WorkOrderReference order = workOrders.raise(new BuildingWorkOrderPort.WorkOrderRequest(
                alert.siteCode(), alert.roomId(), alert.locationCode(),
                title(alert), description + ". System " + alert.systemType() + ", location "
                        + alert.locationCode() + ", device " + alert.deviceCode() + " (AVAMP " + alert.avampAssetId()
                        + ").",
                alert.priority(), alert.id(), evidence, actor.correlationId()));
        BmsAlert linked = repository.saveAlert(alert.linkWorkOrder(order.workOrderId(), order.workOrderNumber()));
        audit.record(actor, channel, AuditAction.BMS_ALERT_RAISED, "BmsAlert", linked.id().toString(),
                linked.siteCode(), null, linked);
        audit.record(actor, channel, AuditAction.BMS_WORK_ORDER_RAISED, "BmsAlert", linked.id().toString(),
                linked.siteCode(), null, Map.of("workOrderId", order.workOrderId().toString(),
                        "workOrderNumber", String.valueOf(order.workOrderNumber()), "evidenceReference", evidence));
        BuildingSystemsEvents.publish(outbox, BuildingSystemsEvents.ALERT_RAISED, "BmsAlert", linked.id(),
                linked.siteCode(), actor, BuildingSystemsEvents.alertPayload(linked));
        return linked;
    }

    private void clear(BmsAlert alert, ActorContext actor, SourceChannel channel) {
        BmsAlert cleared = repository.saveAlert(alert.clear(actor.actorId(), clock.instant(), channel,
                actor.correlationId()));
        audit.record(actor, channel, AuditAction.BMS_ALERT_CLEARED, "BmsAlert", cleared.id().toString(),
                cleared.siteCode(), alert, cleared);
        BuildingSystemsEvents.publish(outbox, BuildingSystemsEvents.ALERT_CLEARED, "BmsAlert", cleared.id(),
                cleared.siteCode(), actor, BuildingSystemsEvents.alertPayload(cleared));
    }

    /** Whether the building has a total-power-loss signal recent enough to call this an outage. */
    private boolean outageInProgress(BmsDevice device) {
        Instant since = clock.instant().minus(configuration.outageWindow(device.siteCode()));
        return repository.findBuildingChannels(device.siteCode(), device.buildingCode(), MeasuredQuantity.POWER_STATE)
                .stream()
                .filter(state -> state.lastObservedAt() != null && !state.lastObservedAt().isBefore(since))
                .anyMatch(state -> CriticalFaultPolicy.indicatesOutage(state.systemType(), state.quantity(),
                        state.lastValue()));
    }

    private static boolean sustained(Instant startedAt, Instant at, Duration debounce) {
        return startedAt != null && !Duration.between(startedAt, at).minus(debounce).isNegative();
    }

    private static String title(BmsAlert alert) {
        String what = alert.type() == AlertType.CRITICAL_FAULT ? String.valueOf(alert.criticalFault())
                : alert.type().name().replace('_', ' ');
        return "BMS " + what + " - " + alert.deviceCode() + " at " + alert.locationCode();
    }
}
