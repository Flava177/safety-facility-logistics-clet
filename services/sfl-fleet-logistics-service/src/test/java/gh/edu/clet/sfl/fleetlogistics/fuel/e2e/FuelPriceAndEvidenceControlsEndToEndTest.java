package gh.edu.clet.sfl.fleetlogistics.fuel.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.common.security.SiteScopedPrincipal;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.EvidenceCommands.UploadEvidence;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.RegisterDriverCommand;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.RegisterVehicleCommand;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.DriverApplicationService;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.FleetEvidenceApplicationService;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.VehicleApplicationService;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.EvidenceRetentionClass;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.LicenceClass;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleCategory;
import gh.edu.clet.sfl.fleetlogistics.fleet.e2e.FleetPostgresSupport;
import gh.edu.clet.sfl.fleetlogistics.fuel.application.port.FuelRepository;
import gh.edu.clet.sfl.fleetlogistics.fuel.application.service.FuelApplicationService;
import gh.edu.clet.sfl.fleetlogistics.fuel.domain.model.FuelAnomalyCase;
import gh.edu.clet.sfl.fleetlogistics.fuel.domain.model.FuelPostedPrice;
import gh.edu.clet.sfl.fleetlogistics.fuel.domain.model.FuelTransaction;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * The controls that answer "a driver buys GHS 100 of fuel and records GHS 120".
 *
 * <p>Database-backed rather than a unit test, and necessarily so. Two of the three rules here read
 * rows written by a different persistence mechanism in the same transaction - the evidence digest
 * comes through JPA, the file content through JDBC - and an in-memory double has no persistence
 * context, so it cannot reproduce either the flush ordering or the foreign key that ordering exists
 * to satisfy. The first run of this against real PostgreSQL is what found that defect.
 */
@SpringBootTest(properties = {"sfl.security.enabled=false", "sfl.fuel.scheduling.enabled=false",
    "sfl.fleet.scheduling.outbox.enabled=false", "sfl.fleet.messaging.transport=local"})
@EnabledIf(value = "gh.edu.clet.sfl.fleetlogistics.fleet.e2e.FleetPostgresSupport#databaseAvailable",
        disabledReason = "No PostgreSQL available")
class FuelPriceAndEvidenceControlsEndToEndTest extends FleetPostgresSupport {

    @Autowired VehicleApplicationService vehicles;
    @Autowired DriverApplicationService drivers;
    @Autowired FuelApplicationService fuel;
    @Autowired FleetEvidenceApplicationService evidence;

    /** The smallest byte sequence that sniffs as a JPEG. Content is irrelevant; the digest is not. */
    private static byte[] photo(String distinguisher) {
        byte[] head = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
        byte[] tail = distinguisher.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        byte[] all = new byte[head.length + tail.length + 2];
        System.arraycopy(head, 0, all, 0, head.length);
        System.arraycopy(tail, 0, all, head.length, tail.length);
        all[all.length - 2] = (byte) 0xFF;
        all[all.length - 1] = (byte) 0xD9;
        return all;
    }

    @Test
    @DisplayName("an overstated price per litre is caught against the posted price, not against the last fill")
    void price_deviation_is_detected_against_the_posted_price() {
        Fixture f = fixture("PRICE");

        // The forecourt posts GHS 15.00. Everything after this is judged against that.
        FuelPostedPrice posted = fuel.recordPostedPrice(new FuelApplicationService.RecordPostedPrice(
                f.site(), "CLET STATION", "DIESEL", new BigDecimal("15.0000"), "GHS",
                f.now().minusSeconds(3600), FuelPostedPrice.Source.ADMINISTERED, "Forecourt sign",
                f.actor(), SourceChannel.WEB));
        assertThat(posted.effectiveTo()).isNull();

        // The driver claims GHS 18.00 a litre - twenty per cent over, which is the shape of the
        // collusion this exists to catch. Tolerance is the policy's 0.30 default, so 0.20 passes it;
        // the assertion below is that the *rule ran and recorded the comparison*, which is what makes
        // a steady overstatement visible to a reviewer even below the anomaly threshold.
        FuelTransaction claimed = capture(f, "OVERSTATED", new BigDecimal("18.0000"), null, null);
        FuelTransaction reconciled = fuel.reconcile(claimed.id(), f.actor(), SourceChannel.WEB);

        var run = fuel.reconciliations(reconciled.id(), f.actor()).get(0);
        @SuppressWarnings("unchecked")
        var priceRule = (java.util.Map<String, Object>) run.ruleResults().get("POSTED_PRICE");
        assertThat(priceRule).containsEntry("passed", true);
        // Compared numerically, not by toString. The rule map is stored as JSON, and the round trip
        // through it normalises 15.0000 to 15.0 - the value is preserved, the scale is not. Anything
        // reading these details for display must format them itself rather than print what it finds.
        assertThat(new BigDecimal(priceRule.get("referencePrice").toString()))
                .isEqualByComparingTo("15.0000");
        assertThat(new BigDecimal(priceRule.get("observedPrice").toString()))
                .isEqualByComparingTo("18.0000");
        assertThat(new BigDecimal(priceRule.get("deviation").toString())).isEqualByComparingTo("0.20");
        // Ten litres, three cedis a litre over the posted price. This is the figure a reviewer wants
        // first, and it is the money the claim overstates.
        assertThat(new BigDecimal(priceRule.get("overstatedBy").toString()))
                .isEqualByComparingTo("30.00");

        // Half as much again is past any tolerance, and raises the case.
        FuelTransaction egregious = capture(f, "EGREGIOUS", new BigDecimal("30.0000"), null, null);
        fuel.reconcile(egregious.id(), f.actor(), SourceChannel.WEB);
        assertThat(anomalyTypes(f, egregious.id())).contains(FuelAnomalyCase.Type.PRICE_DEVIATION);
    }

    @Test
    @DisplayName("a pump photograph reused on a second claim is caught by its digest")
    void reused_pump_photograph_is_detected() {
        Fixture f = fixture("REUSE");
        byte[] identical = photo("one-photograph-two-claims");

        UUID first = uploadPhoto(f, identical, "pump-a.jpg");
        UUID second = uploadPhoto(f, identical, "pump-b.jpg");

        FuelTransaction one = capture(f, "FIRST", new BigDecimal("15.0000"), null, first);
        fuel.reconcile(one.id(), f.actor(), SourceChannel.WEB);
        // Nothing to collide with yet on the first claim... except its own twin, which was uploaded
        // before it. Both directions are flagged, deliberately: whichever is reconciled first, the
        // duplicate pair surfaces.
        FuelTransaction two = capture(f, "SECOND", new BigDecimal("15.0000"), null, second);
        fuel.reconcile(two.id(), f.actor(), SourceChannel.WEB);

        assertThat(anomalyTypes(f, two.id())).contains(FuelAnomalyCase.Type.EVIDENCE_REUSED);

        var run = fuel.reconciliations(two.id(), f.actor()).get(0);
        @SuppressWarnings("unchecked")
        var rule = (java.util.Map<String, Object>) run.ruleResults().get("EVIDENCE_UNIQUE");
        assertThat(rule).containsEntry("passed", false);
        assertThat(rule.get("duplicates").toString()).contains(first.toString());
    }

    @Test
    @DisplayName("a distinct photograph on each claim passes the reuse rule")
    void distinct_photographs_pass() {
        Fixture f = fixture("DISTINCT");
        UUID pump = uploadPhoto(f, photo("genuinely-distinct-" + f.site()), "pump.jpg");

        FuelTransaction tx = capture(f, "CLEAN", new BigDecimal("15.0000"), null, pump);
        fuel.reconcile(tx.id(), f.actor(), SourceChannel.WEB);

        assertThat(anomalyTypes(f, tx.id())).doesNotContain(FuelAnomalyCase.Type.EVIDENCE_REUSED);
    }

    @Test
    @DisplayName("a site can record more than one manual fuel purchase")
    void manual_captures_do_not_collide_on_a_null_provider_reference() {
        Fixture f = fixture("MANUAL");

        /*
          The regression this pins is not hypothetical: it made the second manual fuel purchase at any
          site impossible.

          `uq_fuel_provider_transaction` was UNIQUE NULLS NOT DISTINCT over
          (site_code, source_system, provider_transaction_id). A driver at a pump has no provider
          transaction reference, so the column is null - and NULLS NOT DISTINCT makes every null equal
          to every other, so the first manual purchase consumed the key and the second was refused
          with a duplicate key violation. V31 relaxes it to NULLS DISTINCT.

          Any unit test would have passed. It took a real PostgreSQL to see it at all.
        */
        FuelTransaction first = capture(f, "ONE", new BigDecimal("15.0000"), null, null);
        FuelTransaction second = capture(f, "TWO", new BigDecimal("15.0000"), null, null);

        assertThat(first.providerTransactionId()).isNull();
        assertThat(second.providerTransactionId()).isNull();
        assertThat(second.id()).isNotEqualTo(first.id());

        // And the constraint still does its real job: a provider re-delivering one reference is
        // recognised as the same purchase rather than recorded twice.
        FuelTransaction delivered = fuel.capture(new FuelApplicationService.CaptureFuel(f.site(),
                "PROVIDER-REF-1", "MANUAL", f.vehicleId(), f.driverId(), null, f.now(), "CLET STATION",
                null, "DIESEL", new BigDecimal("10"), "LITRE", new BigDecimal("15.0000"),
                new BigDecimal("150.00"), "GHS", null, 1000, null, null, null, "provider-a-" + f.site(),
                f.actor(), SourceChannel.WEB));
        FuelTransaction redelivered = fuel.capture(new FuelApplicationService.CaptureFuel(f.site(),
                "PROVIDER-REF-1", "MANUAL", f.vehicleId(), f.driverId(), null, f.now(), "CLET STATION",
                null, "DIESEL", new BigDecimal("10"), "LITRE", new BigDecimal("15.0000"),
                new BigDecimal("150.00"), "GHS", null, 1000, null, null, null, "provider-b-" + f.site(),
                f.actor(), SourceChannel.WEB));
        assertThat(redelivered.id()).isEqualTo(delivered.id());
    }

    @Test
    @DisplayName("recording a new price closes the previous one rather than rewriting history")
    void a_superseding_price_closes_the_one_before_it() {
        Fixture f = fixture("SUPERSEDE");
        Instant firstFrom = f.now().minusSeconds(7200);

        fuel.recordPostedPrice(new FuelApplicationService.RecordPostedPrice(f.site(), "CLET STATION",
                "DIESEL", new BigDecimal("15.0000"), "GHS", firstFrom, FuelPostedPrice.Source.ADMINISTERED,
                null, f.actor(), SourceChannel.WEB));
        Instant secondFrom = f.now().minusSeconds(1800);
        fuel.recordPostedPrice(new FuelApplicationService.RecordPostedPrice(f.site(), "CLET STATION",
                "DIESEL", new BigDecimal("16.2000"), "GHS", secondFrom, FuelPostedPrice.Source.ADMINISTERED,
                null, f.actor(), SourceChannel.WEB));

        List<FuelPostedPrice> all = fuel.postedPrices(f.site(), "CLET STATION", "DIESEL", false, f.actor());
        assertThat(all).hasSize(2);
        // The old price is closed exactly where the new one starts - no gap, no overlap. A gap would
        // leave transactions in the middle with no reference price at all.
        FuelPostedPrice closed = all.stream().filter(p -> p.effectiveTo() != null).findFirst().orElseThrow();
        FuelPostedPrice open = all.stream().filter(p -> p.effectiveTo() == null).findFirst().orElseThrow();
        assertThat(closed.unitPrice()).isEqualByComparingTo("15.0000");
        /*
          Compared to the stored instant on the other row, not to the in-memory one that was sent.

          TIMESTAMPTZ keeps microseconds and Instant.now() produces nanoseconds here, so a round trip
          rounds the last three digits and a stored-versus-supplied comparison fails by 100ns. Which is
          not the property worth asserting anyway: what matters is that the closed period ends exactly
          where the open one begins, leaving no instant with two prices in force and none with zero.
        */
        assertThat(closed.effectiveTo()).isEqualTo(open.effectiveFrom());
        assertThat(open.unitPrice()).isEqualByComparingTo("16.2000");

        // A transaction from before the change is still judged against the price that was in force
        // then. Reconciliation has to reach the same verdict if it is run again next year.
        assertThat(fuel.postedPrices(f.site(), "CLET STATION", "DIESEL", true, f.actor()))
                .singleElement()
                .satisfies(price -> assertThat(price.unitPrice()).isEqualByComparingTo("16.2000"));
    }

    /* ------------------------------------------------------------------------------- fixtures */

    private record Fixture(String site, ActorContext actor, UUID vehicleId, UUID driverId, Instant now) {
    }

    private Fixture fixture(String prefix) {
        String site = prefix + System.nanoTime();
        ActorContext actor = new ActorContext(new SiteScopedPrincipal("fuel.manager", "Fuel Manager",
                Set.of(SflRole.FLEET_MANAGER), Set.of(site), false), "fuel-controls-e2e");
        Instant now = Instant.now();
        var vehicle = vehicles.register(new RegisterVehicleCommand("GN-" + site, null, "Toyota", "Hilux",
                2024, VehicleCategory.PICKUP, 5, site, "Transport", "Fleet Manager", null, 1000, false,
                Set.of(), actor, SourceChannel.WEB, "vehicle-" + site));
        var driver = drivers.register(new RegisterDriverCommand("DRV-" + site, "Fuel Driver", "LIC-" + site,
                LicenceClass.B, LocalDate.now().plusYears(2), LocalDate.now().plusYears(1), site, "Transport",
                "DRV-" + site, actor, SourceChannel.WEB, "driver-" + site));
        fuel.createPolicy(new FuelApplicationService.CreatePolicy(site, "Default", now.minusSeconds(86400),
                null, 1, new BigDecimal("50"), null, null, new BigDecimal("80"), null, null, 500, true, 24,
                new BigDecimal("400"), 8, Set.of("DIESEL"), Set.of("CLET STATION"), actor, SourceChannel.WEB));
        return new Fixture(site, actor, vehicle.id(), driver.id(), now);
    }

    private UUID uploadPhoto(Fixture f, byte[] content, String fileName) {
        return evidence.upload(new UploadEvidence(f.site(), "Vehicle", f.vehicleId().toString(),
                "FUEL_PUMP_READING", fileName, "image/jpeg", content,
                EvidenceRetentionClass.COMPLIANCE_7_YEARS, null, f.actor(), SourceChannel.WEB)).id();
    }

    private FuelTransaction capture(Fixture f, String key, BigDecimal unitPrice, UUID receipt, UUID pump) {
        // Ten litres at whatever price, so the total moves with the price and nothing else does.
        BigDecimal quantity = new BigDecimal("10");
        return fuel.capture(new FuelApplicationService.CaptureFuel(f.site(), null, "MANUAL", f.vehicleId(),
                f.driverId(), null, f.now(), "CLET STATION", "PUMP-1", "DIESEL", quantity, "LITRE",
                unitPrice, quantity.multiply(unitPrice).setScale(2, java.math.RoundingMode.HALF_UP), "GHS",
                null, 1000, receipt, pump, null, key + "-" + f.site(), f.actor(), SourceChannel.WEB));
    }

    private List<FuelAnomalyCase.Type> anomalyTypes(Fixture f, UUID transactionId) {
        return fuel.anomalies(f.site(), null, null, null, null, null, null, null, null, transactionId,
                        new FuelRepository.Paging(0, 100, null), f.actor())
                .content().stream().map(FuelAnomalyCase::type).toList();
    }
}
