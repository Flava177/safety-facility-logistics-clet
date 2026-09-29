package gh.edu.clet.sfl.facilities.eventlogistics;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * S173-specific architectural invariants, on top of the generic layering
 * {@code FacilitiesArchitectureTest} already enforces across every module.
 */
class EventLogisticsArchitectureTest {

    private static final String ROOT = "gh.edu.clet.sfl.facilities";
    private static final String MODULE = ROOT + ".eventlogistics";

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT);
    }

    /**
     * S173-02: "Consume it through your own port + adapter ... never on the provider's internals."
     * Only {@code eventlogistics.infrastructure.integration} may import S159, S153 or S169's contract -
     * everywhere else in the module talks to the ports in {@code eventlogistics.application.ports}. This
     * is what keeps the module compiling and testable while S169 is built in a different worktree, and
     * what stops a routing decision from quietly depending on a sibling module's persistence shape.
     */
    @Test
    void only_the_integration_adapters_depend_on_sibling_modules() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(MODULE + "..")
                .and().resideOutsideOfPackage(MODULE + ".infrastructure.integration..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        ROOT + ".booking..",
                        ROOT + ".maintenance..",
                        ROOT + ".cleaning..")
                .because("S173 is a consumer of S159, S153 and S169; every dependency on one of them "
                        + "goes through eventlogistics's own port and exactly one adapter in "
                        + "infrastructure.integration, per the Phase 2 build brief");
        rule.check(classes);
    }

    /** Domain stays framework-free even for this module's own package (belt-and-braces on the generic rule). */
    @Test
    void the_domain_layer_imports_no_framework() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(MODULE + ".domain..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta.persistence..",
                        "jakarta.servlet..", "com.fasterxml.jackson..")
                .because("the domain must be exercisable without a container, a database or an HTTP stack");
        rule.check(classes);
    }
}
