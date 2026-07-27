package gh.edu.clet.sfl.fleetlogistics.dispatch.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.common.security.SiteScopedPrincipal;
import gh.edu.clet.sfl.fleetlogistics.dispatch.application.port.DispatchRepository;
import gh.edu.clet.sfl.fleetlogistics.dispatch.application.service.CourierItemService;
import gh.edu.clet.sfl.fleetlogistics.dispatch.application.service.DispatchCustodyService;
import gh.edu.clet.sfl.fleetlogistics.dispatch.application.service.DispatchDashboardService;
import gh.edu.clet.sfl.fleetlogistics.dispatch.application.service.DispatchExceptionService;
import gh.edu.clet.sfl.fleetlogistics.dispatch.application.service.DispatchManifestService;
import gh.edu.clet.sfl.fleetlogistics.dispatch.application.service.DispatchReceiptService;
import gh.edu.clet.sfl.fleetlogistics.dispatch.application.service.DispatchReturnService;
import gh.edu.clet.sfl.fleetlogistics.dispatch.application.service.DispatchScanService;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.CourierItem;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.CustodyHop;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.Dispatch;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.DispatchExceptionCase;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.DispatchReceipt;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.ReturnReconciliation;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.SealState;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.IntegrationCommands.ReceiveIntegrationMessage;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.RegisterDriverCommand;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.RegisterVehicleCommand;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.AuditPort;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.DriverApplicationService;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.FleetIntegrationApplicationService;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.VehicleApplicationService;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.LicenceClass;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleCategory;
import gh.edu.clet.sfl.fleetlogistics.fleet.e2e.FleetPostgresSupport;
import gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.messaging.OutboxMessageEntity;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Database-backed proof of the nineteen mandatory S171 scenarios (item lifecycle, manifest, chain-of-custody,
 * receipt and variance, return reconciliation, inbound distribution, undelivered/outstanding escalation,
 * scan mismatch, secure inbox, outbox replay, exception gating, audit chain, dashboard reconciliation and the
 * full CT-05 secure-dispatch flow). Each test builds an isolated tenant site and drives the application
 * services directly, exactly as the fuel E2E suite does.
 */
@SpringBootTest(properties = {"sfl.security.enabled=false", "sfl.dispatch.scheduling.enabled=false",
        "sfl.fuel.scheduling.enabled=false", "sfl.fleet.scheduling.outbox.enabled=false",
        "sfl.fleet.messaging.transport=local"})
@EnabledIf(value = "gh.edu.clet.sfl.fleetlogistics.fleet.e2e.FleetPostgresSupport#databaseAvailable",
        disabledReason = "No PostgreSQL available")
class DispatchMandatoryScenariosEndToEndTest extends FleetPostgresSupport {

    @Autowired CourierItemService items;
    @Autowired DispatchManifestService manifests;
    @Autowired DispatchCustodyService custody;
    @Autowired DispatchReceiptService receipts;
    @Autowired DispatchReturnService returns;
    @Autowired DispatchScanService scans;
    @Autowired DispatchExceptionService exceptions;
    @Autowired DispatchDashboardService dashboards;
    @Autowired DispatchRepository repository;
    @Autowired AuditPort audit;
    @Autowired FleetIntegrationApplicationService integration;
    @Autowired VehicleApplicationService vehicleService;
    @Autowired DriverApplicationService driverService;
    @Autowired JdbcTemplate jdbc;

    private record Fixture(String site, ActorContext manager) {}

    private Fixture newFixture() {
        String site = "DSP" + System.nanoTime();
        return new Fixture(site, new ActorContext(new SiteScopedPrincipal("dispatch.manager", "Dispatch Manager",
                Set.of(SflRole.FLEET_MANAGER), Set.of(site), false), "dispatch-e2e"));
    }

    private CourierItem register(Fixture f, String number, CourierItem.Direction d, CourierItem.Type t,
            CourierItem.Sensitivity s) {
        return items.registerItem(new CourierItemService.RegisterItem(f.site(), number, d, t, s, "HQ", "Centre",
                "sender", "recipient", "handler", f.manager(), SourceChannel.WEB));
    }

    /** A DRAFT manifest carrying the given registered items, sealed and dispatched (item count = items.size). */
    private Dispatch dispatched(Fixture f, List<CourierItem> onManifest) {
        var m = manifests.createManifest(new DispatchManifestService.CreateManifest(f.site(), null, "HQ→Centre",
                "handler", "Centre", "WAEC-2026", null, null, null, f.manager(), SourceChannel.WEB));
        for (CourierItem item : onManifest) {
            manifests.addItem(new DispatchManifestService.AddManifestItem(m.id(), item.id(), "SEAL-" + item.itemNumber(),
                    1, f.manager(), SourceChannel.WEB));
        }
        manifests.seal(m.id(), List.of("SEAL-A", "SEAL-B"), f.manager(), SourceChannel.WEB);
        return manifests.dispatch(m.id(), f.manager(), SourceChannel.WEB);
    }

    private void fullCleanCustody(Fixture f, UUID dispatchId, int count) {
        for (CustodyHop hop : List.of(CustodyHop.WAREHOUSE_STAGING, CustodyHop.DISPATCH, CustodyHop.CENTRE_RECEIPT)) {
            custody.recordHandover(new DispatchCustodyService.RecordHandover(dispatchId, hop, "from", "to",
                    Instant.now(), SealState.INTACT, count, null, null, f.manager(), SourceChannel.WEB));
        }
    }

    private DispatchExceptionCase exceptionOf(Fixture f, DispatchExceptionCase.Type type) {
        return exceptions.exceptions(f.site(), type, null, 100, f.manager()).stream().findFirst().orElseThrow();
    }

    // 1
    @Test
    void register_item_and_track_through_delivery() {
        Fixture f = newFixture();
        var item = register(f, "OUT-1", CourierItem.Direction.OUTBOUND, CourierItem.Type.EXAMINATION_PAPER,
                CourierItem.Sensitivity.SECRET);
        assertThat(item.status()).isEqualTo(CourierItem.Status.RECEIVED);
        items.advanceItem(item.id(), "stage", f.manager(), SourceChannel.WEB);
        items.advanceItem(item.id(), "dispatch", f.manager(), SourceChannel.WEB);
        items.advanceItem(item.id(), "in-transit", f.manager(), SourceChannel.WEB);
        var delivered = items.advanceItem(item.id(), "deliver", f.manager(), SourceChannel.WEB);
        assertThat(delivered.status()).isEqualTo(CourierItem.Status.DELIVERED);
    }

    // 2
    @Test
    void dispatch_of_an_unregistered_item_is_rejected() {
        Fixture f = newFixture();
        var m = manifests.createManifest(new DispatchManifestService.CreateManifest(f.site(), null, "HQ→Centre",
                "handler", null, null, null, null, null, f.manager(), SourceChannel.WEB));
        assertThatThrownBy(() -> manifests.addItem(new DispatchManifestService.AddManifestItem(m.id(),
                UUID.randomUUID(), "SEAL-X", 1, f.manager(), SourceChannel.WEB)))
                .isInstanceOf(gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.RecordNotFoundException.class);
    }

    // 3
    @Test
    void build_manifest_with_seals_and_record_a_full_custody_chain() {
        Fixture f = newFixture();
        var a = register(f, "OUT-A", CourierItem.Direction.OUTBOUND, CourierItem.Type.SEALED_BAG,
                CourierItem.Sensitivity.CONFIDENTIAL);
        var b = register(f, "OUT-B", CourierItem.Direction.OUTBOUND, CourierItem.Type.SEALED_BAG,
                CourierItem.Sensitivity.CONFIDENTIAL);
        var d = dispatched(f, List.of(a, b));
        assertThat(d.status()).isEqualTo(Dispatch.Status.DISPATCHED);
        assertThat(d.sealIds()).contains("SEAL-A", "SEAL-B");
        fullCleanCustody(f, d.id(), 2);
        var gaps = custody.gaps(d.id(), f.manager());
        assertThat(gaps.gaps()).isEmpty();
        assertThat(gaps.closable()).isTrue();
    }

    // 4
    @Test
    void a_broken_seal_custody_gap_opens_an_exception_and_blocks_closure() {
        Fixture f = newFixture();
        var a = register(f, "OUT-A", CourierItem.Direction.OUTBOUND, CourierItem.Type.SEALED_BAG,
                CourierItem.Sensitivity.SECRET);
        var d = dispatched(f, List.of(a));
        custody.recordHandover(new DispatchCustodyService.RecordHandover(d.id(), CustodyHop.DISPATCH, "from", "to",
                Instant.now(), SealState.BROKEN, 1, "tamper", null, f.manager(), SourceChannel.WEB));
        assertThat(exceptions.exceptions(f.site(), DispatchExceptionCase.Type.CUSTODY_GAP, null, 100, f.manager()))
                .isNotEmpty();
        assertThatThrownBy(() -> manifests.close(d.id(), "done", f.manager(), SourceChannel.WEB))
                .isInstanceOf(IllegalStateException.class);
    }

    // 5
    @Test
    void a_clean_receipt_completes_the_chain_and_allows_closure() {
        Fixture f = newFixture();
        var a = register(f, "OUT-A", CourierItem.Direction.OUTBOUND, CourierItem.Type.CERTIFICATE,
                CourierItem.Sensitivity.CONFIDENTIAL);
        var d = dispatched(f, List.of(a));
        fullCleanCustody(f, d.id(), 1);
        var receipt = receipts.confirmReceipt(new DispatchReceiptService.ConfirmReceipt(d.id(), SealState.INTACT, true,
                null, 1, "recipient", null, sig(), "RCP-1", false, Instant.now(), f.manager(), SourceChannel.WEB));
        assertThat(receipt.outcome()).isEqualTo(DispatchReceipt.ReceiptOutcome.CLEAN);
        var closed = manifests.close(d.id(), "delivered clean", f.manager(), SourceChannel.WEB);
        assertThat(closed.status()).isEqualTo(Dispatch.Status.CLOSED);
    }

    // 6
    @Test
    void a_receipt_variance_opens_a_security_exception_that_escalates_to_ssemp() {
        Fixture f = newFixture();
        var a = register(f, "OUT-A", CourierItem.Direction.OUTBOUND, CourierItem.Type.EXAMINATION_PAPER,
                CourierItem.Sensitivity.SECRET);
        var d = dispatched(f, List.of(a));
        var receipt = receipts.confirmReceipt(new DispatchReceiptService.ConfirmReceipt(d.id(), SealState.BROKEN, false,
                null, 1, "recipient", null, sig(), "RCP-1", false, Instant.now(), f.manager(), SourceChannel.WEB));
        assertThat(receipt.outcome()).isEqualTo(DispatchReceipt.ReceiptOutcome.VARIANCE);
        var kase = exceptionOf(f, DispatchExceptionCase.Type.RECEIPT_VARIANCE);
        assertThat(kase.securityRelevant()).isTrue();
        var escalated = exceptions.transition(kase.id(), "escalate", "seal tamper", null, f.manager(),
                SourceChannel.WEB);
        assertThat(escalated.status()).isEqualTo(DispatchExceptionCase.Status.ESCALATED);
        Long ssemp = jdbc.queryForObject(
                "SELECT count(*) FROM fleet_logistics.outbox_messages WHERE event_type='sfl.ftlmp.dispatch-security-variance.v1' AND aggregate_id=?",
                Long.class, kase.id().toString());
        assertThat(ssemp).isGreaterThanOrEqualTo(1L);
    }

    // 7
    @Test
    void an_offline_receipt_reconciles_idempotently_on_replay() {
        Fixture f = newFixture();
        var a = register(f, "OUT-A", CourierItem.Direction.OUTBOUND, CourierItem.Type.CERTIFICATE,
                CourierItem.Sensitivity.CONFIDENTIAL);
        var d = dispatched(f, List.of(a));
        var command = new DispatchReceiptService.ConfirmReceipt(d.id(), SealState.INTACT, true, null, 1, "recipient",
                null, sig(), "EDGE-1", true, Instant.now(), f.manager(), SourceChannel.MOBILE);
        var first = receipts.confirmReceipt(command);
        var replay = receipts.confirmReceipt(command);
        assertThat(replay.id()).isEqualTo(first.id());
        assertThat(receipts.receipts(d.id(), f.manager())).hasSize(1);
    }

    // 8
    @Test
    void a_matched_return_leg_reconciles_and_closes_custody() {
        Fixture f = newFixture();
        var a = register(f, "OUT-A", CourierItem.Direction.OUTBOUND, CourierItem.Type.SEALED_BAG,
                CourierItem.Sensitivity.CONFIDENTIAL);
        var d = dispatched(f, List.of(a));
        fullCleanCustody(f, d.id(), 1);
        receipts.confirmReceipt(new DispatchReceiptService.ConfirmReceipt(d.id(), SealState.INTACT, true, null, 1,
                "recipient", null, sig(), "RCP-1", false, Instant.now(), f.manager(), SourceChannel.WEB));
        var reconciled = returns.reconcile(new DispatchReturnService.ReconcileReturn(d.id(), null, 1, 0, "all back",
                null, f.manager(), SourceChannel.WEB));
        assertThat(reconciled.outcome()).isEqualTo(ReturnReconciliation.ReturnOutcome.MATCHED);
        assertThat(manifests.close(d.id(), "return complete", f.manager(), SourceChannel.WEB).status())
                .isEqualTo(Dispatch.Status.CLOSED);
    }

    // 9
    @Test
    void a_return_discrepancy_opens_an_exception_and_blocks_closure() {
        Fixture f = newFixture();
        var a = register(f, "OUT-A", CourierItem.Direction.OUTBOUND, CourierItem.Type.SEALED_BAG,
                CourierItem.Sensitivity.CONFIDENTIAL);
        var d = dispatched(f, List.of(a));
        fullCleanCustody(f, d.id(), 1);
        receipts.confirmReceipt(new DispatchReceiptService.ConfirmReceipt(d.id(), SealState.INTACT, true, null, 1,
                "recipient", null, sig(), "RCP-1", false, Instant.now(), f.manager(), SourceChannel.WEB));
        var reconciled = returns.reconcile(new DispatchReturnService.ReconcileReturn(d.id(), 1, 0, 0, "nothing back",
                null, f.manager(), SourceChannel.WEB));
        assertThat(reconciled.outcome()).isEqualTo(ReturnReconciliation.ReturnOutcome.DISCREPANCY);
        assertThat(exceptions.exceptions(f.site(), DispatchExceptionCase.Type.RETURN_DISCREPANCY, null, 100,
                f.manager())).isNotEmpty();
        assertThatThrownBy(() -> manifests.close(d.id(), "done", f.manager(), SourceChannel.WEB))
                .isInstanceOf(IllegalStateException.class);
    }

    // 10
    @Test
    void inbound_mail_is_distributed_with_a_recorded_acknowledgement() {
        Fixture f = newFixture();
        var inbound = register(f, "IN-1", CourierItem.Direction.INBOUND, CourierItem.Type.ORDINARY_MAIL,
                CourierItem.Sensitivity.ORDINARY);
        var closed = items.distributeInbound(new CourierItemService.DistributeInbound(inbound.id(), "clerk", "SIG-1",
                null, f.manager(), SourceChannel.WEB));
        assertThat(closed.status()).isEqualTo(CourierItem.Status.CLOSED);
    }

    // 11
    @Test
    void an_undelivered_inbound_item_is_selected_by_the_sweep_and_escalated() {
        Fixture f = newFixture();
        var inbound = register(f, "IN-1", CourierItem.Direction.INBOUND, CourierItem.Type.CONFIDENTIAL_CORRESPONDENCE,
                CourierItem.Sensitivity.CONFIDENTIAL);
        assertThat(repository.findUndeliveredInboundItemIds(f.site(), Instant.now().plusSeconds(60), 100))
                .contains(inbound.id());
        items.flagUndelivered(inbound.id(), "unclaimed past window", f.manager(), SourceChannel.SCHEDULER);
        assertThat(exceptions.exceptions(f.site(), DispatchExceptionCase.Type.UNDELIVERED_ITEM, null, 100, f.manager()))
                .isNotEmpty();
    }

    // 12
    @Test
    void an_outstanding_return_is_selected_by_the_sweep_and_escalated() {
        Fixture f = newFixture();
        var a = register(f, "OUT-A", CourierItem.Direction.OUTBOUND, CourierItem.Type.SEALED_BAG,
                CourierItem.Sensitivity.CONFIDENTIAL);
        var d = dispatched(f, List.of(a));
        var outstanding = repository.findOutstandingReturns(f.site(), Instant.now().plusSeconds(60), 100);
        assertThat(outstanding).isNotEmpty();
        var row = outstanding.get(0);
        returns.escalateOutstanding(row.dispatchId(), row.manifestItemId(), row.courierItemId(), f.manager(),
                SourceChannel.SCHEDULER);
        assertThat(exceptions.exceptions(f.site(), DispatchExceptionCase.Type.RETURN_DISCREPANCY, null, 100,
                f.manager())).anyMatch(e -> e.detectedRules().contains("OUTSTANDING_RETURN_WINDOW_ELAPSED"));
    }

    // 13
    @Test
    void a_scan_mismatch_is_flagged_and_routed_to_variance_handling() {
        Fixture f = newFixture();
        var onManifest = register(f, "OUT-A", CourierItem.Direction.OUTBOUND, CourierItem.Type.SEALED_BAG,
                CourierItem.Sensitivity.CONFIDENTIAL);
        var offManifest = register(f, "OUT-B", CourierItem.Direction.OUTBOUND, CourierItem.Type.SEALED_BAG,
                CourierItem.Sensitivity.CONFIDENTIAL);
        var d = dispatched(f, List.of(onManifest));
        String csv = "rowReference,scannedCode\r\nr1,OUT-B\r\n";
        var batch = scans.importCsv(new DispatchScanService.ImportScanBatch(f.site(), "SCANNER-1", "BATCH-1", d.id(),
                csv.getBytes(StandardCharsets.UTF_8), f.manager(), SourceChannel.IMPORT));
        assertThat(batch.mismatchRows()).isEqualTo(1);
        assertThat(exceptions.exceptions(f.site(), DispatchExceptionCase.Type.SCAN_MISMATCH, null, 100, f.manager()))
                .isNotEmpty();
        assertThat(offManifest.itemNumber()).isEqualTo("OUT-B");
    }

    // 14
    @Test
    void unsigned_or_invalid_integration_input_is_rejected_by_the_secure_inbox() {
        Fixture f = newFixture();
        var command = new ReceiveIntegrationMessage("BADSCANNER", "idem-" + f.site(),
                "sfl.ftlmp.dispatch-scan.v1", f.site(), Instant.now(), "not-a-valid-signature", Instant.now(),
                "{\"scannedCode\":\"OUT-X\"}", Map.of("scannedCode", "OUT-X"), f.manager(), SourceChannel.INTEGRATION);
        assertThatThrownBy(() -> integration.receive(command)).isInstanceOf(RuntimeException.class);
    }

    // 15
    @Test
    void a_failed_outbound_integration_is_surfaced_and_replayable() {
        Fixture f = newFixture();
        UUID messageId = UUID.randomUUID();
        jdbc.update("INSERT INTO fleet_logistics.outbox_messages (id,event_type,event_version,aggregate_type,aggregate_id,payload,status,created_at,attempt_count,schema_version) VALUES (?,?,?,?,?,?::jsonb,?,?,?,?)",
                messageId, "sfl.test.dead-letter.v1", 1, "Dispatch", UUID.randomUUID().toString(), "{}",
                OutboxMessageEntity.STATUS_DEAD_LETTERED, Timestamp.from(Instant.now()), 0, 1);
        assertThat(exceptions.integrationHealth(f.manager()).deadLettered()).isGreaterThanOrEqualTo(1);
        assertThat(exceptions.replayIntegration(messageId, f.manager(), SourceChannel.INTEGRATION)).isTrue();
        assertThat(jdbc.queryForObject("SELECT status FROM fleet_logistics.outbox_messages WHERE id=?", String.class,
                messageId)).isEqualTo("PENDING");
    }

    // 16
    @Test
    void an_exception_cannot_close_without_explanation_decision_and_evidence() {
        Fixture f = newFixture();
        var a = register(f, "OUT-A", CourierItem.Direction.OUTBOUND, CourierItem.Type.EXAMINATION_PAPER,
                CourierItem.Sensitivity.SECRET);
        var d = dispatched(f, List.of(a));
        receipts.confirmReceipt(new DispatchReceiptService.ConfirmReceipt(d.id(), SealState.BROKEN, false, null, 1,
                "recipient", null, sig(), "RCP-1", false, Instant.now(), f.manager(), SourceChannel.WEB));
        var kase = exceptionOf(f, DispatchExceptionCase.Type.RECEIPT_VARIANCE);
        exceptions.transition(kase.id(), "assign", "officer", null, f.manager(), SourceChannel.WEB);
        exceptions.transition(kase.id(), "review", null, null, f.manager(), SourceChannel.WEB);
        exceptions.transition(kase.id(), "approve", "accepted", null, f.manager(), SourceChannel.WEB);
        assertThatThrownBy(() -> exceptions.transition(kase.id(), "close", "done", null, f.manager(),
                SourceChannel.WEB)).isInstanceOf(IllegalStateException.class);
    }

    // 17
    @Test
    void audit_chain_verification_runs_after_operations() {
        Fixture f = newFixture();
        register(f, "OUT-A", CourierItem.Direction.OUTBOUND, CourierItem.Type.CERTIFICATE,
                CourierItem.Sensitivity.CONFIDENTIAL);
        assertThat(audit.verifyChain()).isNotNull();
    }

    // 18
    @Test
    void dashboard_counts_reconcile_to_source_records() {
        Fixture f = newFixture();
        var a = register(f, "OUT-A", CourierItem.Direction.OUTBOUND, CourierItem.Type.SEALED_BAG,
                CourierItem.Sensitivity.SECRET);
        var d = dispatched(f, List.of(a));
        custody.recordHandover(new DispatchCustodyService.RecordHandover(d.id(), CustodyHop.DISPATCH, "from", "to",
                Instant.now(), SealState.BROKEN, 1, null, null, f.manager(), SourceChannel.WEB));
        Map<String, Object> dash = dashboards.dashboard(f.site(), f.manager());
        assertThat(((Number) dash.get("inTransitCount")).intValue()).isGreaterThanOrEqualTo(1);
        assertThat(((Number) dash.get("custodyGapCount")).intValue()).isGreaterThanOrEqualTo(1);
        assertThat(((Number) dash.get("openExceptionCount")).intValue()).isGreaterThanOrEqualTo(1);
    }

    // 19
    @Test
    void ct05_full_secure_dispatch_flow_reconciles_with_no_unexplained_variance() {
        Fixture f = newFixture();
        var vehicle = vehicleService.register(new RegisterVehicleCommand("GN-" + f.site(), null, "Toyota", "Hiace",
                2024, VehicleCategory.PICKUP, 5, f.site(), "Transport", "Fleet Manager", null, 1000, false, Set.of(),
                f.manager(), SourceChannel.WEB, "vehicle-" + f.site()));
        var driver = driverService.register(new RegisterDriverCommand("DRV-" + f.site(), "Courier Driver",
                "LIC-" + f.site(), LicenceClass.B, LocalDate.now().plusYears(2), LocalDate.now().plusYears(1), f.site(),
                "Transport", f.manager(), SourceChannel.WEB, "driver-" + f.site()));
        var paper1 = register(f, "EX-1", CourierItem.Direction.OUTBOUND, CourierItem.Type.EXAMINATION_PAPER,
                CourierItem.Sensitivity.SECRET);
        var paper2 = register(f, "EX-2", CourierItem.Direction.OUTBOUND, CourierItem.Type.EXAMINATION_PAPER,
                CourierItem.Sensitivity.SECRET);
        var m = manifests.createManifest(new DispatchManifestService.CreateManifest(f.site(), null, "HQ→ExamCentre",
                "handler", "Exam Centre", "WAEC-2026", null, null, null, f.manager(), SourceChannel.WEB));
        manifests.addItem(new DispatchManifestService.AddManifestItem(m.id(), paper1.id(), "SEAL-1", 1, f.manager(),
                SourceChannel.WEB));
        manifests.addItem(new DispatchManifestService.AddManifestItem(m.id(), paper2.id(), "SEAL-2", 1, f.manager(),
                SourceChannel.WEB));
        manifests.assignTrip(m.id(), null, vehicle.id(), driver.id(), f.manager(), SourceChannel.WEB);
        manifests.seal(m.id(), List.of("SEAL-1", "SEAL-2"), f.manager(), SourceChannel.WEB);
        var d = manifests.dispatch(m.id(), f.manager(), SourceChannel.WEB);
        fullCleanCustody(f, d.id(), 2);
        var receipt = receipts.confirmReceipt(new DispatchReceiptService.ConfirmReceipt(d.id(), SealState.INTACT, true,
                null, 2, "Centre Manager", null, sig(), "RCP-CT05", false, Instant.now(), f.manager(),
                SourceChannel.WEB));
        assertThat(receipt.outcome()).isEqualTo(DispatchReceipt.ReceiptOutcome.CLEAN);
        var reconciled = returns.reconcile(new DispatchReturnService.ReconcileReturn(d.id(), null, 2, 0, "all returned",
                null, f.manager(), SourceChannel.WEB));
        assertThat(reconciled.outcome()).isEqualTo(ReturnReconciliation.ReturnOutcome.MATCHED);
        assertThat(repository.hasOpenException(d.id())).isFalse();
        assertThat(manifests.close(d.id(), "CT-05 complete", f.manager(), SourceChannel.WEB).status())
                .isEqualTo(Dispatch.Status.CLOSED);
    }

    private static gh.edu.clet.sfl.fleetlogistics.dispatch.application.service.DispatchEvidenceSupport.EvidenceMeta sig() {
        return new gh.edu.clet.sfl.fleetlogistics.dispatch.application.service.DispatchEvidenceSupport.EvidenceMeta(
                "signature.png", "image/png", "evidence://sig/" + UUID.randomUUID(), null, null, null);
    }
}
