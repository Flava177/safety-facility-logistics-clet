package gh.edu.clet.sfl.safetysecurity.cctv.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.EvidenceItem;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.EvidenceItemStatus;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.RetentionScope;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.SourceChannel;
import gh.edu.clet.sfl.safetysecurity.cctv.support.CctvTestDoubles;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** SRS-SFL-S161-05 acceptance criteria: an evidence item past its camera's retention window is purged
 * unless a legal hold applies. */
class RetentionServiceTest {

    private static final String SITE = "E2E-HQ";
    private final CctvTestDoubles.InMemoryCctvRepository repository = new CctvTestDoubles.InMemoryCctvRepository();
    private final CctvTestDoubles.FakeAuditPort audit = new CctvTestDoubles.FakeAuditPort();
    private final CctvTestDoubles.FakeEventPublisher events = new CctvTestDoubles.FakeEventPublisher();
    private final CctvAccessPolicy access = new CctvAccessPolicy();
    private final ActorContext complianceOfficer = CctvTestDoubles.actor("compliance-1", SflRole.COMPLIANCE_OFFICER,
            SITE);

    @Test
    void an_evidence_item_past_its_retention_window_is_purged_when_no_legal_hold_applies() {
        Instant createdAt = Instant.parse("2026-01-01T10:00:00Z");
        seedItem(createdAt);
        RetentionService service = serviceAt(createdAt.plusSeconds(40L * 24 * 3600));
        service.define(new RetentionService.DefinePolicy(SITE, RetentionScope.CAMERA, "CAM-01", 30,
                complianceOfficer));

        service.purgeExpiredEvidence();

        EvidenceItem purged = repository.findEvidenceItemsByStatus(EvidenceItemStatus.PURGED).get(0);
        assertThat(purged.status()).isEqualTo(EvidenceItemStatus.PURGED);
    }

    @Test
    void a_legal_hold_prevents_the_purge_sweep_from_removing_the_item() {
        Instant createdAt = Instant.parse("2026-01-01T10:00:00Z");
        seedItem(createdAt);
        RetentionService service = serviceAt(createdAt.plusSeconds(40L * 24 * 3600));
        service.define(new RetentionService.DefinePolicy(SITE, RetentionScope.CAMERA, "CAM-01", 30,
                complianceOfficer));
        service.placeLegalHold(SITE, RetentionScope.CAMERA, "CAM-01", "Ongoing investigation", complianceOfficer);

        service.purgeExpiredEvidence();

        assertThat(repository.findEvidenceItemsByStatus(EvidenceItemStatus.PURGED)).isEmpty();
        assertThat(repository.findEvidenceItemsByStatus(EvidenceItemStatus.ACTIVE)).hasSize(1);
    }

    private void seedItem(Instant createdAt) {
        EvidenceItem item = EvidenceItem.record(UUID.randomUUID(), UUID.randomUUID(), SITE, "CAM-01", createdAt,
                createdAt.plusSeconds(3600), "handle-1", "hash-1", "provenance", null, "soc-1", createdAt,
                SourceChannel.WEB, "corr-1");
        repository.saveEvidenceItem(item);
    }

    private RetentionService serviceAt(Instant now) {
        return new RetentionService(repository, audit, events, access, Clock.fixed(now, ZoneOffset.UTC), 30);
    }
}
