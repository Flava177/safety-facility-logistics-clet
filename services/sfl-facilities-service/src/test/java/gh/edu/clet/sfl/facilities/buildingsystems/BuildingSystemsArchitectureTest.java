package gh.edu.clet.sfl.facilities.buildingsystems;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * SRS-SFL-S156-05 / NFR-MAINT1, enforced by the catalogue rather than by review: "no domain module
 * references a vendor SDK, protocol library or vendor-specific data shape directly" and "a static/
 * architecture check ... confirms no domain package imports a vendor SDK".
 *
 * <p>The generic {@code FacilitiesArchitectureTest} already proves the platform-wide layering rules
 * (domain is framework-free, nothing points into infrastructure, entities live only in persistence). This
 * adds the two invariants specific to S156's Buy-and-Integrate posture.
 */
class BuildingSystemsArchitectureTest {

    private static final String ROOT = "gh.edu.clet.sfl.facilities";
    private static final String MODULE = ROOT + ".buildingsystems";

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT);
    }

    /**
     * Only {@code buildingsystems.infrastructure.integration} may know an adapter's identity. Every
     * vendor-specific class - {@code SimulatedBmsTelemetryAdapter}, {@code BacnetBridgeTelemetryAdapter} -
     * lives there; nothing in {@code api}, {@code application} or {@code domain} may name one.
     */
    @Test
    void no_class_outside_infrastructure_integration_depends_on_a_vendor_adapter() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(MODULE + "..")
                .and().resideOutsideOfPackage(MODULE + ".infrastructure.integration..")
                .should().dependOnClassesThat().resideInAPackage(MODULE + ".infrastructure.integration..")
                .because("vendor specifics live only behind the BmsTelemetryTranslatorPort adapter boundary "
                        + "(SRS-SFL-S156-05) - a class elsewhere naming an adapter is exactly the hard-wired "
                        + "dependency the requirement forbids");

        rule.check(classes);
    }

    /**
     * S152 (masterdata) and S153 (maintenance) never depend on S156. The dependency runs the other way -
     * S156 asks S152 to resolve a location and calls S153 through {@code BuildingWorkOrderPort} - and a
     * reverse import would mean a platform module could not build or be tested without a Buy-and-Integrate
     * one bolted on.
     */
    @Test
    void masterdata_and_maintenance_do_not_depend_on_buildingsystems() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage(ROOT + ".masterdata..", ROOT + ".maintenance..")
                .should().dependOnClassesThat().resideInAPackage(MODULE + "..")
                .because("S152 and S153 are platform modules S156 depends on, never the reverse "
                        + "(SRS-SFL-S156-05 acceptance criterion: a second vendor is addable by adding an "
                        + "adapter, not by changing S152/S153 domain logic)");

        rule.check(classes);
    }
}
