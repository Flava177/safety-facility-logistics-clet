package gh.edu.clet.sfl.safetysecurity.intrusion.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.exception.IntrusionException;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.AlarmStatus;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.AlarmType;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.DisarmOverride;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.DisarmOverrideStatus;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.IntrusionZone;
import gh.edu.clet.sfl.safetysecurity.intrusion.support.IntrusionTestDoubles;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/**
 * SRS-SFL-S162-04 acceptance criteria: a disarm reverts to armed automatically at expiry; a zone that
 * fails to arm, or is disarmed out of policy, raises a SOC exception (here, an
 * {@link AlarmType#ARM_FAILURE} alarm).
 */
class IntrusionZoneServiceTest {

    private static final String SITE = "E2E-HQ";
    private final IntrusionTestDoubles.InMemoryIntrusionRepository repository =
            new IntrusionTestDoubles.InMemoryIntrusionRepository();
    private final IntrusionTestDoubles.FakePanelGateway panelGateway = new IntrusionTestDoubles.FakePanelGateway();
    private final IntrusionTestDoubles.FakeAuditPort audit = new IntrusionTestDoubles.FakeAuditPort();
    private final IntrusionTestDoubles.FakeEventPublisher events = new IntrusionTestDoubles.FakeEventPublisher();
    private final IntrusionAccessPolicy access = new IntrusionAccessPolicy();

    @Test
    void an_active_disarm_past_its_expiry_is_reverted_by_the_sweep() {
        Clock atCreation = Clock.fixed(Instant.parse("2026-09-22T10:00:00Z"), ZoneOffset.UTC);
        IntrusionIngestionService ingestion = new IntrusionIngestionService(repository, null, audit, events,
                atCreation);
        IntrusionZoneService creationService = new IntrusionZoneService(repository, panelGateway, ingestion, audit,
                events, access, atCreation);
        ActorContext director = IntrusionTestDoubles.actor("director-1", SflRole.SECURITY_DIRECTOR, SITE);

        creationService.define(new IntrusionZoneService.DefineZone(SITE, "VAULT-1", "Exam Vault", null,
                "Mon-Fri 07:00-19:00", true, director));
        DisarmOverride override = creationService.requestDisarm(new IntrusionZoneService.RequestDisarm(SITE,
                "VAULT-1", "Maintenance access", "security-director-1", Instant.parse("2026-09-22T10:00:00Z"),
                Instant.parse("2026-09-22T11:00:00Z"), director));
        assertThat(repository.findActiveDisarmOverrides()).hasSize(1);
        assertThat(repository.findZoneByCode(SITE, "VAULT-1").orElseThrow().armed()).isFalse();

        Clock afterExpiry = Clock.fixed(Instant.parse("2026-09-22T12:00:00Z"), ZoneOffset.UTC);
        IntrusionZoneService sweepService = new IntrusionZoneService(repository, panelGateway, ingestion, audit,
                events, access, afterExpiry);
        sweepService.expireOverdueDisarms();

        DisarmOverride reverted = repository.findDisarmOverride(override.id()).orElseThrow();
        assertThat(reverted.status()).isEqualTo(DisarmOverrideStatus.EXPIRED);
        assertThat(repository.findActiveDisarmOverrides()).isEmpty();
        assertThat(repository.findZoneByCode(SITE, "VAULT-1").orElseThrow().armed()).isTrue();
    }

    @Test
    void a_zone_that_fails_to_arm_when_confirmed_raises_an_arm_failure_alarm() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-22T10:00:00Z"), ZoneOffset.UTC);
        IntrusionIngestionService ingestion = new IntrusionIngestionService(repository, null, audit, events, clock);
        IntrusionZoneService service = new IntrusionZoneService(repository, panelGateway, ingestion, audit, events,
                access, clock);
        ActorContext director = IntrusionTestDoubles.actor("director-1", SflRole.SECURITY_DIRECTOR, SITE);

        IntrusionZone zone = service.define(new IntrusionZoneService.DefineZone(SITE, "VAULT-1", "Exam Vault", null,
                "Mon-Fri 07:00-19:00", true, director));

        service.confirmArmed(new IntrusionZoneService.ConfirmArmed(zone.id(), false, 0L, director));

        assertThat(repository.findAlarmsByStatus(SITE, AlarmStatus.RAISED)).hasSize(1)
                .allSatisfy(alarm -> assertThat(alarm.alarmType()).isEqualTo(AlarmType.ARM_FAILURE));
        assertThat(repository.findZoneByCode(SITE, "VAULT-1").orElseThrow().armed()).isFalse();
    }

    @Test
    void a_disarm_request_without_disarm_authority_is_refused() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-22T10:00:00Z"), ZoneOffset.UTC);
        IntrusionIngestionService ingestion = new IntrusionIngestionService(repository, null, audit, events, clock);
        IntrusionZoneService service = new IntrusionZoneService(repository, panelGateway, ingestion, audit, events,
                access, clock);
        ActorContext director = IntrusionTestDoubles.actor("director-1", SflRole.SECURITY_DIRECTOR, SITE);
        service.define(new IntrusionZoneService.DefineZone(SITE, "VAULT-1", "Exam Vault", null,
                "Mon-Fri 07:00-19:00", true, director));
        ActorContext auditor = IntrusionTestDoubles.actor("auditor-1", SflRole.COMPLIANCE_OFFICER, SITE);

        assertThatThrownBy(() -> service.requestDisarm(new IntrusionZoneService.RequestDisarm(SITE, "VAULT-1",
                "Maintenance access", "security-director-1", Instant.parse("2026-09-22T10:00:00Z"),
                Instant.parse("2026-09-22T11:00:00Z"), auditor)))
                .isInstanceOf(IntrusionException.class);
    }
}
