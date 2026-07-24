package gh.edu.clet.sfl.fleetlogistics.fuel.architecture;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
@AnalyzeClasses(packages="gh.edu.clet.sfl.fleetlogistics.fuel",importOptions=ImportOption.DoNotIncludeTests.class)
class FuelArchitectureTest {
 @ArchTest static final ArchRule DOMAIN_IS_FRAMEWORK_FREE=noClasses().that().resideInAPackage("..fuel.domain..").should().dependOnClassesThat().resideInAnyPackage("org.springframework..","jakarta.persistence..","jakarta.servlet..","org.springframework.jdbc..");
 @ArchTest static final ArchRule DOMAIN_DOES_NOT_DEPEND_ON_ADAPTERS=noClasses().that().resideInAPackage("..fuel.domain..").should().dependOnClassesThat().resideInAnyPackage("..fuel.api..","..fuel.infrastructure..");
}
