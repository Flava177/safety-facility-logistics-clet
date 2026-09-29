package gh.edu.clet.sfl.facilities.buildingsystems.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.facilities.buildingsystems.application.BuildingSystemsCommands.IngestionResult;
import gh.edu.clet.sfl.facilities.buildingsystems.application.BuildingSystemsCommands.ItemOutcome;
import gh.edu.clet.sfl.facilities.buildingsystems.application.BuildingSystemsCommands.ItemResult;
import gh.edu.clet.sfl.facilities.buildingsystems.application.contract.BuildingTelemetryObserver;
import gh.edu.clet.sfl.facilities.buildingsystems.application.contract.MeasurementKind;
import gh.edu.clet.sfl.facilities.buildingsystems.application.contract.NormalisedTelemetryReading;
import gh.edu.clet.sfl.facilities.buildingsystems.application.ports.BmsTelemetryTranslatorPort;
import gh.edu.clet.sfl.facilities.buildingsystems.application.ports.BmsTelemetryTranslatorPort.TranslatedReading;
import gh.edu.clet.sfl.facilities.buildingsystems.application.ports.BuildingSystemsRepository;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.BmsDevice;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.ChannelState;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.MeasuredQuantity;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.QuarantineReason;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.QuarantineStatus;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.QuarantinedReading;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.TelemetryReading;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.policy.TelemetryValidationPolicy;
import gh.edu.clet.sfl.facilities.shared.application.FacilitiesAuthorization;
import gh.edu.clet.sfl.facilities.shared.application.ServiceOutbox;
import gh.edu.clet.sfl.facilities.shared.application.port.AuditPort;
import gh.edu.clet.sfl.facilities.shared.application.vendor.VendorMessageVerifier;
import gh.edu.clet.sfl.facilities.shared.application.vendor.VerifiedVendorMessage;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditAction;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesErrorCode;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import gh.edu.clet.sfl.facilities.shared.domain.security.VendorChannel;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authenticated BMS/IoT telemetry ingestion and normalisation - SRS-SFL-S156-01, with the S156-04
 * unregistered-device rule.
 *
 * <h2>The pipeline, in order</h2>
 *
 * <ol>
 *   <li><strong>Permission.</strong> {@code FACILITIES_BMS_TELEMETRY_INGEST}, held only by integration
 *       principals - a person who could post telemetry could fabricate a breach and raise a work order.</li>
 *   <li><strong>Authentication, allowlist, schema, replay</strong> - {@link VendorMessageVerifier} on
 *       {@link VendorChannel#BMS_TELEMETRY}. A forged or unauthenticated message stops here: rejected,
 *       logged, audited, forwarded to the SIEM, and nothing below runs, so no reading, alert or work
 *       order can follow from it (the S156-01 acceptance criterion and NFR-SEC2).</li>
 *   <li><strong>Translation</strong> through the adapter the message's {@code format} names
 *       ({@link BmsTelemetryTranslatorPort}) into SFL's common reading model. A body the adapter cannot
 *       read is a schema rejection, recorded through {@link VendorMessageVerifier#reject}.</li>
 *   <li>Per reading: <strong>registered device</strong> (else quarantine, flagged for registration),
 *       <strong>S152 location</strong> (else quarantine pending mapping), <strong>plausibility and
 *       ordering</strong> (else quarantine - flagged, not stored as fact, never evaluated).</li>
 *   <li><strong>Stored as fact</strong>, the channel watermark moved, every
 *       {@link BuildingTelemetryObserver} told (S157 consumes here), then <strong>evaluated</strong> by
 *       {@link BuildingAlertService}.</li>
 * </ol>
 *
 * <p>One transaction for the whole message: the inbox claim, every reading, every quarantine row, every
 * alert and work order commit together or not at all, so a failure half-way leaves the vendor's retry to
 * replay a clean slate. Quarantine is an outcome, not an exception - throwing would roll the quarantine row
 * back and turn "held for review" into "dropped", which is the one thing S156-01 forbids.
 *
 * <p>An accepted reading is not audited row by row. It is an immutable fact carrying its inbox id, source
 * and provenance columns; auditing each would turn the hash chain into a second telemetry store. Every
 * <em>decision</em> made about telemetry - quarantine, alert, work order, escalation - is audited.
 */
@Service
public class TelemetryIngestionService {

    private static final String MODULE = "S156";
    private static final List<String> REQUIRED_FIELDS = List.of("format");

    private final BuildingSystemsRepository repository;
    private final BuildingSystemsConfiguration configuration;
    private final Map<String, BmsTelemetryTranslatorPort> translators;
    private final VendorMessageVerifier verifier;
    private final S152LocationResolver locations;
    private final BuildingAlertService alerts;
    private final List<BuildingTelemetryObserver> observers;
    private final FacilitiesAuthorization authorization;
    private final AuditPort audit;
    private final ServiceOutbox outbox;
    private final Clock clock;

    /**
     * @param translators every vendor adapter on the classpath. Two with the same format is a deployment
     *        error and fails at startup rather than choosing one silently
     * @param observers S157 and anything else consuming the normalised stream. Empty is a valid deployment
     */
    @Autowired
    public TelemetryIngestionService(BuildingSystemsRepository repository, BuildingSystemsConfiguration configuration,
            List<BmsTelemetryTranslatorPort> translators, VendorMessageVerifier verifier,
            S152LocationResolver locations, BuildingAlertService alerts, List<BuildingTelemetryObserver> observers,
            FacilitiesAuthorization authorization, AuditPort audit, ServiceOutbox outbox, Clock clock) {
        this.repository = repository;
        this.configuration = configuration;
        this.translators = translators.stream().collect(Collectors.toMap(
                translator -> translator.format().strip().toLowerCase(Locale.ROOT), translator -> translator,
                (left, right) -> {
                    throw new IllegalStateException("Two BMS telemetry adapters claim format " + left.format());
                }, LinkedHashMap::new));
        this.verifier = verifier;
        this.locations = locations;
        this.alerts = alerts;
        this.observers = List.copyOf(observers);
        this.authorization = authorization;
        this.audit = audit;
        this.outbox = outbox;
        this.clock = clock;
    }

    /** The formats this deployment can read - the runbook's first check after adding a vendor. */
    public List<String> supportedFormats() {
        return List.copyOf(translators.keySet());
    }

    @Transactional
    public IngestionResult ingest(BuildingSystemsCommands.IngestTelemetry command) {
        ActorContext actor = command.actor();
        SourceChannel channel = command.channel() == null ? SourceChannel.INTEGRATION : command.channel();
        authorization.require(actor, SflPermission.FACILITIES_BMS_TELEMETRY_INGEST, channel, "BmsTelemetry",
                "ingest", command.message().siteCode());

        VerifiedVendorMessage verified = verifier.accept(command.message(), VendorChannel.BMS_TELEMETRY,
                REQUIRED_FIELDS, MODULE, actor);
        authorization.requireSite(actor, verified.siteCode(), channel, "BmsTelemetry", verified.inboxId().toString());
        if (verified.duplicate()) {
            return replay(verified);
        }

        String format = verified.text("format").strip();
        BmsTelemetryTranslatorPort translator = translators.get(format.toLowerCase(Locale.ROOT));
        if (translator == null) {
            throw verifier.reject(verified, "Unknown telemetry format '" + format + "'", MODULE, actor);
        }
        List<TranslatedReading> points;
        try {
            points = translator.translate(verified.payload());
        } catch (BmsTelemetryTranslatorPort.TelemetryTranslationException malformed) {
            throw verifier.reject(verified, malformed.getMessage(), MODULE, actor);
        }
        if (points == null || points.isEmpty()) {
            throw verifier.reject(verified, "Message carries no readings", MODULE, actor);
        }

        List<ItemResult> items = new ArrayList<>();
        for (int index = 0; index < points.size(); index++) {
            items.add(process(verified, translator.format(), index, points.get(index), actor, channel));
        }
        return new IngestionResult(verified.inboxId(), verified.sourceId(), verified.siteCode(), false, items);
    }

    // =============================================================================================
    // Quarantine review - S156-01 "quarantined for review", S156-04 "flagged for registration"
    // =============================================================================================

    @Transactional(readOnly = true)
    public List<QuarantinedReading> quarantine(String siteCode, QuarantineStatus status, ActorContext actor,
            SourceChannel channel) {
        authorization.require(actor, SflPermission.FACILITIES_BMS_READ, channel, "BmsQuarantine", "list", siteCode);
        authorization.requireRequestedSite(actor, siteCode, channel, "BmsQuarantine");
        return authorization.filterBySite(actor, repository.findQuarantine(siteCode, status),
                QuarantinedReading::siteCode);
    }

    /**
     * Releases a quarantined reading as fact once its device is registered or its location mapped.
     *
     * <p>Resolution happens elsewhere - registering the device (S156-04) or relocating it to a live S152
     * space - and this re-runs the checks rather than trusting the reviewer's word that it is fixed. A
     * released reading is stored and offered to observers, but <em>not</em> evaluated against threshold
     * rules and does not move the channel watermark backwards: it is history arriving late, and raising a
     * work order today for a breach that ended hours ago would send a technician to a condition that no
     * longer exists. The gap report records the trade.
     */
    @Transactional
    public QuarantinedReading release(BuildingSystemsCommands.ReleaseQuarantine command) {
        ActorContext actor = command.actor();
        QuarantinedReading held = requireQuarantine(command.quarantineId());
        authorization.require(actor, SflPermission.FACILITIES_BMS_QUARANTINE_RESOLVE, held.siteCode(),
                command.channel(), "BmsQuarantine", held.id().toString());
        if (!held.reason().releasable()) {
            throw new FacilitiesException.InvalidStateTransitionException("A reading quarantined as " + held.reason()
                    + " is not trusted as fact and can only be discarded with a reason");
        }
        BmsDevice device = repository.findActiveDeviceByCode(held.siteCode(), held.deviceCode())
                .orElseThrow(() -> new FacilitiesException.InvalidStateTransitionException(
                        FacilitiesErrorCode.BMS_DEVICE_UNREGISTERED.defaultMessage()
                                + " Register device " + held.deviceCode() + " before releasing."));
        S152LocationResolver.ResolvedLocation location = locations
                .resolve(device.siteCode(), device.buildingCode(), device.roomId())
                .orElseThrow(() -> new FacilitiesException.InvalidStateTransitionException(
                        FacilitiesErrorCode.BMS_LOCATION_UNRESOLVABLE.defaultMessage()
                                + " Map device " + device.deviceCode() + " to a live S152 location before releasing."));
        if (!configuration.plausibility(held.quantity(), held.siteCode()).contains(held.value())) {
            throw new FacilitiesException.InvalidStateTransitionException("Value " + held.value().toPlainString()
                    + " is outside the physically plausible band and cannot be released as fact; discard it");
        }
        Instant now = clock.instant();
        TelemetryReading reading = repository.saveReading(new TelemetryReading(UUID.randomUUID(), held.siteCode(),
                device.id(), device.avampAssetId(), device.deviceCode(), location.buildingCode(), location.roomId(),
                location.locationCode(), held.channel(), held.quantity(), held.value(), held.observedAt(),
                held.receivedAt(), held.sourceId(), held.format(), held.idempotencyKey(), held.itemIndex(),
                held.inboxId(), held.id(), false,
                RecordMetadata.createdBy(actor.actorId(), now, command.channel(), actor.correlationId())));
        ChannelState state = repository.findChannelState(device.id(), held.channel())
                .orElseGet(() -> ChannelState.open(UUID.randomUUID(), device, held.channel(), held.quantity(),
                        actor.actorId(), now, command.channel(), actor.correlationId()));
        repository.saveChannelState(state.observe(reading.id(), reading.value(), reading.observedAt(),
                state.lastReceivedAt() == null ? now : state.lastReceivedAt(), actor.actorId(), command.channel(),
                actor.correlationId()));
        notifyObservers(reading);
        QuarantinedReading released = repository.saveQuarantine(held.release(reading.id(), command.note(),
                actor.actorId(), now, command.channel(), actor.correlationId()));
        audit.record(actor, command.channel(), AuditAction.BMS_QUARANTINE_RESOLVED, "BmsQuarantine",
                released.id().toString(), released.siteCode(), held, released);
        return released;
    }

    @Transactional
    public QuarantinedReading discard(BuildingSystemsCommands.DiscardQuarantine command) {
        ActorContext actor = command.actor();
        QuarantinedReading held = requireQuarantine(command.quarantineId());
        authorization.require(actor, SflPermission.FACILITIES_BMS_QUARANTINE_RESOLVE, held.siteCode(),
                command.channel(), "BmsQuarantine", held.id().toString());
        QuarantinedReading discarded = repository.saveQuarantine(held.discard(command.reason(), actor.actorId(),
                clock.instant(), command.channel(), actor.correlationId()));
        audit.record(actor, command.channel(), AuditAction.BMS_QUARANTINE_RESOLVED, "BmsQuarantine",
                discarded.id().toString(), discarded.siteCode(), held, discarded);
        return discarded;
    }

    // =============================================================================================
    // Readings and retention
    // =============================================================================================

    @Transactional(readOnly = true)
    public List<TelemetryReading> readings(BuildingSystemsRepository.ReadingQuery query, ActorContext actor,
            SourceChannel channel) {
        authorization.require(actor, SflPermission.FACILITIES_BMS_READ, channel, "BmsTelemetry", "list",
                query.siteCode());
        authorization.requireRequestedSite(actor, query.siteCode(), channel, "BmsTelemetry");
        return authorization.filterBySite(actor, repository.findReadings(query), TelemetryReading::siteCode);
    }

    /**
     * Deletes readings older than each site's configured retention - SRS-SFL-S156-01: "historical telemetry
     * is retained per the configured retention policy for trend and audit purposes".
     *
     * <p>The retention is runtime configuration and therefore versioned: a shortened retention is a
     * recorded change with an author, not an edit nobody can see. Readings an alert cites are spared - they
     * are the evidence a work order points to. Each site's purge is audited with its count and cutoff.
     */
    @Transactional
    public int purgeExpiredReadings(ActorContext actor) {
        Instant now = clock.instant();
        int total = 0;
        for (String site : repository.findDevices(null, null).stream().map(BmsDevice::siteCode).distinct().toList()) {
            Instant cutoff = now.minus(configuration.readingRetention(site));
            int purged = repository.purgeReadings(site, cutoff);
            if (purged > 0) {
                audit.record(actor, SourceChannel.SCHEDULER, AuditAction.BMS_TELEMETRY_PURGED, "BmsTelemetry", site,
                        site, null, Map.of("purged", purged, "cutoff", cutoff.toString(),
                                "retention", configuration.readingRetention(site).toString()));
                total += purged;
            }
        }
        return total;
    }

    // =============================================================================================
    // Internals
    // =============================================================================================

    private ItemResult process(VerifiedVendorMessage message, String format, int index, TranslatedReading point,
            ActorContext actor, SourceChannel channel) {
        String site = message.siteCode();
        MeasuredQuantity quantity = MeasuredQuantity.valueOf(point.kind().name());
        Optional<BmsDevice> device = repository.findActiveDeviceByCode(site, point.deviceCode());
        if (device.isEmpty()) {
            return quarantine(message, format, index, point, quantity, null, QuarantineReason.DEVICE_UNREGISTERED,
                    "No active AVAMP-registered device '" + point.deviceCode() + "' at " + site
                            + "; flagged for registration", actor, channel);
        }
        Optional<S152LocationResolver.ResolvedLocation> location = locations.resolve(site,
                device.get().buildingCode(), device.get().roomId());
        if (location.isEmpty()) {
            return quarantine(message, format, index, point, quantity, device.get().id(),
                    QuarantineReason.LOCATION_UNRESOLVABLE, "Device " + device.get().deviceCode() + " is mapped to "
                            + device.get().locationCode() + ", which the S152 register does not resolve",
                    actor, channel);
        }
        Instant now = clock.instant();
        ChannelState state = repository.findChannelState(device.get().id(), point.channel())
                .orElseGet(() -> ChannelState.open(UUID.randomUUID(), device.get(), point.channel(), quantity,
                        actor.actorId(), now, channel, actor.correlationId()));
        Optional<TelemetryValidationPolicy.Finding> finding = TelemetryValidationPolicy.check(point.value(),
                configuration.plausibility(quantity, site), point.observedAt(), message.receivedAt(),
                state.lastObservedAt(), configuration.clockSkewTolerance(site));
        if (finding.isPresent()) {
            return quarantine(message, format, index, point, quantity, device.get().id(), finding.get().reason(),
                    finding.get().detail(), actor, channel);
        }

        S152LocationResolver.ResolvedLocation resolved = location.get();
        TelemetryReading reading = repository.saveReading(new TelemetryReading(UUID.randomUUID(), site,
                device.get().id(), device.get().avampAssetId(), device.get().deviceCode(), resolved.buildingCode(),
                resolved.roomId(), resolved.locationCode(), point.channel(), quantity, point.value(),
                point.observedAt(), message.receivedAt(), message.sourceId(), format, message.idempotencyKey(), index,
                message.inboxId(), null, false,
                RecordMetadata.createdBy(actor.actorId(), now, channel, actor.correlationId())));
        ChannelState observed = repository.saveChannelState(state.observe(reading.id(), reading.value(),
                reading.observedAt(), message.receivedAt(), actor.actorId(), channel, actor.correlationId()));
        alerts.deviceReporting(device.get(), actor, channel);
        notifyObservers(reading);
        alerts.evaluate(device.get(), observed, reading, actor, channel);
        return new ItemResult(index, point.deviceCode(), point.channel(), ItemOutcome.ACCEPTED, reading.id(), null,
                null, null);
    }

    private ItemResult quarantine(VerifiedVendorMessage message, String format, int index, TranslatedReading point,
            MeasuredQuantity quantity, UUID deviceId, QuarantineReason reason, String detail, ActorContext actor,
            SourceChannel channel) {
        Instant now = clock.instant();
        QuarantinedReading held = repository.saveQuarantine(new QuarantinedReading(UUID.randomUUID(),
                message.siteCode(), message.sourceId(), format, message.idempotencyKey(), index, message.inboxId(),
                point.deviceCode(), point.channel(), quantity, point.value(), point.observedAt(), message.receivedAt(),
                reason, detail, deviceId, QuarantineStatus.PENDING, null, null, null, null,
                RecordMetadata.createdBy(actor.actorId(), now, channel, actor.correlationId())));
        audit.record(actor, channel, AuditAction.BMS_TELEMETRY_QUARANTINED, "BmsQuarantine", held.id().toString(),
                held.siteCode(), null, held);
        if (reason == QuarantineReason.IMPLAUSIBLE_VALUE) {
            audit.record(actor, channel, AuditAction.BMS_READING_FLAGGED_IMPLAUSIBLE, "BmsQuarantine",
                    held.id().toString(), held.siteCode(), null, Map.of("value", point.value().toPlainString(),
                            "quantity", quantity.name(), "detail", detail));
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("quarantineId", held.id().toString());
        payload.put("siteCode", held.siteCode());
        payload.put("sourceId", held.sourceId());
        payload.put("deviceCode", held.deviceCode());
        payload.put("deviceId", deviceId == null ? null : deviceId.toString());
        payload.put("channel", held.channel());
        payload.put("kind", quantity.name());
        payload.put("reason", reason.name());
        payload.put("flaggedForRegistration", reason == QuarantineReason.DEVICE_UNREGISTERED);
        payload.put("observedAt", point.observedAt().toString());
        BuildingSystemsEvents.publish(outbox, BuildingSystemsEvents.READING_QUARANTINED, "BmsQuarantine", held.id(),
                held.siteCode(), actor, payload);
        return new ItemResult(index, point.deviceCode(), point.channel(), ItemOutcome.QUARANTINED, null, held.id(),
                reason, codeFor(reason));
    }

    /** A duplicate delivery answers exactly as the first did, from what the first one stored. */
    private IngestionResult replay(VerifiedVendorMessage message) {
        List<ItemResult> items = new ArrayList<>();
        repository.findReadingsByMessage(message.sourceId(), message.idempotencyKey()).forEach(reading ->
                items.add(new ItemResult(reading.itemIndex(), reading.deviceCode(), reading.channel(),
                        ItemOutcome.ACCEPTED, reading.id(), null, null, null)));
        repository.findQuarantineByMessage(message.sourceId(), message.idempotencyKey()).forEach(held ->
                items.add(new ItemResult(held.itemIndex(), held.deviceCode(), held.channel(), ItemOutcome.QUARANTINED,
                        held.releasedReadingId(), held.id(), held.reason(), codeFor(held.reason()))));
        items.sort(Comparator.comparingInt(ItemResult::index));
        return new IngestionResult(message.inboxId(), message.sourceId(), message.siteCode(), true, items);
    }

    private void notifyObservers(TelemetryReading reading) {
        if (observers.isEmpty()) {
            return;
        }
        NormalisedTelemetryReading normalised = new NormalisedTelemetryReading(reading.id(), reading.deviceId(),
                reading.avampAssetId(), reading.siteCode(), reading.buildingCode(), reading.roomId(),
                reading.locationCode(), reading.channel(), MeasurementKind.valueOf(reading.quantity().name()),
                reading.value(), reading.observedAt(), reading.receivedAt());
        observers.forEach(observer -> observer.readingAccepted(normalised));
    }

    private QuarantinedReading requireQuarantine(UUID id) {
        return repository.findQuarantine(id)
                .orElseThrow(() -> new FacilitiesException.RecordNotFoundException("BmsQuarantine", id));
    }

    static String codeFor(QuarantineReason reason) {
        return switch (reason) {
            case DEVICE_UNREGISTERED -> FacilitiesErrorCode.BMS_DEVICE_UNREGISTERED.name();
            case LOCATION_UNRESOLVABLE -> FacilitiesErrorCode.BMS_LOCATION_UNRESOLVABLE.name();
            default -> reason.name();
        };
    }
}
