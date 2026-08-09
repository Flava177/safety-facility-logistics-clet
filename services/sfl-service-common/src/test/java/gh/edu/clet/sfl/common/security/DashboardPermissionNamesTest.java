package gh.edu.clet.sfl.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every permission the dashboard gates a screen on is one this enum actually declares.
 *
 * <h2>The gap this closes</h2>
 *
 * <p>{@code permissions.ts} declares a TypeScript union transcribed by hand from this enum, and the
 * front end's compiler checks names against that union - so a typo in {@code navigation.ts} is
 * caught. What no compiler checks is the transcription itself. Rename a constant here, or delete
 * one, and the union stays valid TypeScript while naming something no service knows: the gate then
 * hides a control permanently, or offers one the endpoint refuses, and neither is an error anywhere.
 *
 * <p>Capability gating made that failure mode much more likely. The sidebar now names roughly forty
 * verbs rather than a dozen reads, and each is a chance for the two files to drift.
 *
 * <p>Reading the file is deliberately literal. Generating the union, or sharing a package between a
 * Maven module and a Vite bundle, is a great deal of machinery to prevent a class of mistake that
 * twenty lines catch here.
 */
class DashboardPermissionNamesTest {

    /** From {@code services/sfl-service-common} up to the repository, then into the dashboard. */
    private static final Path UNION_FILE =
            Path.of("..", "..", "frontend", "sfl-operations-ui", "src", "shared", "layout", "permissions.ts");

    private static final Pattern UNION_MEMBER = Pattern.compile("\\|\\s*'([A-Z][A-Z0-9_]+)'");

    /*
      Worth recording, because it nearly produced a false alarm. An earlier draft of this test read
      the enum with a regex requiring a trailing comma, which silently skipped the last constant -
      EMERGENCY_INTEGRATION_REPLAY - and reported it as a name the services did not know. It is
      declared and it is granted. Reading the enum through values() removes the whole class of
      mistake: only the dashboard, which is genuinely a text file to this module, is parsed.
    */
    @Test
    @DisplayName("no permission named in the dashboard union is unknown to SflPermission")
    void dashboard_union_matches_the_enum() throws IOException {
        // The front end is not required to be present for the services to build - a container image
        // that carries only the API has no reason to fail here.
        assumeTrue(Files.exists(UNION_FILE), "dashboard sources are not present in this checkout");

        String source = Files.readString(UNION_FILE, StandardCharsets.UTF_8);
        Matcher matcher = UNION_MEMBER.matcher(source);
        Set<String> declaredInDashboard = matcher.results()
                .map(result -> result.group(1))
                .collect(Collectors.toSet());

        assertThat(declaredInDashboard)
                .as("the union should have been parsed; a shape change here silently disables this test")
                .hasSizeGreaterThan(50);

        Set<String> declaredInServices = Arrays.stream(SflPermission.values())
                .map(Enum::name)
                .collect(Collectors.toSet());

        assertThat(declaredInServices)
                .as("named by the dashboard but absent from SflPermission - the gate would hide a "
                        + "control for everybody, or offer one every endpoint refuses")
                .containsAll(declaredInDashboard);
    }
}
