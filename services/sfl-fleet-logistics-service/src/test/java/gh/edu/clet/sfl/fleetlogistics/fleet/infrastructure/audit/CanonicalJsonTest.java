package gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The audit hash chain is only as trustworthy as the determinism of the value it hashes. */
class CanonicalJsonTest {

    @Test
    @DisplayName("object keys are sorted, so insertion order cannot change the hash")
    void object_keys_are_sorted() {
        Map<String, Object> insertionOrdered = new LinkedHashMap<>();
        insertionOrdered.put("registrationNumber", "GT-1234-26");
        insertionOrdered.put("siteCode", "ACCRA");
        insertionOrdered.put("lifecycleStatus", "ACTIVE");

        Map<String, Object> differentOrder = new TreeMap<>(Map.of(
                "siteCode", "ACCRA",
                "lifecycleStatus", "ACTIVE",
                "registrationNumber", "GT-1234-26"));

        assertThat(CanonicalJson.write(insertionOrdered)).isEqualTo(CanonicalJson.write(differentOrder));
        assertThat(CanonicalJson.write(insertionOrdered))
                .isEqualTo("{\"lifecycleStatus\":\"ACTIVE\",\"registrationNumber\":\"GT-1234-26\","
                        + "\"siteCode\":\"ACCRA\"}");
    }

    @Test
    @DisplayName("array order is preserved, because order is meaningful in a list of blockers")
    void array_order_is_preserved() {
        assertThat(CanonicalJson.write(List.of("SERVICE_OVERDUE", "COMPLIANCE_DOCUMENT_EXPIRED")))
                .isEqualTo("[\"SERVICE_OVERDUE\",\"COMPLIANCE_DOCUMENT_EXPIRED\"]");
    }

    @Test
    @DisplayName("temporals, UUIDs and enums render through toString")
    void scalars_render_predictably() {
        UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
        Map<String, Object> value = Map.of(
                "id", id,
                "occurredAt", Instant.parse("2026-07-21T08:00:00Z"),
                "channel", gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel.MOBILE);

        String json = CanonicalJson.write(value);

        assertThat(json).contains("\"id\":\"11111111-1111-1111-1111-111111111111\"");
        assertThat(json).contains("\"occurredAt\":\"2026-07-21T08:00:00Z\"");
        assertThat(json).contains("\"channel\":\"MOBILE\"");
    }

    @Test
    @DisplayName("decimal formatting is stable regardless of trailing zeros")
    void decimals_are_normalised() {
        assertThat(CanonicalJson.write(Map.of("odometer", new java.math.BigDecimal("120.500"))))
                .isEqualTo("{\"odometer\":\"120.5\"}".replace("\"120.5\"", "120.5"));
    }

    @Test
    @DisplayName("control characters and quotes are escaped")
    void strings_are_escaped() {
        assertThat(CanonicalJson.write(Map.of("reason", "line1\nline2 \"quoted\"")))
                .isEqualTo("{\"reason\":\"line1\\nline2 \\\"quoted\\\"\"}");
    }

    @Test
    @DisplayName("null renders as null rather than throwing")
    void null_is_supported() {
        assertThat(CanonicalJson.write(null)).isNull();
        assertThat(CanonicalJson.write(java.util.Collections.singletonMap("closedAt", null)))
                .isEqualTo("{\"closedAt\":null}");
    }
}
