package gh.edu.clet.sfl.facilities.construction.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * S176's own architectural invariants, beyond the generic layering {@code FacilitiesArchitectureTest}
 * already enforces across every module.
 */
class ConstructionArchitectureTest {

    private static final String ROOT = "gh.edu.clet.sfl.facilities.construction";

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT);
    }

    /**
     * Only the three named adapters may import a sibling <em>business</em> module's package - S153
     * (maintenance), S158 (spaceplanning) and any future module S176 comes to depend on. Every other
     * cross-module reference in S176 goes through this module's own ports -
     * {@code DefectWorkOrderPort}, {@code ScenarioConfirmationPort}, {@code SiteAccessPort} - so a test
     * can double any of them, exactly as the house checklist asks.
     *
     * <p>{@code masterdata} (S152) is deliberately not in this list. It is the estate register every
     * module in this service is built on top of - {@code booking} and {@code maintenance} import its
     * types directly too - so depending on {@code SpaceType}, {@code FacilityRoom} and the like is the
     * shared-kernel case this rule is not aimed at; {@code EstateRegisterPort} exists to keep S176's
     * own services off {@code FacilitiesMasterDataService} and its persistence, which it does.
     */
    @Test
    void only_the_named_adapters_import_a_sibling_business_module() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(ROOT + "..")
                .and().resideOutsideOfPackages(
                        "gh.edu.clet.sfl.facilities.construction.infrastructure.integration..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "gh.edu.clet.sfl.facilities.maintenance..",
                        "gh.edu.clet.sfl.facilities.spaceplanning..",
                        "gh.edu.clet.sfl.facilities.booking..",
                        "gh.edu.clet.sfl.facilities.buildingsystems..",
                        "gh.edu.clet.sfl.facilities.cleaning..")
                .because("S176 reaches another business module only through its own port; the adapter "
                        + "behind the port is the one place allowed to name it");

        rule.check(classes);
    }

    /** The domain stays framework-free, on top of what the shared rule already checks. */
    @Test
    void the_domain_never_imports_infrastructure_or_application() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(ROOT + ".domain..")
                .should().dependOnClassesThat().resideInAnyPackage(ROOT + ".infrastructure..", ROOT + ".api..")
                .because("the S176 domain is exercisable without a container, a database or an HTTP stack");

        rule.check(classes);
    }

    /**
     * Only {@code ConstructionRefusal} may be thrown by an application service after a refusal is
     * recorded and left to commit ({@code noRollbackFor}). Confirmed narrowly: it must still be a
     * {@code FacilitiesException}, so the shared exception handler maps it like any other S176 refusal.
     */
    @Test
    void construction_refusal_is_a_facilities_exception() {
        assertThatConstructionRefusalExtendsFacilitiesException();
    }

    private static void assertThatConstructionRefusalExtendsFacilitiesException() {
        org.assertj.core.api.Assertions.assertThat(
                gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException.class.isAssignableFrom(
                        gh.edu.clet.sfl.facilities.construction.domain.ConstructionRefusal.class)).isTrue();
    }
}
