package gh.edu.clet.sfl.fleetlogistics.fleet.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.UnsafeUploadException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Two ceilings, and the reason they are not one.
 *
 * <p>They were briefly the same number. A photograph of a pump display and a fuel provider's monthly
 * ledger are different things arriving through the same control, and sizing both for the photograph
 * would have refused a legitimate import for being what it is. Sizing both for the ledger would give
 * every evidence upload four times the room it needs.
 *
 * <p>The assertions are about the *relationship* as much as the values: the import ceiling must
 * exceed the evidence one, and the container's multipart limit in `application.yml` must exceed
 * both - it refuses before any handler runs, so anything it catches is a failure with no error code
 * and nothing an operator can act on.
 */
class UploadLimitsTest {

    @Test
    @DisplayName("a bulk import is allowed more room than a photograph")
    void the_two_ceilings_are_different_and_ordered() {
        assertThat(UploadedFileScanner.MAX_BYTES).isEqualTo(5L * 1024 * 1024);
        assertThat(BulkImportPolicy.MAX_BYTES).isEqualTo(20L * 1024 * 1024);
        assertThat(BulkImportPolicy.MAX_BYTES).isGreaterThan(UploadedFileScanner.MAX_BYTES);
    }

    @Test
    @DisplayName("a CSV over the evidence cap but under the import cap is accepted")
    void an_ordinary_provider_file_is_not_refused() {
        // The exact case the shared limit would have broken: bigger than any receipt, smaller than a
        // month of transactions.
        byte[] tenMegabytes = new byte[10 * 1024 * 1024];

        assertThatCode(() -> BulkImportPolicy.requireWithinLimit("provider-july.csv", tenMegabytes))
                .doesNotThrowAnyException();

        // The same bytes as evidence are still refused - the evidence cap did not move.
        assertThatThrownBy(() -> UploadedFileScanner.scan("receipt.jpg", "image/jpeg", tenMegabytes))
                .isInstanceOf(UnsafeUploadException.class);
    }

    @Test
    @DisplayName("an import past its own ceiling is refused with the reason, not by the container")
    void an_oversized_import_is_refused_here() {
        byte[] tooBig = new byte[(int) BulkImportPolicy.MAX_BYTES + 1];

        assertThatThrownBy(() -> BulkImportPolicy.requireWithinLimit("huge.csv", tooBig))
                .isInstanceOf(UnsafeUploadException.class)
                .hasMessageContaining("20 MB");
    }

    @Test
    @DisplayName("an empty import is refused, which nothing checked before")
    void an_empty_import_is_refused() {
        assertThatThrownBy(() -> BulkImportPolicy.requireWithinLimit("empty.csv", new byte[0]))
                .isInstanceOf(UnsafeUploadException.class);
        assertThatThrownBy(() -> BulkImportPolicy.requireWithinLimit("missing.csv", null))
                .isInstanceOf(UnsafeUploadException.class);
    }

    @Test
    @DisplayName("the container's multipart ceiling clears the larger of the two")
    void the_configured_multipart_limit_is_above_both() throws Exception {
        // Read from the file rather than restated here: a test that hardcodes 21MB passes happily
        // while the config says 6MB, which is the mistake this exists to catch.
        String yaml;
        try (var in = UploadLimitsTest.class.getResourceAsStream("/application.yml")) {
            yaml = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        var matcher = java.util.regex.Pattern.compile("max-file-size:\\s*(\\d+)MB").matcher(yaml);
        assertThat(matcher.find()).as("max-file-size is configured").isTrue();

        long configuredMb = Long.parseLong(matcher.group(1));
        assertThat(configuredMb)
                .as("the container must not refuse a legitimate import before the handler sees it")
                .isGreaterThan(BulkImportPolicy.MAX_BYTES_MB);
    }
}
