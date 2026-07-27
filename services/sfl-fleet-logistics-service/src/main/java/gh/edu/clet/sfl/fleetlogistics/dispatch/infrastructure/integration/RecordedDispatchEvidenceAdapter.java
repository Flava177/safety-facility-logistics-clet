package gh.edu.clet.sfl.fleetlogistics.dispatch.infrastructure.integration;

import gh.edu.clet.sfl.fleetlogistics.dispatch.application.port.DispatchEvidencePort;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.AuditPort;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.EvidenceRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.IntegrationEventPublisher;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.event.FleetEventType;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.AuditAction;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.AuditChainVerification;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.EvidenceReference;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registers dispatch/receipt/scan/custody documents as governed evidence references on the shared
 * S166 evidence + tamper-evident audit foundation, so no ungoverned binary ever lands in a dispatch
 * column. The parent dispatch operation has already authorised the caller (site-scoped
 * {@code DISPATCH_*} permission), so this adapter persists the reference and records the append-only
 * audit entry directly rather than re-checking a fleet permission.
 */
@Component
public class RecordedDispatchEvidenceAdapter implements DispatchEvidencePort {

    private static final String EVIDENCE = "EvidenceReference";

    private final EvidenceRepository evidence;
    private final AuditPort audit;
    private final IntegrationEventPublisher events;
    private final Clock clock;

    public RecordedDispatchEvidenceAdapter(EvidenceRepository evidence, AuditPort audit,
            IntegrationEventPublisher events, Clock clock) {
        this.evidence = evidence;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    @Override
    @Transactional
    public UUID register(EvidenceRegistration r) {
        EvidenceReference reference = EvidenceReference.register(UUID.randomUUID(), r.siteCode(),
                r.relatedRecordType(), r.relatedRecordId(), r.evidenceType(), r.fileName(), r.contentType(),
                r.storageReference(), r.sha256Hash(), r.retentionClass(), r.retentionExpiresAt(),
                new EvidenceReference.ActorStamp(r.actor().actorId(), clock.instant(), r.sourceChannel(),
                        r.actor().correlationId()));
        EvidenceReference saved = evidence.save(reference);
        audit.record(r.actor(), r.sourceChannel(), r.siteCode(), AuditAction.EVIDENCE_REGISTERED, EVIDENCE,
                saved.id().toString(), null, saved.auditImage());
        events.publish(FleetEventType.FLEET_EVIDENCE_REGISTERED, EVIDENCE, saved.id().toString(), r.siteCode(),
                r.actor(), saved.auditImage());
        return saved.id();
    }

    @Override
    public AuditChainVerification verifyAuditChain() {
        return audit.verifyChain();
    }
}
