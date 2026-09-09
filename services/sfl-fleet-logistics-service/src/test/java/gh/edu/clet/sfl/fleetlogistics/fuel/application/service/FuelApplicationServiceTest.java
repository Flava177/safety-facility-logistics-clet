package gh.edu.clet.sfl.fleetlogistics.fuel.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.DriverScopeResolver;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.FleetAuthorizationException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.RecordMetadata;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import gh.edu.clet.sfl.fleetlogistics.fleet.support.FleetTestDoubles;
import gh.edu.clet.sfl.fleetlogistics.fuel.domain.exception.FuelPolicyPeriodOverlapException;
import gh.edu.clet.sfl.fleetlogistics.fuel.domain.exception.FuelPolicyVersionNotAdvancedException;
import gh.edu.clet.sfl.fleetlogistics.fuel.domain.model.FuelPolicy;
import gh.edu.clet.sfl.fleetlogistics.fuel.domain.model.FuelTransaction;
import gh.edu.clet.sfl.fleetlogistics.fuel.support.FuelTestDoubles;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Traces: SRS-SFL-S168fuel - policy effective-dating and overlap, capture/reconciliation, and the
 * per-record authorisation the audit found undertested next to fleet's own services.
 */
class FuelApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-21T08:00:00Z");
    private static final String SITE = "ACCRA";

    private FuelTestDoubles.InMemoryFuelRepository repository;
    private FuelTestDoubles.StubFleetReferencePort fleet;
    private FleetTestDoubles.RecordingAuditPort audit;
    private FleetTestDoubles.RecordingEventPublisher events;
    private FuelApplicationService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        repository = new FuelTestDoubles.InMemoryFuelRepository();
        fleet = new FuelTestDoubles.StubFleetReferencePort();
        audit = new FleetTestDoubles.RecordingAuditPort(clock);
        events = new FleetTestDoubles.RecordingEventPublisher();
        var driverProfiles = new FleetTestDoubles.InMemoryDriverProfileRepository();
        var driverScopes = new DriverScopeResolver(driverProfiles, new gh.edu.clet.sfl.fleetlogistics.fleet.application.service.FleetAccessPolicy());
        service = new FuelApplicationService(repository, fleet, new FuelAccessPolicy(), audit, events,
                new FleetTestDoubles.InMemoryIdempotencyPort(), clock, new FleetTestDoubles.RecordingNotificationPort(),
                new FuelTestDoubles.RecordingFinanceAuditPort(), new FuelTestDoubles.StubOutboxAdminPort(),
                driverScopes, new FuelTestDoubles.InMemoryEvidencePort());
    }

    @Test
    @DisplayName("a policy is created, audited and readable back by its site")
    void createPolicy_persists_and_is_readable_by_site() {
        FuelPolicy policy = service.createPolicy(policy("GOIL only", NOW.minusSeconds(3600), null, 1));

        assertThat(policy.id()).isNotNull();
        assertThat(policy.status()).isEqualTo(FuelPolicy.Status.ACTIVE);
        assertThat(service.policy(policy.id(), manager()).name()).isEqualTo("GOIL only");
        assertThat(audit.hasRecord(gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.AuditAction.CREATE,
                "FuelPolicy")).isTrue();
    }

    @Test
    @DisplayName("a policy period that overlaps an existing active policy for the site is refused")
    void createPolicy_overlapping_active_policy_is_refused() {
        service.createPolicy(policy("First", NOW.minusSeconds(7200), null, 1));

        assertThatThrownBy(() -> service.createPolicy(policy("Second", NOW.minusSeconds(3600), null, 1)))
                .isInstanceOf(FuelPolicyPeriodOverlapException.class);
    }

    @Test
    @DisplayName("revising a policy's limits while reusing its version is refused")
    void updatePolicy_same_version_different_rules_is_refused() {
        FuelPolicy created = service.createPolicy(policy("Base", NOW.minusSeconds(7200), null, 1));

        var revision = new FuelApplicationService.UpdatePolicy(created.id(), created.name(), created.effectiveFrom(),
                created.effectiveTo(), created.policyVersion(), BigDecimal.valueOf(999), created.dailyLimit(),
                created.monthlyLimit(), created.tankCapacity(), created.minConsumption(), created.maxConsumption(),
                created.odometerJumpTolerance(), created.receiptRequired(), created.receiptGraceHours(),
                created.materialityAmount(), created.anomalySlaHours(), created.costVarianceTolerance(),
                created.repeatedPatternWindowHours(), created.repeatedPatternThreshold(), created.allowedFuelProducts(),
                created.approvedVendors(), manager(), SourceChannel.WEB);

        assertThatThrownBy(() -> service.updatePolicy(revision))
                .isInstanceOf(FuelPolicyVersionNotAdvancedException.class);
    }

    @Test
    @DisplayName("a captured transaction that violates no rule reconciles clean and emits the reconciled event")
    void capture_then_reconcile_marks_transaction_reconciled_when_no_violations() {
        service.createPolicy(policy("Base", NOW.minusSeconds(7200), null, 1));
        FuelTransaction captured = service.capture(captureCommand());

        FuelTransaction reconciled = service.reconcile(captured.id(), manager(), SourceChannel.WEB);

        assertThat(reconciled.status()).isEqualTo(FuelTransaction.Status.RECONCILED);
        assertThat(events.types()).contains(
                gh.edu.clet.sfl.fleetlogistics.fleet.domain.event.FleetEventType.FUEL_TRANSACTION_RECONCILED);
    }

    @Test
    @DisplayName("reading a transaction is refused to an actor with no access to its site")
    void transaction_read_is_denied_to_an_actor_without_site_access() {
        service.createPolicy(policy("Base", NOW.minusSeconds(7200), null, 1));
        FuelTransaction captured = service.capture(captureCommand());

        var outsider = FuelTestDoubles.fuelManager("TAMALE");

        assertThatThrownBy(() -> service.transaction(captured.id(), outsider))
                .isInstanceOf(FleetAuthorizationException.class);
    }

    private FuelApplicationService.CreatePolicy policy(String name, Instant from, Instant to, int version) {
        return new FuelApplicationService.CreatePolicy(SITE, name, from, to, version, BigDecimal.valueOf(200),
                BigDecimal.valueOf(400), BigDecimal.valueOf(4000), BigDecimal.valueOf(80), BigDecimal.valueOf(2),
                BigDecimal.valueOf(20), 50, false, 48, BigDecimal.valueOf(500), 72, Set.of(), Set.of(), manager(),
                SourceChannel.WEB);
    }

    private FuelApplicationService.CaptureFuel captureCommand() {
        return new FuelApplicationService.CaptureFuel(SITE, "PTX-1", "MANUAL", UUID.randomUUID(), UUID.randomUUID(),
                null, NOW.minusSeconds(600), "GOIL", "Station 1", "PETROL", BigDecimal.valueOf(40), "LITRE",
                BigDecimal.valueOf(15), null, "GHS", null, 30, null, null, null, "idem-" + UUID.randomUUID(),
                manager(), SourceChannel.WEB);
    }

    private static gh.edu.clet.sfl.common.security.ActorContext manager() {
        return FuelTestDoubles.actor("manager@clet.edu.gh", Set.of(SflRole.FLEET_MANAGER), Set.of(SITE));
    }
}
