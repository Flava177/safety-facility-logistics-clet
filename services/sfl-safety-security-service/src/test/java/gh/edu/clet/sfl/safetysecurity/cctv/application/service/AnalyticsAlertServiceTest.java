package gh.edu.clet.sfl.safetysecurity.cctv.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.AlertSeverity;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.AlertStatus;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.AlertType;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.AnalyticsAlert;
import gh.edu.clet.sfl.safetysecurity.cctv.infrastructure.integration.CctvIntegrationInbox;
import gh.edu.clet.sfl.safetysecurity.cctv.support.CctvTestDoubles;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/** SRS-SFL-S161-04: a tamper alert is genuinely security-relevant and seeds a security incident; a
 * routine motion alert is forwarded to SIEM but does not seed one - see
 * {@code AnalyticsAlertService.seedsIncident}. Ingestion is idempotent per (source, externalEventId). */
class AnalyticsAlertServiceTest {

    private static final String SITE = "E2E-HQ";
    private static final String SOURCE = "CCTV-VMS-VENDOR";
    private final CctvTestDoubles.InMemoryCctvRepository repository = new CctvTestDoubles.InMemoryCctvRepository();
    private final CctvTestDoubles.FakeSiemForwarder siem = new CctvTestDoubles.FakeSiemForwarder();
    private final CctvTestDoubles.FakeCctvIncidentSeedingPort incidentSeeding =
            new CctvTestDoubles.FakeCctvIncidentSeedingPort();
    private final CctvTestDoubles.FakeAuditPort audit = new CctvTestDoubles.FakeAuditPort();
    private final CctvTestDoubles.FakeEventPublisher events = new CctvTestDoubles.FakeEventPublisher();
    private final CctvAccessPolicy access = new CctvAccessPolicy();
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-23T10:00:00Z"), ZoneOffset.UTC);
    private final InMemoryInboxJdbc jdbc = new InMemoryInboxJdbc();
    private final CctvIntegrationInbox inbox = new CctvIntegrationInbox(jdbc, clock, List.of(SOURCE),
            "test-shared-secret");
    private final AnalyticsAlertService service = new AnalyticsAlertService(repository, inbox, siem, incidentSeeding,
            audit, events, access, clock);
    private final ActorContext systemActor = CctvTestDoubles.actor("integration:vendor", SflRole.SFL_ADMIN, "*");

    @Test
    void a_tamper_alert_seeds_an_incident_and_forwards_to_siem() {
        AnalyticsAlert raised = ingest("evt-1", AlertType.TAMPER);

        assertThat(raised.seededIncidentId()).isEqualTo(incidentSeeding.nextIncidentId);
        assertThat(siem.forwarded).hasSize(1);
    }

    @Test
    void a_motion_alert_forwards_to_siem_but_does_not_seed_an_incident() {
        AnalyticsAlert raised = ingest("evt-2", AlertType.MOTION);

        assertThat(raised.seededIncidentId()).isNull();
        assertThat(incidentSeeding.descriptions).isEmpty();
        assertThat(siem.forwarded).hasSize(1);
    }

    @Test
    void the_soc_can_acknowledge_and_then_resolve_an_open_alert() {
        AnalyticsAlert raised = ingest("evt-3", AlertType.LINE_CROSSING);
        ActorContext socOperator = CctvTestDoubles.actor("soc-1", SflRole.SOC_OPERATOR, SITE);

        AnalyticsAlert acknowledged = service.acknowledge(raised.id(), socOperator);
        assertThat(acknowledged.status()).isEqualTo(AlertStatus.ACKNOWLEDGED);

        AnalyticsAlert resolved = service.resolve(raised.id(), socOperator);
        assertThat(resolved.status()).isEqualTo(AlertStatus.RESOLVED);
    }

    @Test
    void a_duplicate_external_event_id_is_rejected_as_already_processed() {
        ingest("evt-dup", AlertType.MOTION);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> ingest("evt-dup", AlertType.MOTION))
                .isInstanceOf(gh.edu.clet.sfl.safetysecurity.cctv.domain.exception.CctvException.class);
    }

    private AnalyticsAlert ingest(String externalEventId, AlertType type) {
        return service.ingest(new AnalyticsAlertService.IngestAlert(SOURCE, externalEventId, clock.instant(),
                CctvIntegrationInbox.hmac("test-shared-secret", clock.instant() + "." + "{}"), "{}",
                Map.of("cameraId", "CAM-01", "type", type.name()), SITE, "CAM-01", type, AlertSeverity.HIGH,
                clock.instant(), systemActor));
    }

    /** A minimal in-memory stand-in for the inbox's two JDBC calls - counting duplicates and no-op
     * inserts - so this test needs no database. */
    private static final class InMemoryInboxJdbc extends JdbcTemplate {
        private final java.util.Set<String> seen = new java.util.HashSet<>();

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            String key = args[0] + "|" + args[1];
            @SuppressWarnings("unchecked")
            T result = (T) Long.valueOf(seen.contains(key) ? 1 : 0);
            return result;
        }

        @Override
        public int update(String sql, Object... args) {
            String key = args[1] + "|" + args[2];
            seen.add(key);
            return 1;
        }
    }
}
