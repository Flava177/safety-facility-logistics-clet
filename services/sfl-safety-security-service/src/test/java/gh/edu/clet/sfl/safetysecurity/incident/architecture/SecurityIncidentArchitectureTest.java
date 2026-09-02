package gh.edu.clet.sfl.safetysecurity.incident.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * The incident domain must stay framework-free and independent of api/infrastructure adapters.
 *
 * <p>Scoped to {@code gh.edu.clet.sfl.safetysecurity.incident} - neither {@code
 * EmergencyArchitectureTest} nor {@code VisitorArchitectureTest}'s {@code @AnalyzeClasses} sees this
 * package, so this module needs its own copy of the same two rules.
 */
@AnalyzeClasses(packages = "gh.edu.clet.sfl.safetysecurity.incident",
        importOptions = ImportOption.DoNotIncludeTests.class)
class SecurityIncidentArchitectureTest {

    @ArchTest
    static final ArchRule DOMAIN_IS_FRAMEWORK_FREE = noClasses().that().resideInAPackage("..incident.domain..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta.persistence..",
                    "jakarta.servlet..", "org.springframework.jdbc..", "tools.jackson..", "io.swagger..");

    @ArchTest
    static final ArchRule DOMAIN_DOES_NOT_DEPEND_ON_ADAPTERS = noClasses().that()
            .resideInAPackage("..incident.domain..").should().dependOnClassesThat()
            .resideInAnyPackage("..incident.api..", "..incident.infrastructure..");
}
