package gh.edu.clet.sfl.fleetlogistics.fleet.application.service;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.EvidenceCommands.DecideEvidenceExport;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.EvidenceCommands.ExportEvidence;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.EvidenceCommands.RegisterEvidence;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.EvidenceCommands.RequestEvidenceExport;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.EvidenceCommands.UploadEvidence;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.AuditPort;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.EvidenceExportRequestRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.EvidenceFileStore;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.EvidenceRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.IntegrationEventPublisher;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.event.FleetEventType;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.AuditChainFailureException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.EvidenceContentMissingException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.RecordNotFoundException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.AuditAction;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.AuditChainVerification;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.EvidenceExportRequest;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.EvidenceReference;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.policy.UploadedFileScanner;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Evidence and audit trail use cases for SRS-SFL-S166-03. */
@Service
public class FleetEvidenceApplicationService {

    private static final String EVIDENCE = "EvidenceReference";
    private static final String EXPORT_REQUEST = "EvidenceExportRequest";
    /** The only verdict that reaches storage today; see the migration for why the column is wider. */
    private static final String SCAN_ACCEPTED = "ACCEPTED";

    private final EvidenceRepository evidenceRepository;
    private final EvidenceExportRequestRepository exportRequests;
    private final EvidenceFileStore fileStore;
    private final FleetAccessPolicy accessPolicy;
    private final AuditPort auditPort;
    private final IntegrationEventPublisher eventPublisher;
    private final Clock clock;

    public FleetEvidenceApplicationService(EvidenceRepository evidenceRepository,
            EvidenceExportRequestRepository exportRequests, EvidenceFileStore fileStore,
            FleetAccessPolicy accessPolicy, AuditPort auditPort,
            IntegrationEventPublisher eventPublisher, Clock clock) {
        this.evidenceRepository = evidenceRepository;
        this.exportRequests = exportRequests;
        this.fileStore = fileStore;
        this.accessPolicy = accessPolicy;
        this.auditPort = auditPort;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /**
     * Registers evidence <em>and</em> the file it refers to, in one transaction.
     *
     * <h2>Why this is one call and not two</h2>
     *
     * <p>Registration and upload as separate steps is how the platform ended up with references whose
     * bytes were never anywhere: the first call succeeds, the second is forgotten or fails, and what
     * remains is a row asserting that a certificate exists. Here the metadata is derived <em>from</em>
     * the bytes - name, type and digest all come out of the scan - so there is no window in which they
     * can disagree and no opportunity to register a hash the platform did not compute itself.
     *
     * <p>The storage reference is still recorded, and still names the scheme holding the file, because
     * the day an object store arrives that column is what tells an operator which records were
     * migrated and which were not.
     */
    @Transactional
    public EvidenceReference upload(UploadEvidence command) {
        SiteCode site = SiteCode.of(command.siteCode());
        accessPolicy.require(command.actor(), SflPermission.FLEET_EVIDENCE_REGISTER, site, EVIDENCE, null);

        UploadedFileScanner.Verdict verdict = UploadedFileScanner.scan(command.fileName(),
                command.declaredContentType(), command.content());

        UUID id = UUID.randomUUID();
        EvidenceReference evidence = EvidenceReference.register(id, site, command.relatedRecordType(),
                command.relatedRecordId(), command.evidenceType(), verdict.fileName(), verdict.contentType(),
                storageReference(id, site, command.relatedRecordType(), verdict.fileName()),
                verdict.sha256Hash(), command.retentionClass(), command.retentionExpiresAt(),
                stamp(command.actor(), command.sourceChannel()));
        // Flushed, not merely saved: the content table has a foreign key onto this row, and the file
        // store writes with JDBC rather than JPA. Without the flush the child insert reaches the
        // database first and the constraint fails.
        EvidenceReference saved = evidenceRepository.saveAndFlush(evidence);
        fileStore.store(saved.id(), command.content(), verdict.contentType(), SCAN_ACCEPTED,
                "Extension, magic bytes and declared type agree" + (verdict.kind() == UploadedFileScanner.Kind.PDF
                        ? "; no active PDF content found" : ""),
                clock.instant(), command.actor().actorId());

        Map<String, Object> image = new LinkedHashMap<>(saved.auditImage());
        image.put("byteSize", verdict.byteSize());
        image.put("scanStatus", SCAN_ACCEPTED);
        auditPort.record(command.actor(), command.sourceChannel(), site, AuditAction.EVIDENCE_REGISTERED,
                EVIDENCE, saved.id().toString(), null, image);
        eventPublisher.publish(FleetEventType.FLEET_EVIDENCE_REGISTERED, EVIDENCE, saved.id().toString(),
                site, command.actor(), image);
        return saved;
    }

    /**
     * The bytes, for a download or a preview.
     *
     * <p>Reading a file is an access event in its own right and is recorded as one. That is not
     * bookkeeping for its own sake: S166-03 makes evidence access auditable, and a download is the
     * access that matters - "who has seen this certificate" is a question an investigation asks.
     */
    @Transactional
    public EvidenceFileStore.StoredFile content(UUID evidenceId, ActorContext actor, SourceChannel sourceChannel) {
        EvidenceReference evidence = findById(evidenceId, actor);
        EvidenceFileStore.StoredFile file = fileStore.find(evidenceId)
                .orElseThrow(() -> new EvidenceContentMissingException(Map.of(
                        "evidenceId", evidenceId.toString(),
                        "storageReference", evidence.storageReference())));
        auditPort.record(actor, sourceChannel, evidence.siteCode(), AuditAction.EVIDENCE_VIEWED, EVIDENCE,
                evidence.id().toString(), null, Map.of(
                        "evidenceId", evidence.id().toString(),
                        "fileName", evidence.fileName(),
                        "sha256Hash", evidence.sha256Hash(),
                        "byteSize", file.byteSize()));
        return file;
    }

    /** Whether bytes are held for this record, so a screen can offer a download link only when there is one. */
    @Transactional(readOnly = true)
    public boolean hasContent(UUID evidenceId) {
        return fileStore.exists(evidenceId);
    }

    /** The same question for a whole list, in one query. */
    @Transactional(readOnly = true)
    public java.util.Set<UUID> withContent(List<EvidenceReference> evidence) {
        return fileStore.withContent(evidence.stream().map(EvidenceReference::id).toList());
    }

    /**
     * Other evidence at this site holding these exact bytes.
     *
     * <p>Exposed for the fuel reconciliation rules, which treat a re-submitted receipt or pump
     * photograph as an anomaly. It answers with references rather than a boolean so the anomaly can
     * name what the file was filed against last time.
     */
    @Transactional(readOnly = true)
    public List<EvidenceReference> findDuplicatesOf(EvidenceReference evidence) {
        return evidenceRepository.findBySha256(evidence.siteCode().value(), evidence.sha256Hash(), evidence.id());
    }

    /**
     * A deterministic key naming where the file is.
     *
     * <p>{@code sfl-evidence://} rather than the old {@code local-demo://}: the scheme is the honest
     * statement of where the bytes are, and they are now genuinely in the platform rather than
     * nowhere. Rows written before this still say {@code local-demo://}, which is exactly the
     * distinction anyone migrating to an object store will need.
     */
    private static String storageReference(UUID id, SiteCode site, String relatedRecordType, String fileName) {
        String safeType = relatedRecordType.replaceAll("[^\\w.-]+", "_").toLowerCase(java.util.Locale.ROOT);
        return "sfl-evidence://" + site.value() + "/" + safeType + "/" + id + "/" + fileName;
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
    /**
     * Evidence attached to one record.
     *
     * <p>Closes gap 5, which the register called the main usability cost in the whole dashboard: with
     * no search, every closure dialog asked an operator to paste an evidence reference id from
     * somewhere else. The repository has answered this question since the service was built and
     * nothing exposed it.
     */
    public List<EvidenceReference> findByRelatedRecord(String relatedRecordType, String relatedRecordId,
            ActorContext actor) {
        accessPolicy.requirePermission(actor, SflPermission.FLEET_EVIDENCE_READ, EVIDENCE);
        return evidenceRepository.findByRelatedRecord(relatedRecordType, relatedRecordId,
                accessPolicy.requireSiteScopeFilter(actor));
    }

    /**
     * Records that somebody looked at this evidence, and returns it.
     *
     * <p>Writable rather than {@code readOnly}, and transactional at all, because of the audit entry.
     * {@link gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.audit.JpaAuditAdapter#record} is
     * declared {@code MANDATORY} on purpose - it must join the caller's transaction so that an audit
     * row can never outlive a rolled-back operation - and this method had no transaction at all, so
     * every call failed with {@code IllegalTransactionStateException} before it wrote anything. The
     * fix belongs here and not on the adapter: making the adapter {@code REQUIRES_NEW} would let
     * audit writes survive the rollback of the operation they describe, everywhere, to spare this
     * one method an annotation.
     */
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
