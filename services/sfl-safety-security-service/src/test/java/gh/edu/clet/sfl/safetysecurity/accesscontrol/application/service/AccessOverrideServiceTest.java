package gh.edu.clet.sfl.safetysecurity.accesscontrol.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.exception.AccessControlException;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.AccessOverride;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.OverrideStatus;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.support.AccessControlTestDoubles;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** SRS-SFL-S160a-03 acceptance criteria: a break-glass override needs no approver at creation and is
 * approved after the fact; an override reverts automatically once its window has passed. */
class AccessOverrideServiceTest {

    private static final String SITE = "E2E-HQ";
    private final AccessControlTestDoubles.InMemoryAccessControlRepository repository =
            new AccessControlTestDoubles.InMemoryAccessControlRepository();
    private final AccessControlTestDoubles.FakeVendorGateway vendorGateway =
            new AccessControlTestDoubles.FakeVendorGateway();
    private final AccessControlTestDoubles.FakeAuditPort audit = new AccessControlTestDoubles.FakeAuditPort();
    private final AccessControlTestDoubles.FakeEventPublisher events = new AccessControlTestDoubles.FakeEventPublisher();
    private final AccessControlAccessPolicy access = new AccessControlAccessPolicy();

    @Test
    void a_break_glass_override_needs_no_approver_and_is_approved_after_the_fact() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-22T10:00:00Z"), ZoneOffset.UTC);
        AccessOverrideService service = new AccessOverrideService(repository, vendorGateway, audit, events, access,
                clock);
        ActorContext socOperator = AccessControlTestDoubles.actor("soc-1", SflRole.SOC_OPERATOR, SITE);

        AccessOverride override = service.requestBreakGlass(new AccessOverrideService.RequestBreakGlassOverride(
                SITE, "door:main-gate", "Fire drill lockdown release", Instant.parse("2026-09-22T10:00:00Z"),
                Instant.parse("2026-09-22T11:00:00Z"), socOperator));

        assertThat(override.breakGlass()).isTrue();
        assertThat(override.approverId()).isNull();
        assertThat(override.status()).isEqualTo(OverrideStatus.ACTIVE);

        AccessOverride approved = service.recordPostHocApproval(override.id(), "security-director-1", socOperator);
        assertThat(approved.approverId()).isEqualTo("security-director-1");
    }

    @Test
    void a_normal_override_request_without_override_authority_is_refused() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-22T10:00:00Z"), ZoneOffset.UTC);
        AccessOverrideService service = new AccessOverrideService(repository, vendorGateway, audit, events, access,
                clock);
        ActorContext auditor = AccessControlTestDoubles.actor("auditor-1", SflRole.AUDITOR, SITE);

        assertThatThrownBy(() -> service.request(new AccessOverrideService.RequestOverride(SITE, "door:main-gate",
                "Vendor delivery", "security-director-1", Instant.parse("2026-09-22T10:00:00Z"),
                Instant.parse("2026-09-22T11:00:00Z"), auditor)))
                .isInstanceOf(AccessControlException.class);
    }

    @Test
    void an_active_override_past_its_expiry_is_reverted_by_the_sweep() {
        Clock atCreation = Clock.fixed(Instant.parse("2026-09-22T10:00:00Z"), ZoneOffset.UTC);
        AccessOverrideService creationService = new AccessOverrideService(repository, vendorGateway, audit, events,
                access, atCreation);
        ActorContext socOperator = AccessControlTestDoubles.actor("soc-1", SflRole.SOC_OPERATOR, SITE);
        AccessOverride override = creationService.request(new AccessOverrideService.RequestOverride(SITE,
                "door:main-gate", "Vendor delivery", "security-director-1", Instant.parse("2026-09-22T10:00:00Z"),
                Instant.parse("2026-09-22T11:00:00Z"), socOperator));
        assertThat(repository.findActiveOverrides()).hasSize(1);

        Clock afterExpiry = Clock.fixed(Instant.parse("2026-09-22T12:00:00Z"), ZoneOffset.UTC);
        AccessOverrideService sweepService = new AccessOverrideService(repository, vendorGateway, audit, events,
                access, afterExpiry);
        sweepService.expireOverdueOverrides();

        AccessOverride reverted = repository.findOverride(override.id()).orElseThrow();
        assertThat(reverted.status()).isEqualTo(OverrideStatus.EXPIRED);
        assertThat(repository.findActiveOverrides()).isEmpty();
    }
}
