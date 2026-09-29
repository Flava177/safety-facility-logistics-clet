package gh.edu.clet.sfl.facilities.energy;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * S157-specific invariants that {@code FacilitiesArchitectureTest}'s generic layering rule does not name.
 *
 * <p>The one this module cares about beyond the generic rule is SRS-SFL-S157-04 as a build-time constraint,
 * not just a runtime one: "a domain module must never depend directly on a vendor API" (SRS 2.6), and here
 * specifically, never directly on the S156 module either. Only the port and its one adapter may know S156
 * exists.
 */
class EnergyArchitectureTest {

    private static final String ROOT = "gh.edu.clet.sfl.facilities.energy";

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT, "gh.edu.clet.sfl.facilities.buildingsystems");
    }

    @Test
    void only_the_integration_adapters_know_buildingsystems_exists() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(ROOT + "..")
                .and().resideOutsideOfPackages(ROOT + ".infrastructure.integration..")
                .should().dependOnClassesThat().resideInAPackage("gh.edu.clet.sfl.facilities.buildingsystems..")
                .because("S157 consumes S156 only through its own EnergyDeviceDirectoryPort - the S156 "
                        + "application.contract types are named nowhere outside infrastructure.integration "
                        + "(SRS-SFL-S157-04)");

        rule.check(classes);
    }

    @Test
    void the_domain_layer_imports_no_framework() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(ROOT + ".domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..", "jakarta.persistence..", "jakarta.servlet..",
                        "jakarta.validation..", "com.fasterxml.jackson..", "tools.jackson..", "io.swagger..")
                .because("the domain must be exercisable without a container, a database or an HTTP stack");

        rule.check(classes);
    }

    @Test
    void only_infrastructure_persistence_uses_jpa_entities() {
        ArchRule rule = classes().that().areAnnotatedWith(jakarta.persistence.Entity.class)
                .and().resideInAPackage(ROOT + "..")
                .should().resideInAPackage(ROOT + ".infrastructure.persistence..");

        rule.check(classes);
    }

    @Test
    void nothing_outside_infrastructure_imports_it() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage(ROOT + ".api..", ROOT + ".application..", ROOT + ".domain..")
                .should().dependOnClassesThat().resideInAPackage(ROOT + ".infrastructure..");

        rule.check(classes);
    }

    @Test
    void application_never_imports_spring_data_or_web_types() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(ROOT + ".application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.data..", "org.springframework.web..", "jakarta.servlet..")
                .because("application services depend on their own ports, not on persistence or web mechanics");

        rule.check(classes);
    }

    @Test
    void the_module_exists_and_was_actually_imported() {
        assertThat(classes.contain("gh.edu.clet.sfl.facilities.energy.domain.EnergyMeter")).isTrue();
    }
}
