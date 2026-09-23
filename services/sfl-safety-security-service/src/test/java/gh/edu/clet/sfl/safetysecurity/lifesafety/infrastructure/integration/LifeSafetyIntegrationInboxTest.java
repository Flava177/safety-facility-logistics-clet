package gh.edu.clet.sfl.safetysecurity.lifesafety.infrastructure.integration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.exception.LifeSafetyErrorCode;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.exception.LifeSafetyException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/** SRS-SFL-S162a-01: authenticated, idempotent ingestion - forged, unauthenticated and duplicate messages are rejected. */
class LifeSafetyIntegrationInboxTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-15T09:00:00Z"), ZoneOffset.UTC);
    private static final String SECRET = "test-secret";

    private LifeSafetyIntegrationInbox inbox(JdbcTemplate jdbc) {
        return new LifeSafetyIntegrationInbox(jdbc, CLOCK, List.of("FIRE-PANEL-VENDOR"), SECRET);
    }

    private LifeSafetyIntegrationInbox.InboundMessage validMessage(String signature) {
        String rawPayload = "{\"kind\":\"FIRE\"}";
        return new LifeSafetyIntegrationInbox.InboundMessage("FIRE-PANEL-VENDOR", "idem-1", "lifesafety.event",
                "E2E-HQ", CLOCK.instant(), signature, rawPayload, Map.of("kind", "FIRE", "zoneCode", "ZONE-A"),
                List.of("kind", "zoneCode"), null);
    }

    @Test
    void rejects_a_message_from_a_source_not_on_the_allowlist() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var message = new LifeSafetyIntegrationInbox.InboundMessage("UNKNOWN-VENDOR", "idem-1", "lifesafety.event",
                "E2E-HQ", CLOCK.instant(), "sig", "{}", Map.of("kind", "FIRE", "zoneCode", "ZONE-A"),
                List.of("kind", "zoneCode"), null);

        assertThatThrownBy(() -> inbox(jdbc).accept(message)).isInstanceOf(LifeSafetyException.class)
                .satisfies(e -> org.assertj.core.api.Assertions
                        .assertThat(((LifeSafetyException) e).errorCode())
                        .isEqualTo(LifeSafetyErrorCode.LIFESAFETY_SOURCE_NOT_ALLOWED));
    }

    @Test
    void rejects_a_message_with_an_invalid_signature() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);

        assertThatThrownBy(() -> inbox(jdbc).accept(validMessage("not-the-real-hmac")))
                .isInstanceOf(LifeSafetyException.class)
                .satisfies(e -> org.assertj.core.api.Assertions
                        .assertThat(((LifeSafetyException) e).errorCode())
                        .isEqualTo(LifeSafetyErrorCode.LIFESAFETY_INVALID_SIGNATURE));
    }

    @Test
    void rejects_a_duplicate_idempotency_key() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(any(String.class), any(Class.class), any(), any())).thenReturn(1L);
        String signature = LifeSafetyIntegrationInbox.hmac(SECRET, CLOCK.instant() + "." + "{\"kind\":\"FIRE\"}");

        assertThatThrownBy(() -> inbox(jdbc).accept(validMessage(signature)))
                .isInstanceOf(LifeSafetyException.class)
                .satisfies(e -> org.assertj.core.api.Assertions
                        .assertThat(((LifeSafetyException) e).errorCode())
                        .isEqualTo(LifeSafetyErrorCode.LIFESAFETY_DUPLICATE_MESSAGE));
    }

    @Test
    void accepts_a_genuine_first_time_message() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(any(String.class), any(Class.class), any(), any())).thenReturn(0L);
        String signature = LifeSafetyIntegrationInbox.hmac(SECRET, CLOCK.instant() + "." + "{\"kind\":\"FIRE\"}");

        var id = inbox(jdbc).accept(validMessage(signature));

        org.assertj.core.api.Assertions.assertThat(id).isNotNull();
    }
}
