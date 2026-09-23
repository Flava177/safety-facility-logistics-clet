package gh.edu.clet.sfl.safetysecurity.cctv.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.exception.CctvErrorCode;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.exception.CctvException;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.EvidenceAccessAction;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.EvidenceItem;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.EvidenceRequest;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.EvidenceRequestStatus;
import gh.edu.clet.sfl.safetysecurity.cctv.support.CctvTestDoubles;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

/** SRS-SFL-S161-02/03 acceptance criteria: no footage is retrieved from a request that is not
 * approved; an unauthorised actor cannot approve one; a retrieval from an approved request records an
 * evidence item with a hash and logs the export as an access. */
class EvidenceRequestServiceTest {

    private static final String SITE = "E2E-HQ";
    private final CctvTestDoubles.InMemoryCctvRepository repository = new CctvTestDoubles.InMemoryCctvRepository();
    private final CctvTestDoubles.FakeVendorGateway vendorGateway = new CctvTestDoubles.FakeVendorGateway();
    private final CctvTestDoubles.FakeAuditPort audit = new CctvTestDoubles.FakeAuditPort();
    private final CctvTestDoubles.FakeEventPublisher events = new CctvTestDoubles.FakeEventPublisher();
    private final CctvAccessPolicy access = new CctvAccessPolicy();
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-23T10:00:00Z"), ZoneOffset.UTC);
    private final EvidenceRequestService service = new EvidenceRequestService(repository, vendorGateway, audit,
            events, access, clock);
    private final ActorContext socOperator = CctvTestDoubles.actor("soc-1", SflRole.SOC_OPERATOR, SITE);
    private final ActorContext securityDirector = CctvTestDoubles.actor("director-1", SflRole.SECURITY_DIRECTOR,
            SITE);

    @Test
    void retrieval_is_blocked_until_the_request_is_approved() {
        EvidenceRequest request = createRequest();

        assertThatThrownBy(() -> service.retrieve(new EvidenceRequestService.Retrieve(request.id(), "CAM-01",
                socOperator)))
                .isInstanceOf(CctvException.class)
                .satisfies(e -> assertThat(((CctvException) e).errorCode())
                        .isEqualTo(CctvErrorCode.CCTV_EVIDENCE_REQUEST_NOT_APPROVED));
    }

    @Test
    void an_soc_operator_lacks_authority_to_approve_an_evidence_request() {
        EvidenceRequest request = createRequest();

        assertThatThrownBy(() -> service.approve(new EvidenceRequestService.Decide(request.id(), null, socOperator)))
                .isInstanceOf(CctvException.class)
                .satisfies(e -> assertThat(((CctvException) e).errorCode())
                        .isEqualTo(CctvErrorCode.CCTV_UNAUTHORIZED_SCOPE));
    }

    @Test
    void an_approved_request_can_be_retrieved_and_records_a_hashed_evidence_item_with_an_export_access_log() {
        EvidenceRequest request = createRequest();

        EvidenceRequest approved = service.approve(new EvidenceRequestService.Decide(request.id(), null,
                securityDirector));
        assertThat(approved.status()).isEqualTo(EvidenceRequestStatus.APPROVED);

        EvidenceItem item = service.retrieve(new EvidenceRequestService.Retrieve(request.id(), "CAM-01",
                socOperator));
        assertThat(item.hash()).isNotBlank();
        assertThat(item.exportHandle()).isNotBlank();
        assertThat(item.copiedRawVideo()).isFalse();

        List<gh.edu.clet.sfl.safetysecurity.cctv.domain.model.EvidenceAccessLog> log = service.accessLogFor(
                item.id(), socOperator);
        assertThat(log).hasSize(1);
        assertThat(log.get(0).action()).isEqualTo(EvidenceAccessAction.EXPORTED);
    }

    private EvidenceRequest createRequest() {
        return service.create(new EvidenceRequestService.CreateRequest(SITE, List.of("CAM-01"), "Main Gate",
                Instant.parse("2026-09-23T08:00:00Z"), Instant.parse("2026-09-23T09:00:00Z"),
                "Suspected tailgating investigation", null, socOperator));
    }
}
