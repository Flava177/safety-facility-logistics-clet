package gh.edu.clet.sfl.fleetlogistics.fleet.application.service;

import static gh.edu.clet.sfl.fleetlogistics.fleet.support.FleetFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.EvidenceCommands.DecideEvidenceExport;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.EvidenceCommands.ExportEvidence;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.EvidenceCommands.RegisterEvidence;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.EvidenceCommands.RequestEvidenceExport;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.EvidenceCommands.UploadEvidence;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.AuditPort;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.EvidenceExportRequestRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.EvidenceFileStore;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.EvidenceRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.event.FleetEventType;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.AuditChainFailureException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.ExportNotApprovedException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.RetentionClassMissingException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.UnsafeUploadException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.AuditAction;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.AuditChainVerification;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.AuditEvent;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.EvidenceExportRequest;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.EvidenceExportStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.EvidenceReference;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.EvidenceRetentionClass;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import gh.edu.clet.sfl.fleetlogistics.fleet.support.FleetTestDoubles;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Traces: SRS-SFL-S166-03 evidence retention, export approval and audit integrity. */
class FleetEvidenceApplicationServiceTest {

    private InMemoryEvidenceRepository evidence;
    private InMemoryEvidenceFileStore fileStore;
    private InMemoryExportRequestRepository exportRequests;
    private FleetTestDoubles.RecordingAuditPort audit;
    private FleetTestDoubles.RecordingEventPublisher events;
    private FleetEvidenceApplicationService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        evidence = new InMemoryEvidenceRepository();
        exportRequests = new InMemoryExportRequestRepository();
        audit = new FleetTestDoubles.RecordingAuditPort(clock);
        events = new FleetTestDoubles.RecordingEventPublisher();
        fileStore = new InMemoryEvidenceFileStore();
        service = new FleetEvidenceApplicationService(evidence, exportRequests, fileStore,
                new FleetAccessPolicy(), audit, events, clock);
    }

    @Test
    @DisplayName("evidence registration requires retention metadata and writes audit plus integration event")
    void evidence_registration_requires_retention_and_audits() {
        RegisterEvidence command = registerCommand(FleetTestDoubles.fleetOfficer("ACCRA"));

        EvidenceReference saved = service.register(command);

        assertThat(saved.retentionClass()).isEqualTo(EvidenceRetentionClass.COMPLIANCE_7_YEARS);
        assertThat(saved.sha256Hash()).isEqualTo(validSha256());
        assertThat(audit.hasRecord(AuditAction.EVIDENCE_REGISTERED, "EvidenceReference")).isTrue();
        assertThat(events.types()).contains(FleetEventType.FLEET_EVIDENCE_REGISTERED);

        assertThatThrownBy(() -> service.register(new RegisterEvidence("ACCRA", "FleetWorkflowItem",
                "wf-001", "CLOSURE_PHOTO", "closure.jpg", "image/jpeg", "s3://bucket/closure.jpg",
                validSha256(), null, Instant.parse("2033-01-01T00:00:00Z"),
                FleetTestDoubles.fleetOfficer("ACCRA"), SourceChannel.WEB)))
                .isInstanceOf(RetentionClassMissingException.class);
    }

    @Test
    @DisplayName("upload derives name, type and digest from the bytes rather than from the caller")
    void upload_derives_metadata_from_content() {
        byte[] jpeg = jpegBytes();

        EvidenceReference saved = service.upload(new UploadEvidence("ACCRA", "Vehicle", "veh-1",
                "ROADWORTHINESS_CERTIFICATE", "cert.jpg", "application/octet-stream", jpeg,
                EvidenceRetentionClass.COMPLIANCE_7_YEARS, null, FleetTestDoubles.fleetOfficer("ACCRA"),
                SourceChannel.WEB));

        // The caller said "octet-stream"; the bytes say JPEG, and the bytes win.
        assertThat(saved.contentType()).isEqualTo("image/jpeg");
        assertThat(saved.sha256Hash()).isEqualTo(sha256Of(jpeg));
        // The scheme is the honest statement that the platform holds this one, unlike local-demo://.
        assertThat(saved.storageReference()).startsWith("sfl-evidence://ACCRA/");
        assertThat(fileStore.exists(saved.id())).isTrue();
        assertThat(service.content(saved.id(), FleetTestDoubles.fleetOfficer("ACCRA"), SourceChannel.WEB)
                .content()).isEqualTo(jpeg);
        // Reading a file is itself an access event; S166-03 makes evidence access auditable.
        assertThat(audit.hasRecord(AuditAction.EVIDENCE_VIEWED, "EvidenceReference")).isTrue();
    }

    @Test
    @DisplayName("a file whose contents contradict its name is refused, and nothing is registered")
    void upload_refuses_a_renamed_file() {
        // "MZ" - a Windows executable wearing a .pdf extension. The extension allowlist alone passes
        // this; the magic-byte check is what stops it.
        byte[] executable = {'M', 'Z', (byte) 0x90, 0x00, 0x03, 0x00};

        assertThatThrownBy(() -> service.upload(new UploadEvidence("ACCRA", "Vehicle", "veh-1",
                "INSURANCE_CERTIFICATE", "insurance.pdf", "application/pdf", executable,
                EvidenceRetentionClass.COMPLIANCE_7_YEARS, null, FleetTestDoubles.fleetOfficer("ACCRA"),
                SourceChannel.WEB)))
                .isInstanceOf(UnsafeUploadException.class)
                .hasMessageContaining("not a PDF, JPG or JPEG file");

        assertThat(evidence.store).isEmpty();
    }

    @Test
    @DisplayName("a structurally valid PDF carrying JavaScript is refused")
    void upload_refuses_active_pdf_content() {
        byte[] dropper = ("%PDF-1.7\n1 0 obj<</Type/Catalog/OpenAction<</S/JavaScript/JS(app.alert('x'))>>>>"
                + "endobj\n%%EOF").getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);

        assertThatThrownBy(() -> service.upload(new UploadEvidence("ACCRA", "Vehicle", "veh-1",
                "ROADWORTHINESS_CERTIFICATE", "cert.pdf", "application/pdf", dropper,
                EvidenceRetentionClass.COMPLIANCE_7_YEARS, null, FleetTestDoubles.fleetOfficer("ACCRA"),
                SourceChannel.WEB)))
                .isInstanceOf(UnsafeUploadException.class)
                .hasMessageContaining("active content");
    }

    @Test
    @DisplayName("identical bytes filed twice are findable, which is what the fuel reuse rule needs")
    void identical_uploads_are_discoverable_by_digest() {
        byte[] photo = jpegBytes();
        EvidenceReference first = service.upload(uploadCommand("txn-1", photo));
        EvidenceReference second = service.upload(uploadCommand("txn-2", photo));

        assertThat(first.sha256Hash()).isEqualTo(second.sha256Hash());
        assertThat(service.findDuplicatesOf(second)).extracting(EvidenceReference::id).containsExactly(first.id());
        // Each record excludes itself, so a lone upload never looks like a duplicate of one.
        assertThat(service.findDuplicatesOf(first)).extracting(EvidenceReference::id).containsExactly(second.id());
    }

    private UploadEvidence uploadCommand(String recordId, byte[] content) {
        return new UploadEvidence("ACCRA", "FuelTransaction", recordId, "FUEL_PUMP_READING", "pump.jpg",
                "image/jpeg", content, EvidenceRetentionClass.COMPLIANCE_7_YEARS, null,
                FleetTestDoubles.fleetOfficer("ACCRA"), SourceChannel.WEB);
    }

    /** The smallest thing that sniffs as a JPEG: the SOI marker plus a little payload. */
    private static byte[] jpegBytes() {
        return new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 'J', 'F', 'I', 'F',
            0x00, 0x01, (byte) 0xFF, (byte) 0xD9};
    }

    private static String sha256Of(byte[] content) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(content);
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("export requires independent approval before evidence leaves the system")
    void export_requires_independent_approval() {
        EvidenceReference saved = service.register(registerCommand(FleetTestDoubles.fleetOfficer("ACCRA")));
        EvidenceExportRequest requested = service.requestExport(new RequestEvidenceExport(saved.id(),
                "External audit sample", FleetTestDoubles.auditor("ACCRA"), SourceChannel.WEB));

        assertThatThrownBy(() -> service.export(new ExportEvidence(requested.id(),
                FleetTestDoubles.auditor("ACCRA"), SourceChannel.WEB)))
                .isInstanceOf(ExportNotApprovedException.class);

        EvidenceExportRequest complianceRequested = service.requestExport(new RequestEvidenceExport(saved.id(),
                "Compliance pack", FleetTestDoubles.complianceOfficer("ACCRA"), SourceChannel.WEB));
        assertThatThrownBy(() -> service.decideExport(new DecideEvidenceExport(complianceRequested.id(), true,
                "Approved for statutory audit", FleetTestDoubles.complianceOfficer("ACCRA"), SourceChannel.WEB)))
                .isInstanceOf(ExportNotApprovedException.class);

        EvidenceExportRequest approved = service.decideExport(new DecideEvidenceExport(requested.id(), true,
                "Approved for statutory audit", FleetTestDoubles.complianceOfficer("ACCRA"), SourceChannel.WEB));
        EvidenceExportRequest exported = service.export(new ExportEvidence(approved.id(),
                FleetTestDoubles.auditor("ACCRA"), SourceChannel.WEB));

        assertThat(exported.status()).isEqualTo(EvidenceExportStatus.EXPORTED);
        assertThat(exported.decidedBy()).isEqualTo("compliance@clet.edu.gh");
        assertThat(audit.hasRecord(AuditAction.EVIDENCE_EXPORT_REQUESTED, "EvidenceExportRequest")).isTrue();
        assertThat(audit.hasRecord(AuditAction.EVIDENCE_EXPORT_DECIDED, "EvidenceExportRequest")).isTrue();
        assertThat(audit.hasRecord(AuditAction.EVIDENCE_EXPORTED, "EvidenceExportRequest")).isTrue();
    }

    @Test
    @DisplayName("audit replay failure publishes the critical integrity alert")
    void audit_replay_failure_publishes_alert() {
        FleetEvidenceApplicationService failing = new FleetEvidenceApplicationService(evidence, exportRequests,
                fileStore, new FleetAccessPolicy(), new BrokenAuditPort(), events,
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> failing.verifyAuditChain(FleetTestDoubles.complianceOfficer("*")))
                .isInstanceOf(AuditChainFailureException.class);

        assertThat(events.types()).contains(FleetEventType.FLEET_AUDIT_INTEGRITY_FAILED);
    }

    private static RegisterEvidence registerCommand(ActorContext actor) {
        return new RegisterEvidence("ACCRA", "FleetWorkflowItem", "wf-001", "CLOSURE_PHOTO",
                "closure.jpg", "image/jpeg", "s3://fleet-evidence/closure.jpg", validSha256(),
                EvidenceRetentionClass.COMPLIANCE_7_YEARS, Instant.parse("2033-01-01T00:00:00Z"),
                actor, SourceChannel.WEB);
    }

    private static String validSha256() {
        return "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    }

    private static final class InMemoryEvidenceRepository implements EvidenceRepository {

        private final Map<UUID, EvidenceReference> store = new LinkedHashMap<>();

        @Override
        public EvidenceReference save(EvidenceReference reference) {
            store.put(reference.id(), reference);
            return reference;
        }

        /**
         * Identical to {@link #save} here, and that is the point worth remembering.
         *
         * <p>An in-memory map has no persistence context and nothing to flush, so this double cannot
         * reproduce the defect the real adapter exists to avoid: JPA deferring the metadata INSERT
         * past the JDBC write that has a foreign key onto it. That was found by running against
         * PostgreSQL, not here, and no amount of tightening this double would have found it.
         */
        @Override
        public EvidenceReference saveAndFlush(EvidenceReference reference) {
            return save(reference);
        }

        @Override
        public Optional<EvidenceReference> findById(UUID id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public List<EvidenceReference> findByRelatedRecord(String relatedRecordType, String relatedRecordId,
                SiteScopeFilter scope) {
            return store.values().stream()
                    .filter(reference -> scope.permits(reference.siteCode().value()))
                    .filter(reference -> reference.relatedRecordType().equals(relatedRecordType))
                    .filter(reference -> reference.relatedRecordId().equals(relatedRecordId))
                    .toList();
        }

        @Override
        public List<EvidenceReference> findBySha256(String siteCode, String sha256Hash, UUID excludingId) {
            return store.values().stream()
                    .filter(reference -> reference.siteCode().value().equals(siteCode))
                    .filter(reference -> reference.sha256Hash().equalsIgnoreCase(sha256Hash))
                    .filter(reference -> excludingId == null || !reference.id().equals(excludingId))
                    .toList();
        }
    }

    /** Holds bytes in a map. Enough to prove upload stores what the scanner accepted, and no more. */
    private static final class InMemoryEvidenceFileStore implements EvidenceFileStore {

        private final Map<UUID, StoredFile> store = new LinkedHashMap<>();

        @Override
        public void store(UUID evidenceId, byte[] content, String contentType, String scanStatus,
                String scanDetail, Instant scannedAt, String uploadedBy) {
            store.put(evidenceId, new StoredFile(evidenceId, content, contentType, content.length));
        }

        @Override
        public Optional<StoredFile> find(UUID evidenceId) {
            return Optional.ofNullable(store.get(evidenceId));
        }

        @Override
        public boolean exists(UUID evidenceId) {
            return store.containsKey(evidenceId);
        }

        @Override
        public java.util.Set<UUID> withContent(java.util.Collection<UUID> evidenceIds) {
            return evidenceIds.stream().filter(store::containsKey)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
    }

    private static final class InMemoryExportRequestRepository implements EvidenceExportRequestRepository {

        private final Map<UUID, EvidenceExportRequest> store = new LinkedHashMap<>();

        @Override
        public EvidenceExportRequest save(EvidenceExportRequest request) {
            store.put(request.id(), request);
            return request;
        }

        @Override
        public Optional<EvidenceExportRequest> findById(UUID id) {
            return Optional.ofNullable(store.get(id));
        }
    }

    private static final class BrokenAuditPort implements AuditPort {

        @Override
        public AuditEvent record(ActorContext actor, SourceChannel sourceChannel, SiteCode siteScope,
                AuditAction action, String resourceType, String resourceId, Object beforeValue, Object afterValue) {
            return null;
        }

        @Override
        public void recordAuthorizationDenied(ActorContext actor, String siteScope, String resourceType,
                String resourceId, String requiredPermission, String reason) {
        }

        @Override
        public List<AuditEvent> search(AuditQuery query) {
            return List.of();
        }

        @Override
        public AuditChainVerification verifyChain() {
            return new AuditChainVerification(false, true, 9, 4L, "expected", "actual",
                    "Hash mismatch at sequence 4", null, null);
        }
    }
}
