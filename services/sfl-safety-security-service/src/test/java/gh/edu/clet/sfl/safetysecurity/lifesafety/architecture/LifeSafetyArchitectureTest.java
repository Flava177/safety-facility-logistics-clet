package gh.edu.clet.sfl.safetysecurity.lifesafety.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * The life-safety domain must stay framework-free and independent of api/infrastructure adapters,
 * following {@code SecurityIncidentArchitectureTest}'s shape - scoped to {@code
 * gh.edu.clet.sfl.safetysecurity.lifesafety} since no sibling module's {@code @AnalyzeClasses} sees it.
 */
@AnalyzeClasses(packages = "gh.edu.clet.sfl.safetysecurity.lifesafety", importOptions = ImportOption.DoNotIncludeTests.class)
class LifeSafetyArchitectureTest {

    @ArchTest
    static final ArchRule DOMAIN_IS_FRAMEWORK_FREE = noClasses().that().resideInAPackage("..lifesafety.domain..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta.persistence..",
                    "jakarta.servlet..", "org.springframework.jdbc..", "tools.jackson..", "io.swagger..");

    @ArchTest
    static final ArchRule DOMAIN_DOES_NOT_DEPEND_ON_ADAPTERS = noClasses().that()
            .resideInAPackage("..lifesafety.domain..").should().dependOnClassesThat()
            .resideInAnyPackage("..lifesafety.api..", "..lifesafety.infrastructure..");

    /**
     * S162a's one architectural invariant that matters most: unlike access control, CCTV and
     * intrusion (each of which has an outbound port to push commands/config to its vendor system),
     * S162a observes, records, notifies and governs only - it must never sit in the certified
     * life-safety actuation path. Only {@code EmergencyFastLanePort} (an in-process S174 trigger, not
     * a vendor command) and the shared {@code IntegrationEventPublisher}/{@code AuditPort} outbound
     * ports may exist in {@code application.port}; nothing here may name "vendor", "gateway", "arm",
     * "disarm" or "actuat*" in a way that would imply this module controls the certified system.
     */
    @ArchTest
    static final ArchRule NO_OUTBOUND_VENDOR_COMMAND_PORT = classes().that()
            .resideInAPackage("..lifesafety.application.port..").should().haveSimpleNameNotContaining("Vendor")
            .andShould().haveSimpleNameNotContaining("Gateway")
            .andShould().haveSimpleNameNotContaining("Actuat");
}
