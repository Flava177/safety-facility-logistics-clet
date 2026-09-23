package gh.edu.clet.sfl.safetysecurity.intrusion.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.AlarmSeverity;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.AlarmStatus;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.AlarmType;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.IntrusionAlarm;
import gh.edu.clet.sfl.safetysecurity.intrusion.support.IntrusionTestDoubles;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * SRS-SFL-S162-02/03 acceptance criteria: an unacknowledged critical alarm escalates automatically
 * once its window has passed, and an acknowledged alarm can be escalated into a security incident
 * with a full trail.
 */
class IntrusionAlarmServiceTest {

    private static final String SITE = "E2E-HQ";
    private final IntrusionTestDoubles.InMemoryIntrusionRepository repository =
            new IntrusionTestDoubles.InMemoryIntrusionRepository();
    private final IntrusionTestDoubles.FakeIncidentSeedingPort incidentSeeding =
            new IntrusionTestDoubles.FakeIncidentSeedingPort();
    private final IntrusionTestDoubles.FakeAuditPort audit = new IntrusionTestDoubles.FakeAuditPort();
    private final IntrusionTestDoubles.FakeEventPublisher events = new IntrusionTestDoubles.FakeEventPublisher();
    private final IntrusionAccessPolicy access = new IntrusionAccessPolicy();

    @Test
    void an_unacknowledged_critical_alarm_past_its_ack_window_escalates_automatically() {
        Clock atRaise = Clock.fixed(Instant.parse("2026-09-22T10:00:00Z"), ZoneOffset.UTC);
        IntrusionAlarm alarm = repository.saveAlarm(IntrusionAlarm.raise(UUID.randomUUID(), SITE, "PANEL-1",
                "VAULT-1", AlarmType.TAMPER, AlarmSeverity.CRITICAL, Instant.parse("2026-09-22T10:00:00Z"),
                Instant.parse("2026-09-22T10:02:00Z"), "system:test", atRaise.instant(),
                gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.SourceChannel.INTEGRATION, "corr-1"));

        Clock pastWindow = Clock.fixed(Instant.parse("2026-09-22T10:05:00Z"), ZoneOffset.UTC);
        IntrusionAlarmService service = new IntrusionAlarmService(repository, incidentSeeding, audit, events, access,
                pastWindow);

        service.escalateOverdueAlarms();

        IntrusionAlarm escalated = repository.findAlarm(alarm.id()).orElseThrow();
        assertThat(escalated.status()).isEqualTo(AlarmStatus.ESCALATED);
        assertThat(escalated.escalatedTo()).isEqualTo("NECC");
        assertThat(repository.findRaisedAlarms()).isEmpty();
    }

    @Test
    void an_acknowledged_alarm_can_be_escalated_into_a_seeded_incident() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-22T10:00:00Z"), ZoneOffset.UTC);
        IntrusionAlarm alarm = repository.saveAlarm(IntrusionAlarm.raise(UUID.randomUUID(), SITE, "PANEL-1",
                "VAULT-1", AlarmType.ZONE_ALARM, AlarmSeverity.HIGH, clock.instant(),
                clock.instant().plusSeconds(300), "system:test", clock.instant(),
                gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.SourceChannel.INTEGRATION, "corr-1"));
        IntrusionAlarmService service = new IntrusionAlarmService(repository, incidentSeeding, audit, events, access,
                clock);
        ActorContext socOperator = IntrusionTestDoubles.actor("soc-1", SflRole.SOC_OPERATOR, SITE);

        service.acknowledge(alarm.id(), socOperator);
        IntrusionAlarm linked = service.linkIncident(alarm.id(), socOperator);

        assertThat(linked.seededIncidentId()).isEqualTo(incidentSeeding.nextIncidentId);
        assertThat(incidentSeeding.descriptions).hasSize(1);
    }

    @Test
    void a_flapping_panel_is_coalesced_into_one_alarm_and_flagged_rather_than_flooding_the_queue() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-22T10:00:00Z"), ZoneOffset.UTC);
        IntrusionIngestionService ingestion = new IntrusionIngestionService(repository, null, audit, events, clock);
        ActorContext systemActor = IntrusionTestDoubles.actor("system:panel", SflRole.SFL_ADMIN, SITE);

        Instant t0 = Instant.parse("2026-09-22T10:00:00Z");
        for (int i = 0; i < 5; i++) {
            ingestion.raiseOrCoalesce(SITE, "PANEL-1", "ZONE-1", AlarmType.DEVICE_FAULT, t0.plusSeconds(i * 10L),
                    systemActor);
        }

        assertThat(repository.findAlarmsByStatus(SITE, AlarmStatus.COALESCED)).hasSize(1);
        IntrusionAlarm coalesced = repository.findAlarmsByStatus(SITE, AlarmStatus.COALESCED).get(0);
        assertThat(coalesced.signalCount()).isEqualTo(5);
        assertThat(repository.findAlarmsByStatus(SITE, AlarmStatus.RAISED)).isEmpty();
    }
}
