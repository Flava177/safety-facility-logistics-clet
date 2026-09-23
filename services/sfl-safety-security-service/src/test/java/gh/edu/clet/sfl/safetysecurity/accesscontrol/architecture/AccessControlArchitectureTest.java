package gh.edu.clet.sfl.safetysecurity.accesscontrol.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** The accesscontrol domain must stay framework-free and independent of api/infrastructure adapters -
 * mirrors {@code VisitorArchitectureTest}, scoped to this module's own package. */
@AnalyzeClasses(packages = "gh.edu.clet.sfl.safetysecurity.accesscontrol",
        importOptions = ImportOption.DoNotIncludeTests.class)
class AccessControlArchitectureTest {

    @ArchTest
    static final ArchRule DOMAIN_IS_FRAMEWORK_FREE = noClasses().that().resideInAPackage("..accesscontrol.domain..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta.persistence..",
                    "jakarta.servlet..", "org.springframework.jdbc..", "tools.jackson..", "io.swagger..");

    @ArchTest
    static final ArchRule DOMAIN_DOES_NOT_DEPEND_ON_ADAPTERS = noClasses().that()
            .resideInAPackage("..accesscontrol.domain..").should().dependOnClassesThat()
            .resideInAnyPackage("..accesscontrol.api..", "..accesscontrol.infrastructure..");
}
