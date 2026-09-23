package gh.edu.clet.sfl.safetysecurity.lifesafety.application.service;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.safetysecurity.lifesafety.application.port.LifeSafetyRepository;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.event.LifeSafetyEventType;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.exception.LifeSafetyErrorCode;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.exception.LifeSafetyException;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.ComplianceExceptionKind;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.ComplianceExceptionStatus;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.ComplianceRefType;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.InspectionSchedule;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.LifeSafetyComplianceException;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.RecordMetadata;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.SourceChannel;
import gh.edu.clet.sfl.safetysecurity.platform.application.port.AuditPort;
import gh.edu.clet.sfl.safetysecurity.platform.application.port.IntegrationEventPublisher;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SRS-SFL-S162a-03: inspection/certification schedules and the panel-fault/overdue-inspection
 * compliance exceptions they raise. A maintenance work order in IFIMP (S153) is a different service's
 * responsibility to create - this only publishes {@code LIFESAFETY_COMPLIANCE_EXCEPTION_RAISED}
 * through the outbox and stops there, exactly as S161's camera-health event does for the same reason.
 *
 * <p>{@link #sweepOverdueInspections(String, ActorContext)} is called per-site by {@code
 * LifeSafetyScheduler} (mirroring {@code EmergencySweepScheduler}'s shape), not annotated
 * {@code @Scheduled} itself - a service method stays a plain, independently-testable unit; the
 * scheduling concern (which sites, how often) belongs to the infrastructure component that iterates them.
 */
@Service
public class InspectionComplianceService {

    private final LifeSafetyRepository repository;
    private final LifeSafetyAccessPolicy access;
    private final AuditPort audit;
    private final IntegrationEventPublisher events;
    private final Clock clock;

    public InspectionComplianceService(LifeSafetyRepository repository, LifeSafetyAccessPolicy access,
            AuditPort audit, IntegrationEventPublisher events, Clock clock) {
        this.repository = repository;
        this.access = access;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    public record RegisterSchedule(String siteCode, String systemRef, String description, int frequencyDays,
            ActorContext actor) {
    }

    @Transactional
    public InspectionSchedule register(RegisterSchedule c) {
        access.require(c.actor(), SflPermission.LIFESAFETY_INSPECTION_MANAGE, c.siteCode(), "InspectionSchedule",
                null);
        var now = clock.instant();
        var schedule = new InspectionSchedule(UUID.randomUUID(), c.siteCode(), c.systemRef(), c.description(),
                c.frequencyDays(), null, now.plusSeconds(c.frequencyDays() * 86400L), null,
                RecordMetadata.createdBy(c.actor().actorId(), now, SourceChannel.WEB, c.actor().correlationId()));
        return repository.saveInspectionSchedule(schedule);
    }

    @Transactional
    public InspectionSchedule recordPerformed(UUID id, String evidenceReference, ActorContext actor) {
        var schedule = repository.findInspectionSchedule(id)
                .orElseThrow(() -> LifeSafetyException.notFound("InspectionSchedule", id));
        access.require(actor, SflPermission.LIFESAFETY_INSPECTION_MANAGE, schedule.siteCode(),
                "InspectionSchedule", id.toString());
        var now = clock.instant();
        var updated = schedule.recordPerformed(now, evidenceReference,
                schedule.metadata().modifiedBy(actor.actorId(), now, SourceChannel.WEB, actor.correlationId()));
        var saved = repository.saveInspectionSchedule(updated);
        resolveOpenException(schedule.siteCode(), id, ComplianceExceptionKind.OVERDUE_INSPECTION, actor);
        return saved;
    }

    @Transactional
    public LifeSafetyComplianceException reportPanelFault(String siteCode, String deviceRef, String note,
            ActorContext actor) {
        return raise(siteCode, ComplianceRefType.PANEL, null, ComplianceExceptionKind.PANEL_FAULT,
                "Panel fault reported for " + deviceRef + (note == null ? "" : ": " + note), actor);
    }

    /** SRS-SFL-S162a-03: flags every schedule whose next-due date has passed and has no open exception yet. */
    @Transactional
    public void sweepOverdueInspections(String siteCode, ActorContext systemActor) {
        Instant now = clock.instant();
        for (InspectionSchedule schedule : repository.findOverdueInspectionSchedules(siteCode, now)) {
            if (!repository.hasOpenComplianceException(siteCode, schedule.id(),
                    ComplianceExceptionKind.OVERDUE_INSPECTION)) {
                raise(siteCode, ComplianceRefType.INSPECTION_SCHEDULE, schedule.id(),
                        ComplianceExceptionKind.OVERDUE_INSPECTION,
                        "Inspection overdue: " + schedule.description(), systemActor);
            }
        }
    }

    private LifeSafetyComplianceException raise(String siteCode, ComplianceRefType refType, UUID refId,
            ComplianceExceptionKind kind, String note, ActorContext actor) {
        var now = clock.instant();
        var exception = new LifeSafetyComplianceException(UUID.randomUUID(), siteCode, refType, refId, kind,
                ComplianceExceptionStatus.OPEN, note, now, null, null,
                RecordMetadata.createdBy(actor.actorId(), now, SourceChannel.SYSTEM, actor.correlationId()));
        var saved = repository.saveComplianceException(exception);
        audit.record(actor, SourceChannel.SYSTEM.name(), siteCode, "LIFESAFETY_COMPLIANCE_EXCEPTION_RAISED",
                "LifeSafetyComplianceException", saved.id().toString(), null, saved, null);
        events.publish(LifeSafetyEventType.LIFESAFETY_COMPLIANCE_EXCEPTION_RAISED.eventType(),
                LifeSafetyEventType.LIFESAFETY_COMPLIANCE_EXCEPTION_RAISED.version(),
                "LifeSafetyComplianceException", saved.id().toString(), siteCode, actor,
                Map.of("exceptionId", saved.id().toString(), "kind", saved.kind(), "note", saved.note() == null
                        ? "" : saved.note()));
        return saved;
    }

    @Transactional
    public LifeSafetyComplianceException resolve(UUID id, ActorContext actor) {
        var exception = repository.findComplianceException(id)
                .orElseThrow(() -> LifeSafetyException.notFound("LifeSafetyComplianceException", id));
        access.require(actor, SflPermission.LIFESAFETY_COMPLIANCE_EXCEPTION_RESOLVE, exception.siteCode(),
                "LifeSafetyComplianceException", id.toString());
        var now = clock.instant();
        var resolved = exception.resolve(actor.actorId(), now,
                exception.metadata().modifiedBy(actor.actorId(), now, SourceChannel.WEB, actor.correlationId()));
        var saved = repository.saveComplianceException(resolved);
        events.publish(LifeSafetyEventType.LIFESAFETY_COMPLIANCE_EXCEPTION_RESOLVED.eventType(),
                LifeSafetyEventType.LIFESAFETY_COMPLIANCE_EXCEPTION_RESOLVED.version(),
                "LifeSafetyComplianceException", saved.id().toString(), saved.siteCode(), actor,
                Map.of("exceptionId", saved.id().toString()));
        return saved;
    }

    private void resolveOpenException(String siteCode, UUID refId, ComplianceExceptionKind kind, ActorContext actor) {
        repository.findComplianceExceptions(siteCode, ComplianceExceptionStatus.OPEN).stream()
                .filter(e -> kind.equals(e.kind()) && refId.equals(e.refId()))
                .forEach(e -> resolve(e.id(), actor));
    }

    @Transactional(readOnly = true)
    public List<InspectionSchedule> listSchedules(String siteCode, ActorContext actor) {
        access.require(actor, SflPermission.LIFESAFETY_INSPECTION_READ, siteCode, "InspectionSchedule", null);
        return repository.findInspectionSchedules(siteCode);
    }

    @Transactional(readOnly = true)
    public List<LifeSafetyComplianceException> listExceptions(String siteCode, ComplianceExceptionStatus status,
            ActorContext actor) {
        access.require(actor, SflPermission.LIFESAFETY_COMPLIANCE_EXCEPTION_READ, siteCode,
                "LifeSafetyComplianceException", null);
        return repository.findComplianceExceptions(siteCode, status);
    }
}
