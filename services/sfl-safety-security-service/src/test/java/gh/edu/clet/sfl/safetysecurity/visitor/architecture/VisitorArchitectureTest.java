package gh.edu.clet.sfl.safetysecurity.visitor.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * The visitor domain must stay framework-free and independent of api/infrastructure adapters.
 *
 * <p>Scoped to {@code gh.edu.clet.sfl.safetysecurity.visitor} - {@code EmergencyArchitectureTest}'s
 * {@code @AnalyzeClasses} is scoped to {@code ..emergency} and does not see this package, so this
 * module needs its own copy of the same two rules rather than relying on the sibling test to widen.
 */
@AnalyzeClasses(packages = "gh.edu.clet.sfl.safetysecurity.visitor",
        importOptions = ImportOption.DoNotIncludeTests.class)
class VisitorArchitectureTest {

    @ArchTest
    static final ArchRule DOMAIN_IS_FRAMEWORK_FREE = noClasses().that().resideInAPackage("..visitor.domain..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta.persistence..",
                    "jakarta.servlet..", "org.springframework.jdbc..", "tools.jackson..", "io.swagger..");

    @ArchTest
    static final ArchRule DOMAIN_DOES_NOT_DEPEND_ON_ADAPTERS = noClasses().that()
            .resideInAPackage("..visitor.domain..").should().dependOnClassesThat()
            .resideInAnyPackage("..visitor.api..", "..visitor.infrastructure..");
}
