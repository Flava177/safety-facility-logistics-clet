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
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.AuditPort;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.EvidenceExportRequestRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.EvidenceRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.event.FleetEventType;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.AuditChainFailureException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.ExportNotApprovedException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.RetentionClassMissingException;
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
        service = new FleetEvidenceApplicationService(evidence, exportRequests, new FleetAccessPolicy(), audit,
                events, clock);
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
                new FleetAccessPolicy(), new BrokenAuditPort(), events, Clock.fixed(NOW, ZoneOffset.UTC));

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
            return new AuditChainVerification(false, 9, 4L, "expected", "actual",
                    "Hash mismatch at sequence 4", null);
        }
    }
}
