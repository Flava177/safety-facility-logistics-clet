package gh.edu.clet.sfl.fleetlogistics.fleet.application.service;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.EvidenceCommands.DecideEvidenceExport;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.EvidenceCommands.ExportEvidence;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.EvidenceCommands.RegisterEvidence;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.EvidenceCommands.RequestEvidenceExport;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.AuditPort;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.EvidenceExportRequestRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.EvidenceRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.IntegrationEventPublisher;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.event.FleetEventType;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.AuditChainFailureException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.RecordNotFoundException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.AuditAction;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.AuditChainVerification;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.EvidenceExportRequest;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.EvidenceReference;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Evidence and audit trail use cases for SRS-SFL-S166-03. */
@Service
public class FleetEvidenceApplicationService {

    private static final String EVIDENCE = "EvidenceReference";
    private static final String EXPORT_REQUEST = "EvidenceExportRequest";

    private final EvidenceRepository evidenceRepository;
    private final EvidenceExportRequestRepository exportRequests;
    private final FleetAccessPolicy accessPolicy;
    private final AuditPort auditPort;
    private final IntegrationEventPublisher eventPublisher;
    private final Clock clock;

    public FleetEvidenceApplicationService(EvidenceRepository evidenceRepository,
            EvidenceExportRequestRepository exportRequests, FleetAccessPolicy accessPolicy, AuditPort auditPort,
            IntegrationEventPublisher eventPublisher, Clock clock) {
        this.evidenceRepository = evidenceRepository;
        this.exportRequests = exportRequests;
        this.accessPolicy = accessPolicy;
        this.auditPort = auditPort;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public EvidenceReference register(RegisterEvidence command) {
        SiteCode site = SiteCode.of(command.siteCode());
        accessPolicy.require(command.actor(), SflPermission.FLEET_EVIDENCE_REGISTER, site, EVIDENCE, null);
        EvidenceReference evidence = EvidenceReference.register(UUID.randomUUID(), site,
                command.relatedRecordType(), command.relatedRecordId(), command.evidenceType(),
                command.fileName(), command.contentType(), command.storageReference(), command.sha256Hash(),
                command.retentionClass(), command.retentionExpiresAt(), stamp(command.actor(), command.sourceChannel()));
        EvidenceReference saved = evidenceRepository.save(evidence);
        auditPort.record(command.actor(), command.sourceChannel(), site, AuditAction.EVIDENCE_REGISTERED,
                EVIDENCE, saved.id().toString(), null, saved.auditImage());
        eventPublisher.publish(FleetEventType.FLEET_EVIDENCE_REGISTERED, EVIDENCE, saved.id().toString(),
                site, command.actor(), saved.auditImage());
        return saved;
    }

    @Transactional(readOnly = true)
    public EvidenceReference findById(UUID evidenceId, ActorContext actor) {
        EvidenceReference evidence = requireEvidence(evidenceId);
        accessPolicy.require(actor, SflPermission.FLEET_EVIDENCE_READ, evidence.siteCode(), EVIDENCE,
                evidence.id().toString());
        return evidence;
    }

    @Transactional
    public EvidenceReference recordAccess(UUID evidenceId, ActorContext actor,
            gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel sourceChannel) {
        EvidenceReference evidence = findById(evidenceId, actor);
        auditPort.record(actor, sourceChannel, evidence.siteCode(), AuditAction.EVIDENCE_VIEWED, EVIDENCE,
                evidence.id().toString(), null, Map.of(
                        "evidenceId", evidence.id().toString(),
                        "storageReference", evidence.storageReference(),
                        "sha256Hash", evidence.sha256Hash()));
        return evidence;
    }

    @Transactional
    public EvidenceExportRequest requestExport(RequestEvidenceExport command) {
        EvidenceReference evidence = requireEvidence(command.evidenceId());
        accessPolicy.require(command.actor(), SflPermission.FLEET_EVIDENCE_EXPORT_REQUEST, evidence.siteCode(),
                EVIDENCE, evidence.id().toString());
        EvidenceExportRequest request = EvidenceExportRequest.request(UUID.randomUUID(), evidence,
                command.reason(), stamp(command.actor(), command.sourceChannel()));
        EvidenceExportRequest saved = exportRequests.save(request);
        auditPort.record(command.actor(), command.sourceChannel(), evidence.siteCode(),
                AuditAction.EVIDENCE_EXPORT_REQUESTED, EXPORT_REQUEST, saved.id().toString(), null,
                saved.auditImage());
        return saved;
    }

    @Transactional
    public EvidenceExportRequest decideExport(DecideEvidenceExport command) {
        EvidenceExportRequest existing = requireExportRequest(command.exportRequestId());
        accessPolicy.requirePrivilegedTransition(command.actor(), SflPermission.FLEET_EVIDENCE_EXPORT_APPROVE,
                existing.siteCode(), EXPORT_REQUEST, existing.id().toString());
        EvidenceExportRequest decided = exportRequests.save(existing.decide(command.approved(),
                command.decisionReason(), stamp(command.actor(), command.sourceChannel())));
        auditPort.record(command.actor(), command.sourceChannel(), decided.siteCode(),
                AuditAction.EVIDENCE_EXPORT_DECIDED, EXPORT_REQUEST, decided.id().toString(),
                existing.auditImage(), decided.auditImage());
        return decided;
    }

    @Transactional
    public EvidenceExportRequest export(ExportEvidence command) {
        EvidenceExportRequest existing = requireExportRequest(command.exportRequestId());
        EvidenceReference evidence = requireEvidence(existing.evidenceId());
        accessPolicy.require(command.actor(), SflPermission.FLEET_EVIDENCE_EXPORT_REQUEST, evidence.siteCode(),
                EXPORT_REQUEST, existing.id().toString());
        EvidenceExportRequest exported = exportRequests.save(existing.markExported(stamp(command.actor(),
                command.sourceChannel())));
        auditPort.record(command.actor(), command.sourceChannel(), evidence.siteCode(), AuditAction.EVIDENCE_EXPORTED,
                EXPORT_REQUEST, exported.id().toString(), existing.auditImage(), exported.auditImage());
        return exported;
    }

    @Transactional(noRollbackFor = AuditChainFailureException.class)
    public AuditChainVerification verifyAuditChain(ActorContext actor) {
        accessPolicy.requirePermission(actor, SflPermission.FLEET_AUDIT_INTEGRITY_CHECK, "AuditChain");
        AuditChainVerification result = auditPort.verifyChain();
        auditPort.record(actor, gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel.API,
                SiteCode.of("UNSCOPED"), AuditAction.AUDIT_INTEGRITY_CHECK, "AuditChain", "fleet",
                null, auditImage(result));
        if (!result.intact()) {
            Map<String, Object> payload = auditImage(result);
            eventPublisher.publish(FleetEventType.FLEET_AUDIT_INTEGRITY_FAILED, "AuditChain", "fleet",
                    SiteCode.of("UNSCOPED"), actor, payload);
            throw new AuditChainFailureException(payload);
        }
        return result;
    }

    private EvidenceReference requireEvidence(UUID id) {
        return evidenceRepository.findById(id).orElseThrow(() -> RecordNotFoundException.of(EVIDENCE, id));
    }

    private EvidenceExportRequest requireExportRequest(UUID id) {
        return exportRequests.findById(id).orElseThrow(() -> RecordNotFoundException.of(EXPORT_REQUEST, id));
    }

    private EvidenceReference.ActorStamp stamp(ActorContext actor,
            gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel sourceChannel) {
        return new EvidenceReference.ActorStamp(actor.actorId(), clock.instant(), sourceChannel,
                actor.correlationId());
    }

    private static Map<String, Object> auditImage(AuditChainVerification result) {
        Map<String, Object> image = new LinkedHashMap<>();
        image.put("intact", result.intact());
        image.put("recordsChecked", result.recordsChecked());
        image.put("firstDivergentSequence", result.firstDivergentSequence());
        image.put("expectedValue", result.expectedValue());
        image.put("actualValue", result.actualValue());
        image.put("reason", result.reason());
        image.put("headHash", result.headHash());
        return image;
    }
}
