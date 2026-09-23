package gh.edu.clet.sfl.safetysecurity.cctv.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** The cctv domain must stay framework-free and independent of api/infrastructure adapters - mirrors
 * {@code AccessControlArchitectureTest}, scoped to this module's own package. */
@AnalyzeClasses(packages = "gh.edu.clet.sfl.safetysecurity.cctv", importOptions = ImportOption.DoNotIncludeTests.class)
class CctvArchitectureTest {

    @ArchTest
    static final ArchRule DOMAIN_IS_FRAMEWORK_FREE = noClasses().that().resideInAPackage("..cctv.domain..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta.persistence..",
                    "jakarta.servlet..", "org.springframework.jdbc..", "tools.jackson..", "io.swagger..");

    @ArchTest
    static final ArchRule DOMAIN_DOES_NOT_DEPEND_ON_ADAPTERS = noClasses().that()
            .resideInAPackage("..cctv.domain..").should().dependOnClassesThat()
            .resideInAnyPackage("..cctv.api..", "..cctv.infrastructure..");
}
