package gh.edu.clet.sfl.fleetlogistics.fleet.application.service;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.FleetWorkflowCommands.AssignWorkflowItem;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.FleetWorkflowCommands.CancelWorkflowItem;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.FleetWorkflowCommands.CloseWorkflowItem;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.FleetWorkflowCommands.CommentOnWorkflowItem;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.FleetWorkflowCommands.EscalateWorkflowItem;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.FleetWorkflowCommands.HoldWorkflowItem;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.FleetWorkflowCommands.RaiseWorkflowItem;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.FleetWorkflowCommands.ReopenWorkflowItem;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.FleetWorkflowCommands.StartWorkflowItem;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.AuditPort;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.FleetWorkflowRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.IntegrationEventPublisher;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.NotificationPort;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.SlaRuleRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.workflow.FleetWorkflowRaiser;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.event.FleetEventType;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.OptimisticLockConflictException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.RecordNotFoundException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.AuditAction;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ComplianceDocument;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.FleetWorkflowItem;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.FleetWorkflowStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.FleetWorkflowType;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.OperatingMode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.RecordMetadata;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SlaTarget;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.Vehicle;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleInspection;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.WorkflowAction;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.WorkflowComment;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.WorkflowPriority;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.WorkflowSeverity;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.WorkflowTransition;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.policy.SlaPolicy;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The fleet workflow queue (SRS-SFL-S166-02).
 *
 * <p>Every transition does four things in one transaction: move the aggregate, append an immutable
 * history entry, write the audit record and notify whoever now owns the work. Doing them together is
 * what makes the history and the audit trail agree with the item's state.
 *
 * <p>SLA targets are always resolved from the rules effective <em>now</em>, never cached, because
 * SRS-SFL-S166-02 requires escalation to use the configuration active at the time of evaluation.
 */
@Service
public class FleetWorkflowApplicationService implements FleetWorkflowRaiser {

    private static final String RESOURCE_TYPE = "FleetWorkflowItem";

    private final FleetWorkflowRepository workflowItems;
    private final SlaRuleRepository slaRules;
    private final FleetAccessPolicy accessPolicy;
    private final AuditPort auditPort;
    private final IntegrationEventPublisher eventPublisher;
    private final NotificationPort notifications;
    private final Clock clock;

    public FleetWorkflowApplicationService(FleetWorkflowRepository workflowItems, SlaRuleRepository slaRules,
            FleetAccessPolicy accessPolicy, AuditPort auditPort, IntegrationEventPublisher eventPublisher,
            NotificationPort notifications, Clock clock) {
        this.workflowItems = workflowItems;
        this.slaRules = slaRules;
        this.accessPolicy = accessPolicy;
        this.auditPort = auditPort;
        this.eventPublisher = eventPublisher;
        this.notifications = notifications;
        this.clock = clock;
    }

    /** SRS-SFL-S166-02: raise a workflow item. */
    @Transactional
    public FleetWorkflowItem raise(RaiseWorkflowItem command) {
        SiteCode site = SiteCode.of(command.siteCode());
        accessPolicy.require(command.actor(), SflPermission.FLEET_WORKFLOW_MANAGE, site, RESOURCE_TYPE, null);

        FleetWorkflowItem item = createItem(command.workflowType(), command.relatedRecordType(),
                command.relatedRecordId(), site, command.title(), command.description(), command.priority(),
                command.severity(), command.operatingMode(), command.actor(), command.sourceChannel());

        if (command.assignee() != null && !command.assignee().isBlank()) {
            item = item.assignTo(command.assignee(), clock.instant(), item.metadata());
        }
        FleetWorkflowItem saved = workflowItems.save(item);

        appendTransition(saved, null, saved.status(), WorkflowAction.CREATED, command.actor(), null);
        auditPort.record(command.actor(), command.sourceChannel(), site, AuditAction.CREATE, RESOURCE_TYPE,
                saved.id().toString(), null, auditImage(saved));
        if (saved.assignee() != null) {
            notifyAssignee(saved, NotificationPort.NotificationKind.WORK_ASSIGNED);
        }
        return saved;
    }

    /** SRS-SFL-S166-02: assign or reassign. */
    @Transactional
    public FleetWorkflowItem assign(AssignWorkflowItem command) {
        FleetWorkflowItem existing = requireItem(command.workflowItemId());
        accessPolicy.require(command.actor(), SflPermission.FLEET_WORKFLOW_ASSIGN, existing.siteCode(),
                RESOURCE_TYPE, existing.id().toString());
        requireExpectedVersion(existing, command.expectedVersion());

        boolean reassignment = existing.assignee() != null;
        FleetWorkflowItem assigned = workflowItems.save(existing.assignTo(command.assignee(), clock.instant(),
                metadataFor(existing, command.actor(), command.sourceChannel())));

        appendTransition(assigned, existing.status(), assigned.status(),
                reassignment ? WorkflowAction.REASSIGNED : WorkflowAction.ASSIGNED, command.actor(),
                command.reason());
        auditPort.record(command.actor(), command.sourceChannel(), assigned.siteCode(),
                reassignment ? AuditAction.REASSIGN : AuditAction.ASSIGN, RESOURCE_TYPE,
                assigned.id().toString(), auditImage(existing), auditImage(assigned));
        notifyAssignee(assigned, NotificationPort.NotificationKind.WORK_ASSIGNED);
        return assigned;
    }

    /** SRS-SFL-S166-02: record progress by moving the item into active work. */
    @Transactional
    public FleetWorkflowItem start(StartWorkflowItem command) {
        FleetWorkflowItem existing = requireItem(command.workflowItemId());
        accessPolicy.require(command.actor(), SflPermission.FLEET_WORKFLOW_MANAGE, existing.siteCode(),
                RESOURCE_TYPE, existing.id().toString());
        requireExpectedVersion(existing, command.expectedVersion());

        FleetWorkflowItem started = workflowItems.save(existing.start(clock.instant(),
                metadataFor(existing, command.actor(), command.sourceChannel())));

        appendTransition(started, existing.status(), started.status(), WorkflowAction.STARTED, command.actor(),
                null);
        auditPort.record(command.actor(), command.sourceChannel(), started.siteCode(),
                AuditAction.STATE_TRANSITION, RESOURCE_TYPE, started.id().toString(), auditImage(existing),
                auditImage(started));
        return started;
    }

    /** SRS-SFL-S166-02: hold or resume. */
    @Transactional
    public FleetWorkflowItem holdOrResume(HoldWorkflowItem command) {
        FleetWorkflowItem existing = requireItem(command.workflowItemId());
        accessPolicy.require(command.actor(), SflPermission.FLEET_WORKFLOW_MANAGE, existing.siteCode(),
                RESOURCE_TYPE, existing.id().toString());
        requireExpectedVersion(existing, command.expectedVersion());

        RecordMetadata metadata = metadataFor(existing, command.actor(), command.sourceChannel());
        FleetWorkflowItem updated = command.resume()
                ? existing.resume(metadata)
                : existing.hold(command.reason(), metadata);
        FleetWorkflowItem saved = workflowItems.save(updated);

        appendTransition(saved, existing.status(), saved.status(),
                command.resume() ? WorkflowAction.RESUMED : WorkflowAction.HELD, command.actor(),
                command.reason());
        auditPort.record(command.actor(), command.sourceChannel(), saved.siteCode(),
                command.resume() ? AuditAction.RESUME : AuditAction.HOLD, RESOURCE_TYPE, saved.id().toString(),
                auditImage(existing), auditImage(saved));
        if (!command.resume()) {
            notifyAssignee(saved, NotificationPort.NotificationKind.WORK_BLOCKED);
        }
        return saved;
    }

    /** SRS-SFL-S166-02: manual escalation. Privileged. */
    @Transactional
    public FleetWorkflowItem escalate(EscalateWorkflowItem command) {
        FleetWorkflowItem existing = requireItem(command.workflowItemId());
        accessPolicy.requirePrivilegedTransition(command.actor(), SflPermission.FLEET_WORKFLOW_ESCALATE,
                existing.siteCode(), RESOURCE_TYPE, existing.id().toString());
        requireExpectedVersion(existing, command.expectedVersion());

        return applyEscalation(existing, command.actor(), command.sourceChannel(), command.reason());
    }

    /**
     * Escalates an item because its SLA breached.
     *
     * <p>Called by the scheduled evaluation with a service principal, so it does not repeat the
     * interactive permission check the manual path performs.
     */
    @Transactional
    public FleetWorkflowItem escalateOnSlaBreach(FleetWorkflowItem item, ActorContext actor) {
        return applyEscalation(item, actor, SourceChannel.SCHEDULER,
                "SLA breached at " + clock.instant() + "; resolution was due " + item.slaDueAt() + ".");
    }

    /** SRS-SFL-S166-02: cancel. Privileged; reason mandatory. */
    @Transactional
    public FleetWorkflowItem cancel(CancelWorkflowItem command) {
        FleetWorkflowItem existing = requireItem(command.workflowItemId());
        accessPolicy.requirePrivilegedTransition(command.actor(), SflPermission.FLEET_WORKFLOW_CANCEL,
                existing.siteCode(), RESOURCE_TYPE, existing.id().toString());
        requireExpectedVersion(existing, command.expectedVersion());

        FleetWorkflowItem cancelled = workflowItems.save(existing.cancel(command.reason(), clock.instant(),
                command.actor().actorId(), metadataFor(existing, command.actor(), command.sourceChannel())));

        appendTransition(cancelled, existing.status(), cancelled.status(), WorkflowAction.CANCELLED,
                command.actor(), command.reason());
        auditPort.record(command.actor(), command.sourceChannel(), cancelled.siteCode(), AuditAction.CANCEL,
                RESOURCE_TYPE, cancelled.id().toString(), auditImage(existing), auditImage(cancelled));
        return cancelled;
    }

    /** SRS-SFL-S166-02: close with the mandatory reason and evidence. */
    @Transactional
    public FleetWorkflowItem close(CloseWorkflowItem command) {
        FleetWorkflowItem existing = requireItem(command.workflowItemId());
        accessPolicy.require(command.actor(), SflPermission.FLEET_WORKFLOW_MANAGE, existing.siteCode(),
                RESOURCE_TYPE, existing.id().toString());
        requireExpectedVersion(existing, command.expectedVersion());

        FleetWorkflowItem closed = workflowItems.save(existing.close(command.closureReason(),
                command.closureEvidenceId(), clock.instant(), command.actor().actorId(),
                metadataFor(existing, command.actor(), command.sourceChannel())));

        appendTransition(closed, existing.status(), closed.status(), WorkflowAction.CLOSED, command.actor(),
                command.closureReason());
        auditPort.record(command.actor(), command.sourceChannel(), closed.siteCode(), AuditAction.CLOSE,
                RESOURCE_TYPE, closed.id().toString(), auditImage(existing), auditImage(closed));
        return closed;
    }

    /** SRS-SFL-S166-02: permitted reopening. Privileged; the SLA restarts. */
    @Transactional
    public FleetWorkflowItem reopen(ReopenWorkflowItem command) {
        FleetWorkflowItem existing = requireItem(command.workflowItemId());
        accessPolicy.requirePrivilegedTransition(command.actor(), SflPermission.FLEET_WORKFLOW_REOPEN,
                existing.siteCode(), RESOURCE_TYPE, existing.id().toString());
        requireExpectedVersion(existing, command.expectedVersion());

        Instant now = clock.instant();
        FleetWorkflowItem reopened = workflowItems.save(existing.reopen(command.reason(), resolveSla(existing, now),
                now, metadataFor(existing, command.actor(), command.sourceChannel())));

        appendTransition(reopened, existing.status(), reopened.status(), WorkflowAction.REOPENED, command.actor(),
                command.reason());
        auditPort.record(command.actor(), command.sourceChannel(), reopened.siteCode(), AuditAction.REOPEN,
                RESOURCE_TYPE, reopened.id().toString(), auditImage(existing), auditImage(reopened));
        return reopened;
    }

    /** SRS-SFL-S166-02: add an immutable comment. */
    @Transactional
    public WorkflowComment comment(CommentOnWorkflowItem command) {
        FleetWorkflowItem existing = requireItem(command.workflowItemId());
        accessPolicy.require(command.actor(), SflPermission.FLEET_WORKFLOW_MANAGE, existing.siteCode(),
                RESOURCE_TYPE, existing.id().toString());

        WorkflowComment comment = workflowItems.appendComment(WorkflowComment.of(existing.id(),
                command.actor().actorId(), command.body(), clock.instant(), command.actor().correlationId()));
        appendTransition(existing, existing.status(), existing.status(), WorkflowAction.COMMENTED,
                command.actor(), null);
        return comment;
    }

    // --- FleetWorkflowRaiser ------------------------------------------------------------

    @Override
    @Transactional
    public FleetWorkflowItem raiseInspectionDefect(VehicleInspection inspection, Vehicle vehicle,
            ActorContext actor, SourceChannel sourceChannel) {
        String relatedId = inspection.id().toString();
        return workflowItems.findOpenByRelatedRecord("VehicleInspection", relatedId)
                .orElseGet(() -> {
                    WorkflowSeverity severity = inspection.hasOpenCriticalDefect()
                            ? WorkflowSeverity.CRITICAL
                            : WorkflowSeverity.MAJOR;
                    FleetWorkflowItem item = workflowItems.save(createItem(FleetWorkflowType.VEHICLE_DEFECT,
                            "VehicleInspection", relatedId, vehicle.siteCode(),
                            "Defect on " + vehicle.registrationNumber(),
                            "Inspection " + inspection.inspectionType() + " returned "
                                    + inspection.result() + " for vehicle "
                                    + vehicle.registrationNumber() + ".",
                            inspection.hasOpenCriticalDefect() ? WorkflowPriority.URGENT : WorkflowPriority.HIGH,
                            severity, OperatingMode.MAINTENANCE, actor, sourceChannel));
                    appendTransition(item, null, item.status(), WorkflowAction.CREATED, actor,
                            "Raised automatically from a failed inspection");
                    auditPort.record(actor, sourceChannel, item.siteCode(), AuditAction.CREATE, RESOURCE_TYPE,
                            item.id().toString(), null, auditImage(item));
                    notifications.notifyRole(item.siteCode(), gh.edu.clet.sfl.common.security.SflRole
                                    .FLEET_MANAGER, NotificationPort.NotificationKind.INSPECTION_FAILED,
                            item.workflowNumber(), Map.of("vehicleId", vehicle.id().toString()));
                    return item;
                });
    }

    @Override
    @Transactional
    public FleetWorkflowItem raiseComplianceExpiry(ComplianceDocument document, Vehicle vehicle, boolean expired,
            ActorContext actor, SourceChannel sourceChannel) {
        String relatedId = document.id().toString();
        return workflowItems.findOpenByRelatedRecord("ComplianceDocument", relatedId)
                .orElseGet(() -> {
                    FleetWorkflowItem item = workflowItems.save(createItem(FleetWorkflowType.COMPLIANCE_RENEWAL,
                            "ComplianceDocument", relatedId, vehicle.siteCode(),
                            document.documentType() + (expired ? " expired" : " expiring") + " for "
                                    + vehicle.registrationNumber(),
                            document.documentType() + " for vehicle " + vehicle.registrationNumber()
                                    + (expired ? " expired on " : " expires on ") + document.expiresOn() + ".",
                            expired ? WorkflowPriority.URGENT : WorkflowPriority.HIGH,
                            expired ? WorkflowSeverity.MAJOR : WorkflowSeverity.MODERATE,
                            OperatingMode.ROUTINE, actor, sourceChannel));
                    appendTransition(item, null, item.status(), WorkflowAction.CREATED, actor,
                            "Raised automatically by the compliance expiry sweep");
                    auditPort.record(actor, sourceChannel, item.siteCode(), AuditAction.CREATE, RESOURCE_TYPE,
                            item.id().toString(), null, auditImage(item));
                    notifications.notifyRole(item.siteCode(), gh.edu.clet.sfl.common.security.SflRole
                                    .FLEET_LOGISTICS_OFFICER,
                            expired ? NotificationPort.NotificationKind.COMPLIANCE_EXPIRED
                                    : NotificationPort.NotificationKind.COMPLIANCE_EXPIRING,
                            item.workflowNumber(), Map.of("vehicleId", vehicle.id().toString()));
                    return item;
                });
    }

    @Override
    @Transactional
    public FleetWorkflowItem raiseServiceDue(Vehicle vehicle, boolean overdue, ActorContext actor,
            SourceChannel sourceChannel) {
        String relatedId = vehicle.id().toString();
        return workflowItems.findOpenByRelatedRecord("VehicleService", relatedId)
                .orElseGet(() -> {
                    FleetWorkflowItem item = workflowItems.save(createItem(FleetWorkflowType.SERVICE_SCHEDULING,
                            "VehicleService", relatedId, vehicle.siteCode(),
                            "Service " + (overdue ? "overdue" : "due") + " for "
                                    + vehicle.registrationNumber(),
                            "Vehicle " + vehicle.registrationNumber() + " has service status "
                                    + vehicle.serviceStatus() + " at odometer "
                                    + vehicle.odometer().value() + ".",
                            overdue ? WorkflowPriority.HIGH : WorkflowPriority.MEDIUM,
                            overdue ? WorkflowSeverity.MAJOR : WorkflowSeverity.MODERATE,
                            OperatingMode.MAINTENANCE, actor, sourceChannel));
                    appendTransition(item, null, item.status(), WorkflowAction.CREATED, actor,
                            "Raised automatically by the service-due sweep");
                    auditPort.record(actor, sourceChannel, item.siteCode(), AuditAction.CREATE, RESOURCE_TYPE,
                            item.id().toString(), null, auditImage(item));
                    notifications.notifyRole(item.siteCode(), gh.edu.clet.sfl.common.security.SflRole
                                    .FLEET_LOGISTICS_OFFICER,
                            overdue ? NotificationPort.NotificationKind.SERVICE_OVERDUE
                                    : NotificationPort.NotificationKind.SERVICE_DUE,
                            item.workflowNumber(), Map.of("vehicleId", vehicle.id().toString()));
                    return item;
                });
    }

    // --- helpers ------------------------------------------------------------------------

    private FleetWorkflowItem applyEscalation(FleetWorkflowItem existing, ActorContext actor,
            SourceChannel sourceChannel, String reason) {
        Instant now = clock.instant();
        SlaTarget sla = resolveSla(existing, now);
        FleetWorkflowItem escalated = workflowItems.save(existing.escalate(sla, now,
                metadataFor(existing, actor, sourceChannel)));

        appendTransition(escalated, existing.status(), escalated.status(), WorkflowAction.ESCALATED, actor,
                reason);
        auditPort.record(actor, sourceChannel, escalated.siteCode(), AuditAction.ESCALATE, RESOURCE_TYPE,
                escalated.id().toString(), auditImage(existing), auditImage(escalated));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("workflowItemId", escalated.id().toString());
        payload.put("workflowNumber", escalated.workflowNumber());
        payload.put("escalationLevel", escalated.escalationLevel());
        payload.put("slaDueAt", String.valueOf(existing.slaDueAt()));
        payload.put("escalationRole", sla.escalationRole().name());
        payload.put("slaRule", sla.ruleReference());
        payload.put("reason", reason);
        eventPublisher.publish(FleetEventType.FLEET_WORKFLOW_ESCALATED, RESOURCE_TYPE,
                escalated.id().toString(), escalated.siteCode(), actor, payload);

        notifications.notifyRole(escalated.siteCode(), sla.escalationRole(),
                NotificationPort.NotificationKind.WORK_ESCALATED, escalated.workflowNumber(),
                Map.of("workflowItemId", escalated.id().toString(),
                        "escalationLevel", String.valueOf(escalated.escalationLevel())));
        if (escalated.assignee() != null) {
            notifications.notifyAssignee(escalated.siteCode(), escalated.assignee(),
                    NotificationPort.NotificationKind.WORK_OVERDUE, escalated.workflowNumber(), Map.of());
        }
        return escalated;
    }

    private FleetWorkflowItem createItem(FleetWorkflowType workflowType, String relatedRecordType,
            String relatedRecordId, SiteCode site, String title, String description, WorkflowPriority priority,
            WorkflowSeverity severity, OperatingMode operatingMode, ActorContext actor,
            SourceChannel sourceChannel) {
        Instant now = clock.instant();
        UUID id = UUID.randomUUID();
        SlaTarget sla = SlaPolicy.resolve(slaRules.findEffectiveRules(now), workflowType, priority, severity,
                site.value(), operatingMode);
        return FleetWorkflowItem.raise(id, workflowNumber(id), workflowType, relatedRecordType, relatedRecordId,
                site, title, description, priority, severity, operatingMode, sla, now,
                RecordMetadata.createdBy(actor.actorId(), now, sourceChannel, actor.correlationId()));
    }

    /** Resolves the SLA from the rules effective now — never from a cached value. */
    private SlaTarget resolveSla(FleetWorkflowItem item, Instant now) {
        return SlaPolicy.resolve(slaRules.findEffectiveRules(now), item.workflowType(), item.priority(),
                item.severity(), item.siteCode().value(), item.operatingMode());
    }

    private void appendTransition(FleetWorkflowItem item, FleetWorkflowStatus from, FleetWorkflowStatus to,
            WorkflowAction action, ActorContext actor, String reason) {
        workflowItems.appendTransition(WorkflowTransition.of(item.id(),
                workflowItems.nextTransitionSequence(item.id()), from, to, action, actor.actorId(),
                clock.instant(), reason, actor.correlationId()));
    }

    private void notifyAssignee(FleetWorkflowItem item, NotificationPort.NotificationKind kind) {
        if (item.assignee() == null) {
            return;
        }
        notifications.notifyAssignee(item.siteCode(), item.assignee(), kind, item.workflowNumber(),
                Map.of("workflowItemId", item.id().toString(),
                        "slaDueAt", String.valueOf(item.slaDueAt())));
    }

    private FleetWorkflowItem requireItem(UUID id) {
        return workflowItems.findById(id).orElseThrow(() -> RecordNotFoundException.of(RESOURCE_TYPE, id));
    }

    private RecordMetadata metadataFor(FleetWorkflowItem item, ActorContext actor, SourceChannel channel) {
        return item.metadata().modifiedBy(actor.actorId(), clock.instant(), channel, actor.correlationId());
    }

    private static void requireExpectedVersion(FleetWorkflowItem item, Long expectedVersion) {
        if (expectedVersion != null && expectedVersion != item.metadata().version()) {
            throw new OptimisticLockConflictException(Map.of(
                    "expectedVersion", expectedVersion,
                    "currentVersion", item.metadata().version()));
        }
    }

    private static String workflowNumber(UUID id) {
        return "FWF-" + id.toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    static Map<String, Object> auditImage(FleetWorkflowItem item) {
        Map<String, Object> image = new LinkedHashMap<>();
        image.put("workflowItemId", item.id().toString());
        image.put("workflowNumber", item.workflowNumber());
        image.put("workflowType", item.workflowType().name());
        image.put("relatedRecordType", item.relatedRecordType());
        image.put("relatedRecordId", item.relatedRecordId());
        image.put("siteCode", item.siteCode().value());
        image.put("priority", item.priority().name());
        image.put("severity", item.severity().name());
        image.put("operatingMode", item.operatingMode().name());
        image.put("status", item.status().name());
        image.put("assignee", item.assignee());
        image.put("slaDueAt", item.slaDueAt() == null ? null : item.slaDueAt().toString());
        image.put("escalationLevel", item.escalationLevel());
        image.put("closureReason", item.closureReason());
        image.put("closureEvidenceId", item.closureEvidenceId() == null
                ? null
                : item.closureEvidenceId().toString());
        image.put("version", item.metadata().version());
        return image;
    }
}
