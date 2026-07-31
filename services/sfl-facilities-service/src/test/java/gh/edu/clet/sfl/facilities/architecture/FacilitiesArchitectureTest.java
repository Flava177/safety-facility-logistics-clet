package gh.edu.clet.sfl.facilities.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The dependency rule, enforced (solution.md, NFR 23.8).
 *
 * <p>"Architecture tests must enforce module boundaries, contracts-only references, no cross-schema
 * foreign keys and no provider names outside adapters." These are the ones a compiler cannot check.
 */
class FacilitiesArchitectureTest {

    private static final String ROOT = "gh.edu.clet.sfl.facilities";

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT);
    }

    @Test
    void the_domain_layer_imports_no_framework() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..",
                        "jakarta.servlet..",
                        "jakarta.validation..",
                        "com.fasterxml.jackson..",
                        "tools.jackson..",
                        "io.swagger..")
                .because("the domain must be exercisable without a container, a database or an HTTP stack");

        rule.check(classes);
    }

    /**
     * Nothing points into infrastructure.
     *
     * <p>{@code maintenance} used to be excluded here, as a recorded debt: its services injected their
     * Spring Data repositories directly, so S153's application layer named its own JPA types. The S153
     * build introduced {@code MaintenanceRepository} and the exclusion went with it. The rule now holds
     * across every module, which is the point at which it starts being a rule.
     */
    @Test
    void nothing_points_into_infrastructure() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("..api..", "..application..", "..domain..")
                .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
                .because("adapters implement ports; the inside of a module never names one");

        rule.check(classes);
    }

    /**
     * Readiness knows nothing about maintenance.
     *
     * <p>The direction matters and it is not arbitrary. Whether a hall can be used is a fact about the
     * estate, true whether or not anybody has raised a work order about it; maintenance is one of
     * several things that can change that fact, alongside assessments and asset failures. So
     * maintenance depends on readiness, through {@code ExternalBlockerPort} — which readiness declares
     * itself, precisely so implementing it does not drag a fault or a work order back across the line.
     *
     * <p>This is the rule that would break first if somebody added a "which work order is fixing this
     * blocker?" field to readiness. It is a reasonable thing to want and it belongs on the maintenance
     * side, looked up by {@code sourceReference}.
     */
    @Test
    void readiness_does_not_depend_on_maintenance() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(ROOT + ".readiness..")
                .should().dependOnClassesThat().resideInAPackage(ROOT + ".maintenance..")
                .because("readiness is the deeper module: maintenance changes readiness, never the reverse");

        rule.check(classes);
    }

    @Test
    void the_domain_does_not_depend_on_the_application_or_api_layers() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage("..application..", "..api..")
                .because("the dependency rule runs api -> application -> domain, never back");

        rule.check(classes);
    }

    @Test
    void controllers_do_not_reach_into_persistence() {
        ArchRule rule = noClasses()
                .that().haveSimpleNameEndingWith("Controller")
                .should().dependOnClassesThat().resideInAPackage("..persistence..")
                .because("a controller that can reach a repository will eventually put a rule beside it");

        rule.check(classes);
    }

    @Test
    void the_application_layer_does_not_import_spring_data_or_web_types() {
        // Spring's stereotype and transaction annotations are allowed — they are declarative and
        // provider-swappable. Pageable, Page and the servlet API are not: they would put an
        // infrastructure shape into a use case's signature.
        ArchRule rule = noClasses()
                .that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.data.domain..",
                        "org.springframework.data.jpa..",
                        "org.springframework.web..",
                        "jakarta.servlet..")
                .because("a use case states what it needs; how it is paged or transported is an adapter's job");

        rule.check(classes);
    }

    @Test
    void readiness_does_not_reach_into_another_modules_persistence() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(ROOT + ".readiness..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        ROOT + ".masterdata.infrastructure..",
                        ROOT + ".maintenance.infrastructure..",
                        ROOT + ".dashboard.infrastructure..")
                .because("modules reference each other's contracts, never each other's tables");

        rule.check(classes);
    }

    /**
     * The estate's domain and application layers do not depend on readiness or the dashboard.
     *
     * <p>{@code masterdata} is the host module: its consumers depend on it, not the reverse. It reaches
     * readiness only through {@code SpaceReadinessPort}, which it declares itself — the inversion that
     * keeps the compile-time arrow pointing one way.
     *
     * <p>The rule stops at the application boundary on purpose. A controller is the composition root
     * for one request, and {@code FacilitiesMasterDataController} legitimately routes
     * {@code PATCH /rooms/{id}/readiness} to the readiness service so the critical-blocker rule applies
     * to a manual status exactly as it does to a derived one. Forbidding that would only move the
     * endpoint to a worse URL.
     */
    @Test
    void the_estate_domain_and_application_do_not_depend_on_readiness_or_the_dashboard() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage(ROOT + ".masterdata.domain..", ROOT + ".masterdata.application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        ROOT + ".readiness..",
                        ROOT + ".dashboard..")
                .because("S152's estate is the platform; its consumers depend on it, not the reverse");

        rule.check(classes);
    }

    @Test
    void jpa_entities_live_only_in_persistence_packages() {
        ArchRule rule = classes()
                .that().areAnnotatedWith(jakarta.persistence.Entity.class)
                .should().resideInAPackage("..infrastructure.persistence..")
                .because("an entity outside an adapter is a database shape leaking into the domain");

        rule.check(classes);
    }

    @Test
    void no_provider_name_appears_outside_an_adapter() {
        ArchRule rule = noClasses()
                .that().resideOutsideOfPackages("..infrastructure..", "..config..")
                .should().haveSimpleNameContaining("Keycloak")
                .orShould().haveSimpleNameContaining("Postgres")
                .orShould().haveSimpleNameContaining("RabbitMq")
                .because("a product name in a use case is a rewrite waiting to happen");

        rule.check(classes);
    }
}
