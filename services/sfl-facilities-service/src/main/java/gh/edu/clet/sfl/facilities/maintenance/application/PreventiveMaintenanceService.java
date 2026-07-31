package gh.edu.clet.sfl.facilities.maintenance.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.facilities.maintenance.application.ports.MaintenanceRepository;
import gh.edu.clet.sfl.facilities.maintenance.domain.PreventiveMaintenanceSchedule;
import gh.edu.clet.sfl.facilities.maintenance.domain.WorkOrder;
import gh.edu.clet.sfl.facilities.maintenance.domain.policy.SlaPolicy;
import gh.edu.clet.sfl.facilities.masterdata.application.ports.FacilitiesRepository;
import gh.edu.clet.sfl.facilities.masterdata.domain.AssetOperationalStatus;
import gh.edu.clet.sfl.facilities.masterdata.domain.FacilityAsset;
import gh.edu.clet.sfl.facilities.masterdata.domain.Site;
import gh.edu.clet.sfl.facilities.shared.application.FacilitiesAuthorization;
import gh.edu.clet.sfl.facilities.shared.application.ServiceOutbox;
import gh.edu.clet.sfl.facilities.shared.application.port.AuditPort;
import gh.edu.clet.sfl.facilities.shared.application.port.IdempotencyPort;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditAction;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.model.EstateCodes;
import gh.edu.clet.sfl.facilities.shared.domain.model.OperatingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Preventive maintenance — the half of S153 that stops faults happening.
 *
 * <h2>The idempotency this class turns on</h2>
 *
 * {@link #generateDueWorkOrders} is called from a scheduler, which is at-least-once. Two runs in one
 * day, a restart mid-run, or a manual trigger next to the scheduled one must not produce two work
 * orders for one service — a technician who arrives to find the generator already serviced yesterday
 * stops trusting the queue, and one who is sent twice costs a vendor call-out.
 *
 * <p>The key is the <em>cycle</em>, not the run:
 * {@link PreventiveMaintenanceSchedule#isDueForGeneration} refuses to generate for a due date already
 * covered, and {@link PreventiveMaintenanceSchedule#markGenerated} advances by the interval from the
 * cycle just generated rather than from today. That second detail matters more than it looks: a
 * generator that ran three days late must not push every subsequent service three days later, or a
 * quarterly inspection drifts out of its quarter inside a year.
 */
@Service
public class PreventiveMaintenanceService {

    private final MaintenanceRepository maintenance;
    private final FacilitiesRepository facilities;
    private final MaintenanceConfiguration configuration;
    private final FacilitiesAuthorization authorization;
    private final AuditPort audit;
    private final IdempotencyPort idempotency;
    private final ServiceOutbox outbox;
    private final Clock clock;

    public PreventiveMaintenanceService(MaintenanceRepository maintenance, FacilitiesRepository facilities,
            MaintenanceConfiguration configuration, FacilitiesAuthorization authorization, AuditPort audit,
            IdempotencyPort idempotency, ServiceOutbox outbox, Clock clock) {
        this.maintenance = maintenance;
        this.facilities = facilities;
        this.configuration = configuration;
        this.authorization = authorization;
        this.audit = audit;
        this.idempotency = idempotency;
        this.outbox = outbox;
        this.clock = clock;
    }

    // =============================================================================================
    // Schedule management
    // =============================================================================================

    @Transactional
    public PreventiveMaintenanceSchedule create(MaintenanceCommands.CreateSchedule command) {
        ActorContext actor = command.actor();
        String siteCode = EstateCodes.normalize(command.siteCode());
        authorization.require(actor, SflPermission.FACILITIES_PM_SCHEDULE_MANAGE, siteCode, command.channel(),
                "PreventiveMaintenanceSchedule", command.scheduleCode());

        if (command.idempotencyKey() != null && !command.idempotencyKey().isBlank()) {
            Optional<PreventiveMaintenanceSchedule> replayed = idempotency
                    .findExistingResult("create-preventive-schedule", command.idempotencyKey(),
                            idempotency.fingerprint(command.idempotencyPayload()))
                    .flatMap(maintenance::findSchedule);
            if (replayed.isPresent()) {
                return replayed.get();
            }
        }

        String scheduleCode = EstateCodes.normalize(command.scheduleCode());
        maintenance.findScheduleByCode(siteCode, scheduleCode).ifPresent(existing -> {
            if (existing.lifecycleStatus().occupiesIdentifier()) {
                throw new FacilitiesException.DuplicateIdentifierException("preventive schedule", scheduleCode,
                        siteCode);
            }
        });

        FacilityAsset asset = requireAsset(command.assetId(), siteCode);
        Instant at = now();
        PreventiveMaintenanceSchedule schedule = maintenance.saveSchedule(
                PreventiveMaintenanceSchedule.create(UUID.randomUUID(), siteCode, scheduleCode, command.name(),
                        command.description(), asset.id(), asset.roomId(), command.intervalDays(),
                        command.leadTimeDays(), command.priority(), command.workOrderType(),
                        command.firstDueOn(), actor.actorId(), at, command.channel(), actor.correlationId()));

        audit.record(actor, command.channel(), AuditAction.PREVENTIVE_SCHEDULE_CREATED,
                "PreventiveMaintenanceSchedule", schedule.id().toString(), schedule.siteCode(), null, schedule);
        outbox.record("sfl.ifimp.preventive-schedule-created.v1", 1, "PreventiveMaintenanceSchedule", schedule.id(),
                schedule.siteCode(), actor.correlationId(), actor.actorId(), schedule);
        idempotency.recordResult("create-preventive-schedule", command.idempotencyKey(),
                idempotency.fingerprint(command.idempotencyPayload()), schedule.id(), schedule.siteCode(),
                actor.actorId());
        return schedule;
    }

    @Transactional
    public PreventiveMaintenanceSchedule update(MaintenanceCommands.UpdateSchedule command) {
        ActorContext actor = command.actor();
        PreventiveMaintenanceSchedule schedule = requireSchedule(command.scheduleId());
        authorization.require(actor, SflPermission.FACILITIES_PM_SCHEDULE_MANAGE, schedule.siteCode(),
                command.channel(), "PreventiveMaintenanceSchedule", schedule.id().toString());
        schedule.metadata().requireVersion(command.expectedVersion(), "Preventive schedule", schedule.id());

        PreventiveMaintenanceSchedule updated = maintenance.saveSchedule(schedule.update(command.name(),
                command.description(), command.intervalDays(), command.leadTimeDays(), command.priority(),
                command.nextDueOn(), actor.actorId(), now(), command.channel(), actor.correlationId()));
        audit.record(actor, command.channel(), AuditAction.PREVENTIVE_SCHEDULE_UPDATED,
                "PreventiveMaintenanceSchedule", updated.id().toString(), updated.siteCode(), schedule,
                updated);
        return updated;
    }

    @Transactional
    public PreventiveMaintenanceSchedule changeLifecycle(MaintenanceCommands.ChangeScheduleLifecycle command) {
        ActorContext actor = command.actor();
        PreventiveMaintenanceSchedule schedule = requireSchedule(command.scheduleId());
        authorization.require(actor, SflPermission.FACILITIES_PM_SCHEDULE_MANAGE, schedule.siteCode(),
                command.channel(), "PreventiveMaintenanceSchedule", schedule.id().toString());
        schedule.metadata().requireVersion(command.expectedVersion(), "Preventive schedule", schedule.id());

        PreventiveMaintenanceSchedule changed = maintenance.saveSchedule(schedule.changeLifecycle(
                command.lifecycleStatus(), actor.actorId(), now(), command.channel(), actor.correlationId()));
        audit.record(actor, command.channel(), AuditAction.PREVENTIVE_SCHEDULE_LIFECYCLE_CHANGED,
                "PreventiveMaintenanceSchedule", changed.id().toString(), changed.siteCode(), schedule,
                changed);
        return changed;
    }

    @Transactional(readOnly = true)
    public List<PreventiveMaintenanceSchedule> list(String siteCode, UUID assetId, ActorContext actor,
            SourceChannel channel) {
        authorization.require(actor, SflPermission.FACILITIES_PM_SCHEDULE_READ, channel,
                "PreventiveMaintenanceSchedule", "list", siteCode);
        authorization.requireRequestedSite(actor, siteCode, channel, "PreventiveMaintenanceSchedule");
        return authorization.filterBySite(actor, maintenance.findSchedules(siteCode, assetId),
                PreventiveMaintenanceSchedule::siteCode);
    }

    @Transactional(readOnly = true)
    public PreventiveMaintenanceSchedule findById(UUID id, ActorContext actor, SourceChannel channel) {
        PreventiveMaintenanceSchedule schedule = requireSchedule(id);
        authorization.require(actor, SflPermission.FACILITIES_PM_SCHEDULE_READ, schedule.siteCode(), channel,
                "PreventiveMaintenanceSchedule", id.toString());
        return schedule;
    }

    // =============================================================================================
    // Generation
    // =============================================================================================

    /**
     * Raises work orders for every schedule inside its lead-time window.
     *
     * <p>Runs as the system actor, because nobody asked for it. That is also why each generated order
     * carries {@link SourceChannel#SCHEDULER}: a reader of the audit trail should be able to tell work
     * the estate scheduled for itself from work a person raised.
     *
     * @return the orders raised. Empty is the ordinary answer on most days.
     */
    @Transactional
    public List<WorkOrder> generateDueWorkOrders(ActorContext systemActor, LocalDate today) {
        int batch = configuration.generationBatchSize(null);
        List<PreventiveMaintenanceSchedule> due = maintenance.findSchedulesDueForGeneration(today, batch);
        List<WorkOrder> generated = new ArrayList<>();
        Instant at = now();

        for (PreventiveMaintenanceSchedule schedule : due) {
            // Re-checked in the aggregate as well as in the query. The query narrows by date; only the
            // schedule knows whether this cycle has already been generated for, and that is the check
            // that makes a second run in the same window a no-op.
            if (!schedule.isDueForGeneration(today)) {
                continue;
            }
            Optional<FacilityAsset> asset = facilities.findAsset(schedule.assetId());
            if (asset.isEmpty() || !servicable(asset.get())) {
                // A schedule against a retired asset stops generating rather than failing the whole
                // run. It stays on the register so somebody can see it and retire it too — deleting
                // it here would hide the fact that a service was being planned for a machine that is
                // gone, which is exactly what an audit of a missed inspection would want to find.
                continue;
            }

            SlaPolicy sla = configuration.slaPolicyFor(schedule.siteCode());
            Instant slaDue = sla.resolutionDueFrom(at, schedule.priority(),
                    operatingModeOf(schedule.siteCode()));
            Instant responseDue = sla.responseDueFrom(at, schedule.priority(),
                    operatingModeOf(schedule.siteCode()));
            int evidenceRequired = configuration.evidenceRequiredFor(schedule.siteCode(),
                    schedule.priority());

            WorkOrder order = maintenance.saveWorkOrder(WorkOrder.planned(UUID.randomUUID(),
                    maintenance.nextWorkOrderNumber(schedule.siteCode()), schedule.workOrderType(),
                    schedule.id(), schedule.siteCode(), schedule.roomId(), asset.get().locationCode(),
                    schedule.assetId(),
                    schedule.name() + " — due " + schedule.nextDueOn(),
                    schedule.description(), schedule.priority(), slaDue, responseDue, evidenceRequired,
                    systemActor.actorId(), at, SourceChannel.SCHEDULER, systemActor.correlationId()));

            maintenance.saveSchedule(schedule.markGenerated(order.id(), at, systemActor.actorId(),
                    SourceChannel.SCHEDULER, systemActor.correlationId()));

            audit.record(systemActor, SourceChannel.SCHEDULER, AuditAction.PREVENTIVE_WORK_ORDER_GENERATED,
                    "WorkOrder", order.id().toString(), order.siteCode(), null, order);
            outbox.record("sfl.ifimp.work-order-created.v1", 1, "WorkOrder", order.id(), order.siteCode(),
                    systemActor.correlationId(), systemActor.actorId(), order);
            generated.add(order);
        }
        return generated;
    }

    // =============================================================================================
    // Internals
    // =============================================================================================

    /**
     * Whether an asset is still worth servicing.
     *
     * <p>Two conditions, because the estate model has two ways of retiring a thing: the record can be
     * archived, and the machine itself can be decommissioned. A schedule that kept generating for
     * either would send a technician to service equipment that has left the building.
     */
    private static boolean servicable(FacilityAsset asset) {
        return asset.lifecycleStatus().isOperational()
                && asset.operationalStatus() != AssetOperationalStatus.DECOMMISSIONED;
    }

    private FacilityAsset requireAsset(UUID assetId, String siteCode) {
        FacilityAsset asset = facilities.findAsset(assetId)
                .orElseThrow(() -> new FacilitiesException.InvalidParentReferenceException("Asset", assetId));
        if (!asset.siteCode().equals(siteCode)) {
            throw new FacilitiesException.ValidationFailedException(
                    "Asset " + asset.assetCode() + " is not at site " + siteCode + ".");
        }
        return asset;
    }

    private PreventiveMaintenanceSchedule requireSchedule(UUID id) {
        return maintenance.findSchedule(id)
                .orElseThrow(() -> new FacilitiesException.RecordNotFoundException("Preventive schedule", id));
    }

    private OperatingMode operatingModeOf(String siteCode) {
        return facilities.findSiteByCode(siteCode).map(Site::operatingMode).orElse(OperatingMode.ROUTINE);
    }

    private Instant now() {
        return clock.instant();
    }

    /** Today at the service's clock, in UTC — the date the generator reasons about. */
    public LocalDate today() {
        return now().atZone(ZoneOffset.UTC).toLocalDate();
    }
}
