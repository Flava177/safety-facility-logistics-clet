package gh.edu.clet.sfl.fleetlogistics.fleet.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.AuditPort;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

/**
 * The dependency rule, enforced.
 *
 * <p>{@code api -> application -> domain}; {@code infrastructure -> application/domain}; nothing points
 * into {@code infrastructure}; the domain imports no framework and names no vendor.
 */
class FleetArchitectureTest {

    private static final String BASE = "gh.edu.clet.sfl.fleetlogistics";
    private static final String FLEET = BASE + ".fleet";

    private static JavaClasses fleetClasses;

    @BeforeAll
    static void importClasses() {
        fleetClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(BASE);
    }

    @Test
    @DisplayName("the domain layer imports no framework, persistence, broker or vendor type")
    void domain_is_framework_free() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(FLEET + ".domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..",
                        "jakarta.servlet..",
                        "jakarta.validation..",
                        "org.hibernate..",
                        "tools.jackson..",
                        "com.fasterxml.jackson..",
                        "org.postgresql..",
                        "com.rabbitmq..",
                        "org.slf4j..",
                        "io.lettuce..",
                        "redis..")
                .because("SRS/workplan dependency rule: the domain layer depends on nothing framework-specific");

        rule.check(fleetClasses);
    }

    @Test
    @DisplayName("the domain layer does not depend on the outer layers")
    void domain_does_not_depend_on_outer_layers() {
        noClasses()
                .that().resideInAPackage(FLEET + ".domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        FLEET + ".api..",
                        FLEET + ".application..",
                        FLEET + ".infrastructure..",
                        FLEET + ".config..")
                .because("the dependency rule points inwards only")
                .check(fleetClasses);
    }

    @Test
    @DisplayName("the application layer does not depend on the API or infrastructure layers")
    void application_does_not_depend_on_api_or_infrastructure() {
        noClasses()
                .that().resideInAPackage(FLEET + ".application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        FLEET + ".api..",
                        FLEET + ".infrastructure..",
                        FLEET + ".config..")
                .because("outbound ports belong to the application layer; adapters implement them")
                .check(fleetClasses);
    }

    @Test
    @DisplayName("the API layer does not reach into infrastructure")
    void api_does_not_depend_on_infrastructure() {
        noClasses()
                .that().resideInAPackage(FLEET + ".api..")
                .should().dependOnClassesThat().resideInAPackage(FLEET + ".infrastructure..")
                .because("controllers talk to application services, never to adapters")
                .check(fleetClasses);
    }

    /**
     * The rule is "an entity lives in an infrastructure package", not "an entity lives in
     * <em>fleet's</em> infrastructure package". It used to say {@code FLEET + ".infrastructure.."},
     * which was the same thing while fleet was the only module here with JPA - fuel and dispatch are
     * JDBC. AVAMP arrived with two entities under {@code assets.infrastructure.persistence} and the
     * rule failed, correctly and usefully: it was the only check that noticed the merge at all.
     * Widening it to any module's infrastructure keeps the constraint that matters - entities stay out
     * of domain and application - while letting each module own its own adapters.
     */
    @Test
    @DisplayName("JPA entities live only in the persistence adapters")
    void jpa_entities_live_only_in_infrastructure() {
        classes()
                .that().areAnnotatedWith(jakarta.persistence.Entity.class)
                .should().resideInAnyPackage(BASE + "..infrastructure..")
                .because("JPA entities are a persistence detail and must never become domain aggregates")
                .check(fleetClasses);
    }

    @Test
    @DisplayName("controllers do not own transactions and do not use repositories directly")
    void controllers_do_not_own_transactions_or_repositories() {
        noClasses()
                .that().resideInAPackage(FLEET + ".api..")
                .should().beAnnotatedWith(org.springframework.transaction.annotation.Transactional.class)
                .because("application services own the transaction boundary")
                .check(fleetClasses);

        noClasses()
                .that().resideInAPackage(FLEET + ".api..")
                .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository")
                .because("controllers must not contain persistence or business logic")
                .check(fleetClasses);
    }

    @Test
    @DisplayName("outbound ports are interfaces")
    void ports_are_interfaces() {
        classes()
                .that().resideInAPackage(FLEET + ".application.port..")
                .and().areTopLevelClasses()
                .should().beInterfaces()
                .because("a port is a contract the application owns and infrastructure implements")
                .check(fleetClasses);
    }

    /**
     * The rule that would have caught two 500s before anyone clicked anything.
     *
     * <p>{@code JpaAuditAdapter.record} is declared {@code MANDATORY}, deliberately: an audit row must
     * commit or roll back with the operation it describes, so it has to join a transaction it did not
     * start. The consequence is that a public method writing audit with no transaction of its own does
     * not degrade - it throws {@code IllegalTransactionStateException} before doing any work, and the
     * caller sees an unexplained server error. That is what {@code recordAccess} on evidence did, and
     * {@code replay} on the integration inbox did the same thing for the same reason.
     *
     * <p>Public methods only. A private or package-private helper that writes audit is called from a
     * public method that owns the boundary, which is the normal shape here and not worth outlawing;
     * a public one is an entry point, and an entry point has no caller to inherit a transaction from.
     */
    @Test
    @DisplayName("a public method that writes audit declares a transaction")
    void public_audit_writers_are_transactional() {
        DescribedPredicate<JavaMethod> writeAuditPublicly =
                new DescribedPredicate<>("are public and call AuditPort.record") {
                    @Override
                    public boolean test(JavaMethod method) {
                        return method.getModifiers().contains(JavaModifier.PUBLIC)
                                && method.getMethodCallsFromSelf().stream()
                                        .anyMatch(call -> "record".equals(call.getName())
                                                && call.getTargetOwner().isAssignableTo(AuditPort.class));
                    }
                };

        methods()
                .that(writeAuditPublicly)
                .should().beAnnotatedWith(Transactional.class)
                .because("AuditPort.record is MANDATORY, so an entry point that writes audit without a "
                        + "transaction fails outright rather than skipping the audit entry")
                .check(fleetClasses);
    }

    @Test
    @DisplayName("no provider or vendor product name appears in the domain layer")
    void domain_names_no_vendor() {
        // Vendor and product names that must stay behind an adapter. If a real vendor is procured, add
        // its name here rather than letting it appear in an aggregate.
        String[] forbidden = {"Keycloak", "Rabbit", "Postgres", "Redis", "Hibernate", "Jackson", "Samsara",
                "Geotab", "Wialon", "Cartrack", "Twilio", "Fixi", "Sap", "Oracle"};
        for (String vendor : forbidden) {
            noClasses()
                    .that().resideInAPackage(FLEET + ".domain..")
                    .should().haveSimpleNameContaining(vendor)
                    .because("no provider-specific model or name may appear in the domain layer (SRS-SFL-S166-04)")
                    .check(fleetClasses);
        }
    }
}
