package gh.edu.clet.sfl.facilities.spaceplanning;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * S158-specific invariants beyond {@code FacilitiesArchitectureTest}'s generic layering rule.
 *
 * <p>The one that matters most is the SRS-SFL-S158-03 validation rule made mechanical: "S158 never writes
 * back into S159's booking records." A read-only contract is a promise nobody re-checks by eye once the
 * module has grown past one file, so this asserts it directly: nothing under {@code spaceplanning}
 * depends on any {@code booking} class - the one exception is
 * {@code infrastructure.integration.S159BookingUtilisationAdapter}, whose sole job is naming
 * {@code BookingUtilisationReader} and its {@code RoomUtilisation} record.
 */
class SpacePlanningArchitectureTest {

    private static final String ROOT = "gh.edu.clet.sfl.facilities";

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT);
    }

    @Test
    void spaceplanning_depends_on_no_booking_class_except_the_utilisation_reader() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(ROOT + ".spaceplanning..")
                .and().doNotHaveFullyQualifiedName(ROOT + ".spaceplanning.infrastructure.integration.S159BookingUtilisationAdapter")
                .should().dependOnClassesThat().resideInAPackage(ROOT + ".booking..")
                .because("SRS-SFL-S158-03: S158 never writes back into S159's booking records, and reads only "
                        + "through BookingUtilisationReader");

        rule.check(classes);
    }

    @Test
    void spaceplanning_depends_on_no_construction_class_except_the_contract_it_consumes() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(ROOT + ".spaceplanning..")
                .and().doNotHaveFullyQualifiedName(ROOT + ".spaceplanning.infrastructure.integration.S176ConstructionHandoffAdapter")
                .should().dependOnClassesThat().resideInAPackage(ROOT + ".construction..")
                .because("modules meet through the provider's published contract, never its internals (ADR 0009)");

        rule.check(classes);
    }

    @Test
    void spaceplanning_domain_imports_no_infrastructure_or_application() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(ROOT + ".spaceplanning.domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        ROOT + ".spaceplanning.infrastructure..",
                        ROOT + ".spaceplanning.application..",
                        ROOT + ".spaceplanning.api..")
                .because("the dependency rule runs api -> application -> domain, never back");

        rule.check(classes);
    }

    @Test
    void spaceplanning_never_reaches_masterdata_persistence_directly() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(ROOT + ".spaceplanning..")
                .should().dependOnClassesThat().resideInAPackage(ROOT + ".masterdata.infrastructure..")
                .because("S158 reaches S152 only through FacilitiesRepository/SpaceAllocationService, "
                        + "never its JPA adapters");

        rule.check(classes);
    }
}
