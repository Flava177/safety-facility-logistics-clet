package gh.edu.clet.sfl.facilities.cleaning.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * S169-specific architectural invariants that the generic {@code FacilitiesArchitectureTest} does not
 * state, because they are this module's rules rather than every module's.
 */
class CleaningArchitectureTest {

    private static final String ROOT = "gh.edu.clet.sfl.facilities";

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT);
    }

    /**
     * The rule the build brief asks for by name: booking declares {@code BookingLifecycleObserver} and
     * never learns that a consumer of it exists. Reversing the dependency - cleaning calling back into
     * booking's internals rather than booking calling out through its own port - would make S159
     * un-buildable, un-testable and un-deployable without S169, which is exactly the coupling the
     * observer pattern exists to prevent.
     */
    @Test
    void booking_does_not_depend_on_cleaning() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(ROOT + ".booking..")
                .should().dependOnClassesThat().resideInAPackage(ROOT + ".cleaning..")
                .because("booking declares BookingLifecycleObserver for cleaning to implement; it must never "
                        + "import cleaning's package to know an implementation exists");

        rule.check(classes);
    }

    /**
     * Only two classes in this module are allowed to import booking's package: the adapter that resolves
     * a claimed booking reference ({@code S159BookingDirectoryAdapter}) and the observer that receives
     * booking's lifecycle calls ({@code BookingCleaningObserver}). Every other class reaches booking, if
     * at all, through {@code BookingDirectoryPort} - so a service class cannot quietly start depending on
     * booking's domain shape.
     */
    @Test
    void only_the_named_adapters_import_booking() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(ROOT + ".cleaning..")
                .and().resideOutsideOfPackage(ROOT + ".cleaning.infrastructure.integration..")
                .should().dependOnClassesThat().resideInAPackage(ROOT + ".booking..")
                .because("cleaning depends on booking only through BookingDirectoryPort, implemented in "
                        + "infrastructure.integration - see S159BookingDirectoryAdapter and BookingCleaningObserver");

        rule.check(classes);
    }

    /** The generic layering rule, restated for this module by name so a violation names S169 directly. */
    @Test
    void cleaning_domain_imports_no_framework() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(ROOT + ".cleaning.domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..", "jakarta.persistence..", "jakarta.servlet..",
                        "jakarta.validation..", "com.fasterxml.jackson..", "tools.jackson..", "io.swagger..")
                .because("the cleaning domain - schedules, tasks, the checklist and capacity policies - must be "
                        + "exercisable with no container, database or HTTP stack");

        rule.check(classes);
    }

    /** Every checklist and capacity rule lives in {@code domain.policy}, not scattered across the services. */
    @Test
    void cleaning_entities_live_only_in_persistence() {
        ArchRule rule = classes()
                .that().resideInAPackage(ROOT + ".cleaning..")
                .and().areAnnotatedWith(jakarta.persistence.Entity.class)
                .should().resideInAPackage(ROOT + ".cleaning.infrastructure.persistence..")
                .because("an entity outside the persistence adapter is a database shape leaking into the domain");

        rule.check(classes);
    }
}
