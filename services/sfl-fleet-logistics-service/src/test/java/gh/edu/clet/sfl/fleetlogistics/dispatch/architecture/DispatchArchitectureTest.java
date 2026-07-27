package gh.edu.clet.sfl.fleetlogistics.dispatch.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** The dispatch domain must stay framework-free and independent of the api/infrastructure adapters. */
@AnalyzeClasses(packages = "gh.edu.clet.sfl.fleetlogistics.dispatch",
        importOptions = ImportOption.DoNotIncludeTests.class)
class DispatchArchitectureTest {

    @ArchTest
    static final ArchRule DOMAIN_IS_FRAMEWORK_FREE = noClasses().that().resideInAPackage("..dispatch.domain..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta.persistence..",
                    "jakarta.servlet..", "org.springframework.jdbc..");

    @ArchTest
    static final ArchRule DOMAIN_DOES_NOT_DEPEND_ON_ADAPTERS = noClasses().that()
            .resideInAPackage("..dispatch.domain..").should().dependOnClassesThat()
            .resideInAnyPackage("..dispatch.api..", "..dispatch.infrastructure..");
}
