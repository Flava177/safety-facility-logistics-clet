package gh.edu.clet.sfl.fleetlogistics.fuel.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.common.security.SiteScopedPrincipal;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.RegisterDriverCommand;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.RegisterVehicleCommand;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.AuditPort;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.DriverApplicationService;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.VehicleApplicationService;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.FleetErrorCode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.DriverProfileReference;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.LicenceClass;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.Vehicle;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleCategory;
import gh.edu.clet.sfl.fleetlogistics.fleet.e2e.FleetPostgresSupport;
import gh.edu.clet.sfl.fleetlogistics.fuel.application.port.FuelRepository;
import gh.edu.clet.sfl.fleetlogistics.fuel.application.service.FuelApplicationService;
import gh.edu.clet.sfl.fleetlogistics.fuel.application.service.FuelImportService;
import gh.edu.clet.sfl.fleetlogistics.fuel.domain.exception.FuelImportAlreadyProcessedException;
import gh.edu.clet.sfl.fleetlogistics.fuel.domain.exception.FuelPolicyPeriodOverlapException;
import gh.edu.clet.sfl.fleetlogistics.fuel.domain.model.DriverLogbook;
import gh.edu.clet.sfl.fleetlogistics.fuel.domain.model.FuelAnomalyCase;
import gh.edu.clet.sfl.fleetlogistics.fuel.domain.model.FuelImportRow;
import gh.edu.clet.sfl.fleetlogistics.fuel.domain.model.FuelTransaction;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Proof for the gaps closed after the S168 dashboard was built.
 *
 * <p>Each test names the gap it covers. They exist because every one of these was a real defect
 * found by driving the running service, not by reading the source — the kind that a compiling build
 * and a passing unit suite both miss.
 */
@SpringBootTest(properties={"sfl.security.enabled=false","sfl.fuel.scheduling.enabled=false","sfl.fleet.scheduling.outbox.enabled=false","sfl.fleet.messaging.transport=local"})
@EnabledIf(value="gh.edu.clet.sfl.fleetlogistics.fleet.e2e.FleetPostgresSupport#databaseAvailable",disabledReason="No PostgreSQL available")
class FuelGapClosureEndToEndTest extends FleetPostgresSupport {

    @Autowired VehicleApplicationService vehicleService;
    @Autowired DriverApplicationService driverService;
    @Autowired FuelApplicationService fuel;
    @Autowired FuelImportService imports;
    @Autowired FuelRepository repository;
    @Autowired AuditPort audit;

    private record Fixture(String site, ActorContext manager, Vehicle vehicle, DriverProfileReference driver) {}

    private static FuelRepository.Paging page(int page, int size) {
        return new FuelRepository.Paging(page, size, null);
    }

    private Fixture newFixture(boolean withPolicy) {
        String site = "GAP" + System.nanoTime();
        ActorContext manager = new ActorContext(new SiteScopedPrincipal("fuel.manager","Fuel Manager",
                Set.of(SflRole.FLEET_MANAGER),Set.of(site),false),"fuel-gap-e2e");
        var vehicle = vehicleService.register(new RegisterVehicleCommand("GN-"+site,null,"Toyota","Hilux",2024,
                VehicleCategory.PICKUP,5,site,"Transport","Fleet Manager",null,1000,false,Set.of(),manager,
                SourceChannel.WEB,"vehicle-"+site));
        var driver = driverService.register(new RegisterDriverCommand("DRV-"+site,"Fuel Driver","LIC-"+site,
                LicenceClass.B,LocalDate.now().plusYears(2),LocalDate.now().plusYears(1),site,"Transport",manager,
                SourceChannel.WEB,"driver-"+site));
        if (withPolicy) {
            fuel.createPolicy(new FuelApplicationService.CreatePolicy(site,"Default",
                    Instant.now().minusSeconds(7L*24*3600),null,1,new BigDecimal("50"),null,null,new BigDecimal("80"),
                    null,null,500,true,24,new BigDecimal("400"),8,Set.of("DIESEL"),Set.of("CLET STATION"),manager,
                    SourceChannel.WEB));
        }
        return new Fixture(site,manager,vehicle,driver);
    }

    private FuelTransaction capture(Fixture f, String provider, BigDecimal litres, long reading) {
        return fuel.capture(new FuelApplicationService.CaptureFuel(f.site(),provider,"MANUAL",f.vehicle().id(),
                f.driver().id(),null,Instant.now(),"CLET STATION","PUMP-1","DIESEL",litres,"LITRE",
                new BigDecimal("10"),null,"GHS","1234567890",reading,UUID.randomUUID(),null,
                "tx-"+provider+"-"+f.site(),f.manager(),SourceChannel.WEB));
    }

    /** Gap 1: reconciliation rule results are stored on every run and were readable from none of it. */
    @Test void reconciliation_results_are_readable_with_their_rule_outcomes() {
        Fixture f = newFixture(true);
        var tx = capture(f,"PROVIDER-RECON",new BigDecimal("70"),1100);   // 70 L against a 50 L limit
        fuel.reconcile(tx.id(),f.manager(),SourceChannel.WEB);

        var runs = fuel.reconciliations(tx.id(),f.manager());
        assertThat(runs).hasSize(1);
        var run = runs.get(0);
        assertThat(run.outcome()).isEqualTo("EXCEPTION");
        assertThat(run.policyVersion()).isEqualTo(1);
        // The half no screen could show before: rules that passed, named.
        assertThat(run.failedRules()).contains("MAX_PER_TRANSACTION");
        assertThat(run.passedRules()).contains("FUEL_PRODUCT","APPROVED_VENDOR");
        assertThat(run.ruleResults()).containsKey("ODOMETER_JUMP");
    }

    /** Gap 1: a rerun appends rather than amending, so the history of decisions survives. */
    @Test void reconciliation_reruns_append_rather_than_replace() {
        Fixture f = newFixture(true);
        var tx = capture(f,"PROVIDER-RERUN",new BigDecimal("20"),1100);
        fuel.reconcile(tx.id(),f.manager(),SourceChannel.WEB);
        fuel.reconcile(tx.id(),f.manager(),SourceChannel.WEB);
        assertThat(fuel.reconciliations(tx.id(),f.manager())).hasSize(2);
    }

    /** Gap 2: the batch and its rows are written on every upload and were readable from none of it. */
    @Test void import_batches_and_their_rows_are_readable_afterwards() {
        Fixture f = newFixture(true);
        String csv = """
                vehicleId,driverId,occurredAt,vendorReference,fuelProduct,quantity,quantityUnit,unitPrice,currency,odometerReading
                %s,%s,%s,CLET STATION,DIESEL,20,LITRE,10,GHS,1100
                00000000-0000-0000-0000-000000000000,%s,%s,CLET STATION,DIESEL,20,LITRE,10,GHS,1200
                """.formatted(f.vehicle().id(),f.driver().id(),Instant.now(),f.driver().id(),Instant.now());

        var result = imports.importCsv(f.site(),"CSV-TEST","fuel.csv",csv.getBytes(StandardCharsets.UTF_8),f.manager());
        assertThat(result.totalRows()).isEqualTo(2);
        assertThat(result.acceptedRows()).isEqualTo(1);
        assertThat(result.rejectedRows()).isEqualTo(1);

        var batch = fuel.importBatch(result.batchId(),f.manager());
        assertThat(batch.rows()).hasSize(2);
        assertThat(batch.status()).isEqualTo(gh.edu.clet.sfl.fleetlogistics.fuel.domain.model.FuelImportBatch.Status.COMPLETED_WITH_ERRORS);
        // The row-level reason is retained, which is the whole point of being able to reopen a batch.
        assertThat(batch.rows()).anySatisfy(row -> {
            if (!row.accepted()) {
                assertThat(row.errorCode()).isEqualTo("FUEL_IMPORT_ROW_INVALID");
                assertThat(row.errorMessage()).isNotBlank();
            }
        });
        assertThat(fuel.importBatches(f.site(),null,page(0,25),f.manager()).totalElements()).isEqualTo(1);

        // The rows are also readable a page at a time, and the status filter runs in SQL. The screen
        // used to take every row from the detail read above and filter the array, so "rejected only"
        // on a large file found the rejections that happened to be on the page being looked at.
        var everyRow = fuel.importRows(result.batchId(),null,page(0,25),f.manager());
        assertThat(everyRow.totalElements()).isEqualTo(2);

        var rejected = fuel.importRows(result.batchId(),FuelImportRow.Status.REJECTED,page(0,25),f.manager());
        // The total describes the filter, not the batch — count and page share one predicate.
        assertThat(rejected.totalElements()).isEqualTo(1);
        assertThat(rejected.content()).singleElement()
                .satisfies(row -> assertThat(row.status()).isEqualTo(FuelImportRow.Status.REJECTED));

        var accepted = fuel.importRows(result.batchId(),FuelImportRow.Status.ACCEPTED,page(0,25),f.manager());
        assertThat(accepted.totalElements()).isEqualTo(1);
        assertThat(accepted.content()).singleElement()
                .satisfies(row -> assertThat(row.accepted()).isTrue());
    }

    /** Gap 12: a re-uploaded file was refused by the constraint and escaped as an unmapped 500. */
    @Test void reimporting_the_same_file_is_refused_with_a_mapped_error() {
        Fixture f = newFixture(true);
        String csv = """
                vehicleId,driverId,occurredAt,vendorReference,fuelProduct,quantity,quantityUnit,unitPrice,currency,odometerReading
                %s,%s,%s,CLET STATION,DIESEL,20,LITRE,10,GHS,1100
                """.formatted(f.vehicle().id(),f.driver().id(),Instant.now());
        byte[] content = csv.getBytes(StandardCharsets.UTF_8);

        imports.importCsv(f.site(),"CSV-DUP","fuel.csv",content,f.manager());
        assertThatThrownBy(() -> imports.importCsv(f.site(),"CSV-DUP","fuel.csv",content,f.manager()))
                .isInstanceOf(FuelImportAlreadyProcessedException.class)
                .satisfies(e -> assertThat(((FuelImportAlreadyProcessedException) e).errorCode())
                        .isEqualTo(FleetErrorCode.FUEL_IMPORT_ALREADY_PROCESSED));

        // Nothing was duplicated: exactly one batch, and capture idempotency held for the rows.
        assertThat(fuel.importBatches(f.site(),"CSV-DUP",page(0,25),f.manager()).totalElements()).isEqualTo(1);
    }

    /** Gap 11: the documented no-overlap invariant that nothing enforced. */
    @Test void an_overlapping_active_policy_is_refused() {
        Fixture f = newFixture(false);
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-07-01T00:00:00Z");
        fuel.createPolicy(policy(f,"First",from,to,1));

        // Starts inside the first policy's period.
        assertThatThrownBy(() -> fuel.createPolicy(policy(f,"Overlapping",Instant.parse("2026-06-01T00:00:00Z"),null,2)))
                .isInstanceOf(FuelPolicyPeriodOverlapException.class)
                .satisfies(e -> assertThat(((FuelPolicyPeriodOverlapException) e).details())
                        .containsKey("conflictingPolicies"));

        // Abutting rather than overlapping is legal: the first period is half-open and ends at `to`.
        var successor = fuel.createPolicy(policy(f,"Successor",to,null,2));
        assertThat(successor.policyVersion()).isEqualTo(2);
    }

    private FuelApplicationService.CreatePolicy policy(Fixture f,String name,Instant from,Instant to,int version) {
        return new FuelApplicationService.CreatePolicy(f.site(),name,from,to,version,new BigDecimal("50"),null,null,
                null,null,null,500,true,24,new BigDecimal("400"),8,Set.of("DIESEL"),Set.of("CLET STATION"),
                f.manager(),SourceChannel.WEB);
    }

    /** Gap 4: collections are paged, with a real total and a stable order. */
    @Test void collections_are_paged_with_a_total_and_a_stable_order() {
        Fixture f = newFixture(true);
        for (int i = 0; i < 5; i++) capture(f,"PROVIDER-PAGE-"+i,new BigDecimal("20"),1100 + i);

        var first = fuel.transactions(f.site(),null,null,null,null,null,null,null,
                new FuelRepository.Paging(0,2,"occurredAt,desc"),f.manager());
        assertThat(first.totalElements()).isEqualTo(5);
        assertThat(first.totalPages()).isEqualTo(3);
        assertThat(first.content()).hasSize(2);
        assertThat(first.sort()).isEqualTo("occurredAt: DESC");

        var second = fuel.transactions(f.site(),null,null,null,null,null,null,null,
                new FuelRepository.Paging(1,2,"occurredAt,desc"),f.manager());
        // A page boundary must not repeat a record, which is what the id tiebreak is there for.
        assertThat(second.content()).extracting(FuelTransaction::id)
                .doesNotContainAnyElementsOf(first.content().stream().map(FuelTransaction::id).toList());

        // Beyond the last page is empty, not an error.
        assertThat(fuel.transactions(f.site(),null,null,null,null,null,null,null,page(9,2),f.manager()).content())
                .isEmpty();
    }

    /** Gap 4: the requested size cannot be used to pull the whole table in one call. */
    @Test void page_size_is_capped() {
        assertThat(new FuelRepository.Paging(0, 100_000, null).size())
                .isEqualTo(FuelRepository.Paging.MAX_SIZE);
    }

    /** Gap 5: the anomaly queue filters that used to be applied client-side over a capped window. */
    @Test void anomaly_queue_filters_server_side() {
        Fixture f = newFixture(true);
        var tx = capture(f,"PROVIDER-FILTER",new BigDecimal("70"),1100);
        fuel.reconcile(tx.id(),f.manager(),SourceChannel.WEB);

        var open = fuel.anomalies(f.site(),null,null,null,null,null,null,Boolean.TRUE,null,null,page(0,50),f.manager());
        assertThat(open.totalElements()).isGreaterThanOrEqualTo(1);

        var limitOnly = fuel.anomalies(f.site(),null,FuelAnomalyCase.Type.LIMIT_EXCEEDED,null,null,null,null,null,null,
                null,page(0,50),f.manager());
        assertThat(limitOnly.content()).isNotEmpty()
                .allSatisfy(a -> assertThat(a.type()).isEqualTo(FuelAnomalyCase.Type.LIMIT_EXCEEDED));

        var unassigned = fuel.anomalies(f.site(),null,null,null,null,Boolean.TRUE,null,Boolean.TRUE,null,null,
                page(0,50),f.manager());
        assertThat(unassigned.content()).allSatisfy(a -> assertThat(a.assignee()).isNull());

        // `dueBefore` was reachable only from the sweep scheduler; it is what makes an SLA queue real.
        var breaching = fuel.anomalies(f.site(),null,null,null,null,null,null,Boolean.TRUE,
                Instant.now().plusSeconds(3600L*48),null,page(0,50),f.manager());
        assertThat(breaching.totalElements()).isGreaterThanOrEqualTo(1);

        var byTransaction = fuel.anomalies(f.site(),null,null,null,null,null,null,null,null,tx.id(),page(0,50),f.manager());
        assertThat(byTransaction.content()).allSatisfy(a -> assertThat(a.transactionId()).isEqualTo(tx.id()));

        // An assigned case leaves the unassigned view.
        var one = limitOnly.content().get(0);
        fuel.transitionAnomaly(one.id(),"assign","officer.a",null,f.manager(),SourceChannel.WEB);
        assertThat(fuel.anomalies(f.site(),null,null,null,"officer",null,null,null,null,null,page(0,50),f.manager())
                .content()).extracting(FuelAnomalyCase::id).contains(one.id());
    }

    /** Gap 6: indicators the dashboard did not publish, so the dashboard had to derive them. */
    @Test void dashboard_publishes_anomaly_logbook_and_import_indicators() {
        Fixture f = newFixture(true);
        var tx = capture(f,"PROVIDER-DASH",new BigDecimal("70"),1100);
        fuel.reconcile(tx.id(),f.manager(),SourceChannel.WEB);

        Instant now = Instant.now();
        var log = fuel.createLogbook(new FuelApplicationService.CreateLogbook(f.site(),f.driver().id(),
                f.vehicle().id(),null,LocalDate.now(),now,now.plusSeconds(3600),"HQ","Court",null,
                DriverLogbook.UseClassification.OFFICIAL,"Official delivery",null,1100,1150L,true,UUID.randomUUID(),
                f.manager(),SourceChannel.WEB));
        fuel.transitionLogbook(log.id(),"submit",null,f.manager(),SourceChannel.WEB);

        var dashboard = fuel.dashboard(f.site(),f.manager());
        assertThat(dashboard).containsKeys("openAnomalies","anomaliesBreachingSla","materialOpenAnomalies",
                "unassignedAnomalies","pendingLogbookReviews","draftLogbooks","awaitingReconciliation",
                "importBatches","importBatchesWithErrors","lastImportAt","stale");
        assertThat((Long) dashboard.get("openAnomalies")).isGreaterThanOrEqualTo(1L);
        assertThat(dashboard).containsEntry("pendingLogbookReviews",1L);
    }

    /** Gap 3: policy detail, which had no endpoint and had to be selected out of the site list. */
    @Test void a_policy_is_readable_by_id() {
        Fixture f = newFixture(true);
        var listed = fuel.policies(f.site(),null,false,page(0,25),f.manager()).content().get(0);
        assertThat(fuel.policy(listed.id(),f.manager()).id()).isEqualTo(listed.id());
    }

    /** Gap 3: `inForceOnly` is an interval test, not a status filter. */
    @Test void in_force_only_excludes_an_active_policy_whose_period_has_not_started() {
        Fixture f = newFixture(false);
        fuel.createPolicy(policy(f,"Future",Instant.now().plusSeconds(86_400),null,1));
        assertThat(fuel.policies(f.site(),null,false,page(0,25),f.manager()).totalElements()).isEqualTo(1);
        assertThat(fuel.policies(f.site(),null,true,page(0,25),f.manager()).totalElements()).isZero();
    }

    /**
     * Gap 10: the audit search that returned 500 on every call, and the fuel history built on it.
     *
     * <p>The filter combinations are exercised deliberately — the previous JPQL failed whatever was
     * supplied, because a bare {@code ? IS NULL} test left PostgreSQL unable to infer a type.
     */
    @Test void audit_search_runs_and_fuel_records_expose_their_history() {
        Fixture f = newFixture(true);
        var tx = capture(f,"PROVIDER-AUDIT",new BigDecimal("20"),1100);
        fuel.reconcile(tx.id(),f.manager(),SourceChannel.WEB);

        assertThat(audit.search(new AuditPort.AuditQuery(java.util.List.of(),null,null,null,null,null,null,0,10)))
                .isNotNull();
        assertThat(audit.search(new AuditPort.AuditQuery(java.util.List.of(f.site()),"FuelTransaction",null,null,null,
                null,null,0,10))).isNotNull();
        assertThat(audit.search(new AuditPort.AuditQuery(java.util.List.of(f.site()),null,null,null,
                gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.AuditAction.CREATE,null,null,0,10))).isNotNull();

        var history = fuel.history("FuelTransaction",tx.id(),f.manager());
        assertThat(history).isNotEmpty()
                .allSatisfy(event -> assertThat(event.resourceId()).isEqualTo(tx.id().toString()));
        // Capture then reconcile: a create and a state transition, both on the chain.
        assertThat(history).extracting(e -> e.action().name()).contains("CREATE","STATE_TRANSITION");
    }

    /** Gap 10: history is authorised against the record, so it cannot be used to read another site. */
    @Test void history_is_refused_for_a_record_outside_the_actors_scope() {
        Fixture f = newFixture(true);
        var tx = capture(f,"PROVIDER-SCOPE",new BigDecimal("20"),1100);
        ActorContext outsider = new ActorContext(new SiteScopedPrincipal("other.manager","Other Manager",
                Set.of(SflRole.FLEET_MANAGER),Set.of("SOMEWHERE-ELSE"),false),"fuel-gap-e2e");
        assertThatThrownBy(() -> fuel.history("FuelTransaction",tx.id(),outsider))
                .isInstanceOf(gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.FleetAuthorizationException.class);
    }
}
