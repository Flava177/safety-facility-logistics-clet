package gh.edu.clet.sfl.fleetlogistics.fleet.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.fleetlogistics.assets.domain.policy.AssetVisibilityPermissionMatrix;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.policy.DispatchPermissionMatrix;
import gh.edu.clet.sfl.fleetlogistics.fuel.domain.policy.FuelPermissionMatrix;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * The FTLMP section of {@code docs/ROLE_MATRIX_DRAFT.md} is generated from this module's four
 * matrices, and this test is what makes that true rather than aspirational.
 *
 * <p><strong>Four matrices, one deployable, one answer</strong> - and this time all four are asked.
 * {@code AssetVisibilityPermissionMatrix} was once missing from the union that answers
 * {@code /actor/permissions}, and because the dashboard treats a non-null answer as complete, its
 * two permissions were not left unknown, they were <em>denied</em> - silently, to every actor who
 * held them. Rendering the document from the same four in the same order means that particular
 * omission now shows up as a diff in a text file rather than as a screen nobody can open.
 *
 * <p>See the facilities module's copy of this test for why the document is generated rather than
 * written, and why there are three of these rather than one.
 *
 * <p>Regenerate with:
 *
 * <pre>
 * mvn -f services/pom.xml test -Dtest=RoleMatrixDocumentTest -Dsfl.roleMatrix.write=true
 * </pre>
 */
class RoleMatrixDocumentTest {

    /** The section this module owns. One module, one platform, one delimited block. */
    private static final String SECTION = "FTLMP";

    private static final String DOC = "docs/ROLE_MATRIX_DRAFT.md";
    private static final String BEGIN = "<!-- BEGIN GENERATED: " + SECTION + " -->";
    private static final String END = "<!-- END GENERATED: " + SECTION + " -->";
    private static final String REGENERATE =
            "mvn -f services/pom.xml -pl sfl-fleet-logistics-service test"
                    + " -Dtest=RoleMatrixDocumentTest -Dsfl.roleMatrix.write=true";

    @Test
    void the_document_says_exactly_what_the_matrices_say() throws IOException {
        Path doc = document();
        String raw = Files.readString(doc, StandardCharsets.UTF_8);
        boolean crlf = raw.contains("\r\n");
        String file = raw.replace("\r\n", "\n");

        int begin = file.indexOf(BEGIN);
        int end = file.indexOf(END);
        assertThat(begin).as("%s has lost its %s opening marker, %s", DOC, SECTION, BEGIN).isNotNegative();
        assertThat(end).as("%s has lost its %s closing marker, %s", DOC, SECTION, END).isGreaterThan(begin);

        String expected = "\n" + render() + "\n";
        String actual = file.substring(begin + BEGIN.length(), end);

        if (!expected.equals(actual) && writeRequested()) {
            String rewritten = file.substring(0, begin + BEGIN.length()) + expected + file.substring(end);
            Files.writeString(doc, crlf ? rewritten.replace("\n", "\r\n") : rewritten, StandardCharsets.UTF_8);
            return;
        }

        assertThat(actual)
                .as("The %s section of %s no longer matches the four FTLMP matrices.%nRegenerate it"
                                + " with:%n  %s%nThen read the diff - if it is not the change you meant,"
                                + " a matrix is wrong.",
                        SECTION, DOC, REGENERATE)
                .isEqualTo(expected);
    }

    // ---------------------------------------------------------------- what this platform grants

    /** The same union, in the same order, as {@code ActorPermissionsController}. */
    private static Set<SflPermission> heldBy(SflRole role) {
        Set<SflRole> roles = Set.of(role);
        EnumSet<SflPermission> held = EnumSet.noneOf(SflPermission.class);
        held.addAll(FleetPermissionMatrix.permissionsFor(roles));
        held.addAll(AssetVisibilityPermissionMatrix.permissionsFor(roles));
        for (SflPermission permission : SflPermission.values()) {
            if (FuelPermissionMatrix.grants(roles, permission)
                    || DispatchPermissionMatrix.grants(roles, permission)) {
                held.add(permission);
            }
        }
        return held;
    }

    private static String render() {
        StringBuilder out = new StringBuilder();
        out.append("## FTLMP - Fleet, Transport & Logistics\n");
        out.append("\n");
        out.append("Served by `sfl-fleet-logistics-service` on port 8093, and by the portal on 8090.\n");
        out.append("Generated from the union of `FleetPermissionMatrix`, `AssetVisibilityPermissionMatrix`,\n");
        out.append("`FuelPermissionMatrix` and `DispatchPermissionMatrix` - the same four\n");
        out.append("`ActorPermissionsController` unions, in the same order.\n");
        List<SflRole> nothing = new ArrayList<>();
        for (SflRole role : rolesByName()) {
            Set<SflPermission> held = heldBy(role);
            if (held.isEmpty()) {
                nothing.add(role);
                continue;
            }
            out.append("\n").append(block(role, held));
        }
        out.append("\nHolding nothing on this platform: ").append(roleNames(nothing)).append("\n");
        return out.toString();
    }

    // ---------------------------------------------------------------- rendering, shared by shape

    private static List<SflRole> rolesByName() {
        return Arrays.stream(SflRole.values())
                .sorted(Comparator.comparing(Enum::name))
                .collect(Collectors.toList());
    }

    private static String block(SflRole role, Set<SflPermission> granted) {
        List<SflPermission> canDo = granted.stream()
                .filter(permission -> !permission.name().endsWith("_READ"))
                .sorted(Comparator.comparing(Enum::name))
                .collect(Collectors.toList());
        List<SflPermission> canSee = granted.stream()
                .filter(permission -> permission.name().endsWith("_READ"))
                .sorted(Comparator.comparing(Enum::name))
                .collect(Collectors.toList());
        return "### `" + role.name() + "`\n"
                + "\n"
                + "Can do (" + canDo.size() + "): "
                + (canDo.isEmpty() ? "nothing - read only on this platform" : permissionNames(canDo)) + "\n"
                + "\n"
                + "Can see (" + canSee.size() + "): "
                + (canSee.isEmpty() ? "nothing" : permissionNames(canSee)) + "\n";
    }

    private static String permissionNames(List<SflPermission> values) {
        return values.stream().map(value -> "`" + value.name() + "`").collect(Collectors.joining(", "));
    }

    private static String roleNames(List<SflRole> values) {
        return values.isEmpty()
                ? "none - every role holds something here"
                : values.stream().map(value -> "`" + value.name() + "`").collect(Collectors.joining(", "));
    }

    // ---------------------------------------------------------------- finding the document

    private static Path document() {
        Path from = Path.of("").toAbsolutePath();
        for (Path at = from; at != null; at = at.getParent()) {
            Path candidate = at.resolve(DOC);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not find " + DOC + " in or above " + from);
    }

    private static boolean writeRequested() {
        return Boolean.getBoolean("sfl.roleMatrix.write")
                || "true".equalsIgnoreCase(System.getenv("SFL_ROLE_MATRIX_WRITE"));
    }
}
