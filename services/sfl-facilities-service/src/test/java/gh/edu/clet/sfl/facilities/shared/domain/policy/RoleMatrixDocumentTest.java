package gh.edu.clet.sfl.facilities.shared.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.common.security.SflRole;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * The IFIMP section of {@code docs/ROLE_MATRIX_DRAFT.md} is generated from this matrix, and this
 * test is what makes that true rather than aspirational.
 *
 * <h2>Why a golden file and not a script</h2>
 *
 * <p>The first version of that document was produced by a regular expression over the matrix
 * sources, and it was wrong in three separate ways at once: it missed the roles built with a stream
 * filter and reported them as read-only, it missed the last constant of an enum because it required
 * a trailing comma, and it silently omitted {@code DTI_ADMIN}, {@code INTEGRATION_ENGINEER} and
 * {@code SERVICE_INTEGRATION} entirely. Every one of those failures reads the same way to somebody
 * checking the document: a role that can do less than it can. A document that understates
 * permissions is worse than no document, because it is the one people quote.
 *
 * <p>So the generator is the matrix itself. {@link FacilitiesPermissionMatrix#permissionsFor(SflRole)}
 * is the same call {@code FacilitiesAuthorization} makes on every request, which means the document
 * cannot describe a permission the service does not grant, and the build fails the moment it tries.
 *
 * <h2>Why one test per module rather than one test for the matrix</h2>
 *
 * <p>No module can see all three platforms. Facilities, emergency and fleet each own their own
 * matrix on purpose - a shared one would put three services' business rules in the shared library -
 * so a single test that rendered the whole document would need exactly the dependency the split
 * exists to prevent. Three tests, three delimited sections, each owned by the module that can
 * actually answer for it. The document names the owner of each section.
 *
 * <h2>Regenerating</h2>
 *
 * <pre>
 * mvn -f services/pom.xml test -Dtest=RoleMatrixDocumentTest -Dsfl.roleMatrix.write=true
 * </pre>
 *
 * <p>That rewrites the section in place and passes. Then read the diff: if it is not the change you
 * meant to make, the matrix is wrong and the document was right to complain.
 */
class RoleMatrixDocumentTest {

    /** The section this module owns. One module, one platform, one delimited block. */
    private static final String SECTION = "IFIMP";

    private static final String DOC = "docs/ROLE_MATRIX_DRAFT.md";
    private static final String BEGIN = "<!-- BEGIN GENERATED: " + SECTION + " -->";
    private static final String END = "<!-- END GENERATED: " + SECTION + " -->";
    private static final String REGENERATE =
            "mvn -f services/pom.xml -pl sfl-facilities-service test"
                    + " -Dtest=RoleMatrixDocumentTest -Dsfl.roleMatrix.write=true";

    @Test
    void the_document_says_exactly_what_the_matrix_says() throws IOException {
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
                .as("The %s section of %s no longer matches %s.%nRegenerate it with:%n  %s%nThen read"
                                + " the diff - if it is not the change you meant, the matrix is wrong.",
                        SECTION, DOC, FacilitiesPermissionMatrix.class.getSimpleName(), REGENERATE)
                .isEqualTo(expected);
    }

    // ---------------------------------------------------------------- what this platform grants

    private static Set<SflPermission> heldBy(SflRole role) {
        return FacilitiesPermissionMatrix.permissionsFor(role);
    }

    private static String render() {
        StringBuilder out = new StringBuilder();
        out.append("## IFIMP - Facilities & Infrastructure\n");
        out.append("\n");
        out.append("Served by `sfl-facilities-service` on port 8091, and by the portal on 8090.\n");
        out.append("Generated from `FacilitiesPermissionMatrix.permissionsFor(role)`.\n");
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

    /**
     * Walks up from the module directory. Surefire runs with the module as the working directory and
     * the document is at the repository root, and hard-coding {@code ../../docs} would break the day
     * somebody runs the test from anywhere else.
     */
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

    /** System property for the command line, environment variable for anyone driving it from a script. */
    private static boolean writeRequested() {
        return Boolean.getBoolean("sfl.roleMatrix.write")
                || "true".equalsIgnoreCase(System.getenv("SFL_ROLE_MATRIX_WRITE"));
    }
}
