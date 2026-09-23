package gh.edu.clet.sfl.safetysecurity.intrusion.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** The intrusion domain must stay framework-free and independent of api/infrastructure adapters -
 * mirrors {@code AccessControlArchitectureTest}, scoped to this module's own package. */
@AnalyzeClasses(packages = "gh.edu.clet.sfl.safetysecurity.intrusion",
        importOptions = ImportOption.DoNotIncludeTests.class)
class IntrusionArchitectureTest {

    @ArchTest
    static final ArchRule DOMAIN_IS_FRAMEWORK_FREE = noClasses().that().resideInAPackage("..intrusion.domain..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta.persistence..",
                    "jakarta.servlet..", "org.springframework.jdbc..", "tools.jackson..", "io.swagger..");

    @ArchTest
    static final ArchRule DOMAIN_DOES_NOT_DEPEND_ON_ADAPTERS = noClasses().that()
            .resideInAPackage("..intrusion.domain..").should().dependOnClassesThat()
            .resideInAnyPackage("..intrusion.api..", "..intrusion.infrastructure..");
}
