package gh.edu.clet.sfl.safetysecurity.accesscontrol.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.AccessException;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.ExceptionRuleCode;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.ExceptionSeverity;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.ExceptionStatus;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.support.AccessControlTestDoubles;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** SRS-SFL-S160a-04: a genuinely security-relevant exception (forced-open, tailgating, restricted
 * zone) seeds a security incident and is forwarded to SIEM; a reader-health or repeated-denial
 * exception is forwarded to SIEM but does not seed one - see {@code AccessExceptionService.seedsIncident}. */
class AccessExceptionServiceTest {

    private static final String SITE = "E2E-HQ";
    private final AccessControlTestDoubles.InMemoryAccessControlRepository repository =
            new AccessControlTestDoubles.InMemoryAccessControlRepository();
    private final AccessControlTestDoubles.FakeSiemForwarder siem = new AccessControlTestDoubles.FakeSiemForwarder();
    private final AccessControlTestDoubles.FakeIncidentSeedingPort incidentSeeding =
            new AccessControlTestDoubles.FakeIncidentSeedingPort();
    private final AccessControlTestDoubles.FakeAuditPort audit = new AccessControlTestDoubles.FakeAuditPort();
    private final AccessControlTestDoubles.FakeEventPublisher events = new AccessControlTestDoubles.FakeEventPublisher();
    private final AccessControlAccessPolicy access = new AccessControlAccessPolicy();
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-22T10:00:00Z"), ZoneOffset.UTC);
    private final AccessExceptionService service = new AccessExceptionService(repository, siem, incidentSeeding,
            audit, events, access, clock);
    private final ActorContext systemActor = AccessControlTestDoubles.actor("integration:vendor", SflRole.SFL_ADMIN,
            "*");

    @Test
    void a_forced_open_exception_seeds_an_incident_and_forwards_to_siem() {
        AccessException raised = service.raise(SITE, null, "reader-1", "zone-main", ExceptionRuleCode.FORCED_OPEN,
                ExceptionSeverity.CRITICAL, systemActor);

        assertThat(raised.seededIncidentId()).isEqualTo(incidentSeeding.nextIncidentId);
        assertThat(siem.forwarded).hasSize(1);
        assertThat(raised.siemForwardedAt()).isNotNull();
    }

    @Test
    void a_repeated_denial_exception_forwards_to_siem_but_does_not_seed_an_incident() {
        AccessException raised = service.raise(SITE, null, "reader-1", "zone-main", ExceptionRuleCode.REPEATED_DENIAL,
                ExceptionSeverity.MEDIUM, systemActor);

        assertThat(raised.seededIncidentId()).isNull();
        assertThat(siem.forwarded).hasSize(1);
        assertThat(incidentSeeding.descriptions).isEmpty();
    }

    @Test
    void the_soc_can_acknowledge_and_then_resolve_an_open_exception() {
        AccessException raised = service.raise(SITE, null, "reader-1", "zone-main", ExceptionRuleCode.READER_OFFLINE,
                ExceptionSeverity.HIGH, systemActor);
        ActorContext socOperator = AccessControlTestDoubles.actor("soc-1", SflRole.SOC_OPERATOR, SITE);

        AccessException acknowledged = service.acknowledge(raised.id(), socOperator);
        assertThat(acknowledged.status()).isEqualTo(ExceptionStatus.ACKNOWLEDGED);

        AccessException resolved = service.resolve(raised.id(), socOperator);
        assertThat(resolved.status()).isEqualTo(ExceptionStatus.RESOLVED);
    }
}
