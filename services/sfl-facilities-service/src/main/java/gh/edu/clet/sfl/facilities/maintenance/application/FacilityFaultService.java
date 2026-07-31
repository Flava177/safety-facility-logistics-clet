package gh.edu.clet.sfl.facilities.maintenance.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.facilities.maintenance.application.ports.MaintenanceRepository;
import gh.edu.clet.sfl.facilities.maintenance.domain.FacilityFault;
import gh.edu.clet.sfl.facilities.maintenance.domain.FacilityFaultStatus;
import gh.edu.clet.sfl.facilities.maintenance.domain.FaultPriority;
import gh.edu.clet.sfl.facilities.maintenance.domain.policy.FaultReadinessPolicy;
import gh.edu.clet.sfl.facilities.maintenance.domain.policy.SlaPolicy;
import gh.edu.clet.sfl.facilities.masterdata.application.ports.FacilitiesRepository;
import gh.edu.clet.sfl.facilities.masterdata.domain.FacilityRoom;
import gh.edu.clet.sfl.facilities.masterdata.domain.Site;
import gh.edu.clet.sfl.facilities.readiness.application.ports.ExternalBlockerPort;
import gh.edu.clet.sfl.facilities.readiness.domain.BlockerSeverity;
import gh.edu.clet.sfl.facilities.readiness.domain.BlockerSource;
import gh.edu.clet.sfl.facilities.shared.application.FacilitiesAuthorization;
import gh.edu.clet.sfl.facilities.shared.application.ServiceOutbox;
import gh.edu.clet.sfl.facilities.shared.application.port.AuditPort;
import gh.edu.clet.sfl.facilities.shared.application.port.IdempotencyPort;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditAction;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.model.OperatingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reporting, triaging and dismissing faults — SRS-SFL-S153-01 and the front half of -02.
 *
 * <h2>What this replaces</h2>
 *
 * The pre-S152 version of this class had no authorisation of any kind. {@code findAll()} returned
 * every fault at every site to any caller, {@code report} accepted whatever site code it was handed,
 * and the actor came from an {@code X-SFL-User} header the controller read directly. Nothing here is
 * a refinement of that; the file is replaced, and the defect is named in the S153 gap report because
 * the same shape may exist in other services built in the same weeks.
 *
 * <h2>The two site-scope rules, which are not the same rule</h2>
 *
 * <ul>
 *   <li><strong>Writing</strong> checks the site named in the command, so an actor cannot report a
 *       fault into a site they cannot reach.</li>
 *   <li><strong>Reading</strong> filters by the actor's scopes rather than refusing, so a manager
 *       holding two sites gets both without asking twice — and gets a shorter list rather than an
 *       error when their scope narrows.</li>
 * </ul>
 *
 * <p>A requester is narrowed further, per record: they read the faults they reported and nothing
 * else. That is not something a permission matrix can express — "mine" is a property of the record,
 * not of the role — so it lives in {@link #requesterFilter}.
 */
@Service
public class FacilityFaultService {

    private final MaintenanceRepository maintenance;
    private final FacilitiesRepository facilities;
    private final ExternalBlockerPort readinessBlockers;
    private final MaintenanceConfiguration configuration;
    private final FacilitiesAuthorization authorization;
    private final AuditPort audit;
    private final IdempotencyPort idempotency;
    private final ServiceOutbox outbox;
    private final Clock clock;

    public FacilityFaultService(MaintenanceRepository maintenance, FacilitiesRepository facilities,
            ExternalBlockerPort readinessBlockers, MaintenanceConfiguration configuration,
            FacilitiesAuthorization authorization, AuditPort audit, IdempotencyPort idempotency,
            ServiceOutbox outbox, Clock clock) {
        this.maintenance = maintenance;
        this.facilities = facilities;
        this.readinessBlockers = readinessBlockers;
        this.configuration = configuration;
        this.authorization = authorization;
        this.audit = audit;
        this.idempotency = idempotency;
        this.outbox = outbox;
        this.clock = clock;
    }

    // =============================================================================================
    // Commands
    // =============================================================================================

    @Transactional
    public FacilityFault report(MaintenanceCommands.ReportFault command) {
        ActorContext actor = command.actor();
        String siteCode = resolveSite(command.siteCode(), command.roomId());
        authorization.require(actor, SflPermission.FACILITIES_FAULT_REPORT, siteCode, command.channel(),
                "FacilityFault", "new");

        if (command.idempotencyKey() != null && !command.idempotencyKey().isBlank()) {
            Optional<FacilityFault> replayed = idempotency
                    .findExistingResult("report-facility-fault", command.idempotencyKey(),
                            idempotency.fingerprint(command.idempotencyPayload()))
                    .flatMap(maintenance::findFault);
            if (replayed.isPresent()) {
                return replayed.get();
            }
        }

        FacilityRoom room = command.roomId() == null ? null : requireRoom(command.roomId(), siteCode);
        Instant at = now();
        FacilityFault fault = FacilityFault.report(
                UUID.randomUUID(),
                maintenance.nextFaultNumber(siteCode),
                siteCode,
                room == null ? null : room.id(),
                command.locationCode() == null && room != null ? room.roomCode() : command.locationCode(),
                command.assetId(),
                command.title(),
                command.description(),
                command.category(),
                command.priority(),
                actor.actorId(),
                at,
                command.channel(),
                actor.correlationId());

        FacilityFault saved = reconcileReadiness(maintenance.saveFault(fault), actor, command.channel());
        audit.record(actor, command.channel(), AuditAction.FAULT_REPORTED, "FacilityFault",
                saved.id().toString(), saved.siteCode(), null, saved);
        publish("sfl.ifimp.facility-fault-reported.v1", saved, actor);
        idempotency.recordResult("report-facility-fault", command.idempotencyKey(),
                idempotency.fingerprint(command.idempotencyPayload()), saved.id(), saved.siteCode(),
                actor.actorId());
        return saved;
    }

    /**
     * Triage: the point at which a fault gets a deadline.
     *
     * <p>The SLA is computed here from the configuration active right now and from the site's current
     * operating mode — a fault triaged during an examination gets the compressed deadline, one
     * triaged the week before does not. That is what SRS-SFL-S153-02 means by calculating timers from
     * "priority, severity, site, operating mode and workflow type".
     */
    @Transactional
    public FacilityFault triage(MaintenanceCommands.TriageFault command) {
        ActorContext actor = command.actor();
        FacilityFault fault = requireFault(command.faultId());
        authorization.require(actor, SflPermission.FACILITIES_FAULT_TRIAGE, fault.siteCode(),
                command.channel(), "FacilityFault", fault.id().toString());
        fault.metadata().requireVersion(command.expectedVersion(), "Facility fault", fault.id());

        Instant at = now();
        FaultPriority priority = command.priority() == null ? fault.priority() : command.priority();
        SlaPolicy sla = configuration.slaPolicyFor(fault.siteCode());
        Instant due = sla.resolutionDueFrom(fault.reportedAt(), priority, operatingModeOf(fault.siteCode()));

        FacilityFault triaged = maintenance.saveFault(fault.triage(priority, command.notes(), due,
                actor.actorId(), at, command.channel(), actor.correlationId()));
        triaged = reconcileReadiness(triaged, actor, command.channel());
        audit.record(actor, command.channel(), AuditAction.FAULT_TRIAGED, "FacilityFault",
                triaged.id().toString(), triaged.siteCode(), fault, triaged);
        publish("sfl.ifimp.facility-fault-triaged.v1", triaged, actor);
        return triaged;
    }

    /** Rejection, duplication or withdrawal. Any of the three closes the fault's readiness blocker. */
    @Transactional
    public FacilityFault dismiss(MaintenanceCommands.DismissFault command) {
        ActorContext actor = command.actor();
        FacilityFault fault = requireFault(command.faultId());
        authorization.require(actor, SflPermission.FACILITIES_FAULT_TRIAGE, fault.siteCode(),
                command.channel(), "FacilityFault", fault.id().toString());
        fault.metadata().requireVersion(command.expectedVersion(), "Facility fault", fault.id());
        if (command.duplicateOfFaultId() != null) {
            requireFault(command.duplicateOfFaultId());
        }

        FacilityFault dismissed = maintenance.saveFault(fault.dismiss(command.outcome(), command.reason(),
                command.duplicateOfFaultId(), actor.actorId(), now(), command.channel(),
                actor.correlationId()));
        dismissed = reconcileReadiness(dismissed, actor, command.channel());
        audit.record(actor, command.channel(), AuditAction.FAULT_DISMISSED, "FacilityFault",
                dismissed.id().toString(), dismissed.siteCode(), fault, dismissed);
        publish("sfl.ifimp.facility-fault-dismissed.v1", dismissed, actor);
        return dismissed;
    }

    @Transactional
    public FacilityFault changeLifecycle(MaintenanceCommands.ChangeFaultLifecycle command) {
        ActorContext actor = command.actor();
        FacilityFault fault = requireFault(command.faultId());
        authorization.require(actor, SflPermission.FACILITIES_FAULT_TRIAGE, fault.siteCode(),
                command.channel(), "FacilityFault", fault.id().toString());
        fault.metadata().requireVersion(command.expectedVersion(), "Facility fault", fault.id());

        FacilityFault changed = maintenance.saveFault(fault.changeLifecycle(command.lifecycleStatus(),
                actor.actorId(), now(), command.channel(), actor.correlationId()));
        audit.record(actor, command.channel(), AuditAction.FAULT_LIFECYCLE_CHANGED, "FacilityFault",
                changed.id().toString(), changed.siteCode(), fault, changed);
        return changed;
    }

    /**
     * The other half of closing a work order.
     *
     * <p>Package-private and command-free on purpose. This is not a use case a caller may invoke;
     * exposing it would let a fault be marked resolved without the work that resolved it, which is
     * precisely the audit hole SRS-SFL-S153-03 exists to prevent.
     */
    @Transactional
    FacilityFault resolveFromWorkOrder(FacilityFault fault, String notes, ActorContext actor,
            SourceChannel channel) {
        FacilityFault resolved = maintenance.saveFault(
                fault.resolve(notes, actor.actorId(), now(), channel, actor.correlationId()));
        FacilityFault reconciled = reconcileReadiness(resolved, actor, channel);
        audit.record(actor, channel, AuditAction.FAULT_RESOLVED, "FacilityFault", reconciled.id().toString(),
                reconciled.siteCode(), fault, reconciled);
        publish("sfl.ifimp.facility-fault-resolved.v1", reconciled, actor);
        return reconciled;
    }

    /** Escalation, applied by the scheduled evaluator. Package-private for the same reason. */
    @Transactional
    FacilityFault applyEscalation(FacilityFault fault, int level, ActorContext actor, SourceChannel channel) {
        FacilityFault escalated = fault.escalateTo(level, actor.actorId(), now(), channel,
                actor.correlationId());
        if (escalated == fault) {
            return fault;
        }
        FacilityFault saved = maintenance.saveFault(escalated);
        audit.record(actor, channel, AuditAction.FAULT_ESCALATED, "FacilityFault", saved.id().toString(),
                saved.siteCode(), fault, saved);
        publish("sfl.ifimp.facility-fault-escalated.v1", saved, actor);
        return saved;
    }

    // =============================================================================================
    // Queries
    // =============================================================================================

    @Transactional(readOnly = true)
    public List<FacilityFault> search(String siteCode, UUID roomId, FacilityFaultStatus status, Boolean openOnly,
            int limit, ActorContext actor, SourceChannel channel) {
        authorization.require(actor, SflPermission.FACILITIES_FAULT_READ, channel, "FacilityFault", "list",
                siteCode);
        authorization.requireRequestedSite(actor, siteCode, channel, "FacilityFault");
        List<FacilityFault> found = maintenance.findFaults(siteCode, roomId, status, openOnly,
                requesterFilter(actor), limit);
        return authorization.filterBySite(actor, found, FacilityFault::siteCode);
    }

    @Transactional(readOnly = true)
    public FacilityFault findById(UUID id, ActorContext actor, SourceChannel channel) {
        FacilityFault fault = requireFault(id);
        authorization.require(actor, SflPermission.FACILITIES_FAULT_READ, fault.siteCode(), channel,
                "FacilityFault", id.toString());
        String filter = requesterFilter(actor);
        if (filter != null && !filter.equals(fault.reportedBy())) {
            // Audited as a denial rather than answered as a 404. An actor asking for a record they may
            // not see is exactly the event SRS-SFL-S152-01 wants evidence of, and pretending the
            // record does not exist would leave a review with nothing to look at.
            audit.recordDenial(actor, channel, "FacilityFault", id.toString(), fault.siteCode(),
                    "A requester may read only the faults they reported");
            throw new FacilitiesException.UnauthorizedScopeException("You may only view faults you reported.");
        }
        return fault;
    }

    /** Open faults on a space, for the S152 space-detail screen. */
    @Transactional(readOnly = true)
    public List<FacilityFault> openFaultsForRoom(UUID roomId, ActorContext actor, SourceChannel channel) {
        FacilityRoom room = facilities.findRoom(roomId)
                .orElseThrow(() -> new FacilitiesException.RecordNotFoundException("Space", roomId));
        authorization.require(actor, SflPermission.FACILITIES_FAULT_READ, room.siteCode(), channel,
                "FacilityFault", roomId.toString());
        return maintenance.findFaults(room.siteCode(), roomId, null, true, requesterFilter(actor), 50);
    }

    // =============================================================================================
    // Internals
    // =============================================================================================

    /**
     * Keeps the fault's readiness blocker in step with the fault.
     *
     * <p>Called after every state change rather than only on report, because every one of them can
     * change the answer: triage may raise the priority over the threshold, and any dismissal or
     * resolution takes the fault out of the open set. Calling it unconditionally means a transition
     * added later cannot silently forget to.
     */
    private FacilityFault reconcileReadiness(FacilityFault fault, ActorContext actor, SourceChannel channel) {
        if (fault.roomId() == null) {
            return fault;
        }
        String reference = FaultReadinessPolicy.reference(fault);
        BlockerSeverity severity = FaultReadinessPolicy.severityFor(fault,
                configuration.blockerThreshold(fault.siteCode()));

        if (severity == null) {
            if (!fault.blockerRaised()) {
                return fault;
            }
            readinessBlockers.resolveExternalBlockers(BlockerSource.WORK_ORDER, reference,
                    FaultReadinessPolicy.resolution(fault), actor, channel);
            return maintenance.saveFault(fault.withBlockerRaised(false));
        }

        UUID blockerId = readinessBlockers.raiseExternalBlocker(fault.roomId(), BlockerSource.WORK_ORDER,
                reference, severity, FaultReadinessPolicy.describe(fault), actor, channel);
        boolean raised = blockerId != null;
        return raised == fault.blockerRaised() ? fault : maintenance.saveFault(fault.withBlockerRaised(raised));
    }

    /**
     * The {@code reportedBy} value a query must be narrowed to, or {@code null} for no narrowing.
     *
     * <p>Narrows only when {@code IFIMP_REQUESTER} is the actor's <em>only</em> facilities role. A
     * manager who also happens to hold the requester role is a manager; treating the union of roles
     * as its narrowest member would make adding a role to somebody take capability away.
     */
    private String requesterFilter(ActorContext actor) {
        Set<SflRole> roles = actor.principal().roles();
        boolean onlyRequester = roles.contains(SflRole.IFIMP_REQUESTER)
                && roles.stream().allMatch(role -> role == SflRole.IFIMP_REQUESTER);
        return onlyRequester ? actor.actorId() : null;
    }

    private String resolveSite(String requestedSiteCode, UUID roomId) {
        if (requestedSiteCode != null && !requestedSiteCode.isBlank()) {
            return requestedSiteCode.strip().toUpperCase(Locale.ROOT);
        }
        if (roomId != null) {
            return facilities.findRoom(roomId)
                    .map(FacilityRoom::siteCode)
                    .orElseThrow(() -> new FacilitiesException.InvalidParentReferenceException("Space", roomId));
        }
        throw new FacilitiesException.MissingSiteScopeException();
    }

    private FacilityRoom requireRoom(UUID roomId, String siteCode) {
        FacilityRoom room = facilities.findRoom(roomId)
                .orElseThrow(() -> new FacilitiesException.InvalidParentReferenceException("Space", roomId));
        if (!room.siteCode().equals(siteCode)) {
            throw new FacilitiesException.ValidationFailedException(
                    "Space " + room.roomCode() + " is not at site " + siteCode + ".");
        }
        return room;
    }

    private FacilityFault requireFault(UUID id) {
        return maintenance.findFault(id)
                .orElseThrow(() -> new FacilitiesException.RecordNotFoundException("Facility fault", id));
    }

    /**
     * The fault behind a work order, for {@code WorkOrderApplicationService}.
     *
     * <p>Package-private and unauthorised on purpose. The caller has already authorised the actor
     * against the work order, and the work order's site is the fault's site — checking twice would
     * refuse a legitimate closure whenever the two modules disagreed about which permission applied.
     */
    FacilityFault faultFor(UUID id) {
        return requireFault(id);
    }

    private OperatingMode operatingModeOf(String siteCode) {
        return facilities.findSiteByCode(siteCode).map(Site::operatingMode).orElse(OperatingMode.ROUTINE);
    }

    private void publish(String eventType, FacilityFault fault, ActorContext actor) {
        outbox.record(eventType, 1, "FacilityFault", fault.id(), fault.siteCode(), actor.correlationId(),
                actor.actorId(), fault);
    }

    private Instant now() {
        return clock.instant();
    }
}
