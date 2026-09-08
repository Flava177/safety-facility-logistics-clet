package gh.edu.clet.sfl.fleetlogistics.dispatch.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.CourierItem;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.Dispatch;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.DispatchManifestItem;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.ScanImportBatch;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.ScanImportRow;
import gh.edu.clet.sfl.fleetlogistics.dispatch.support.DispatchTestDoubles;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.FleetAuthorizationException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.RecordMetadata;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import gh.edu.clet.sfl.fleetlogistics.fleet.support.FleetTestDoubles;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Traces: SRS-SFL-S171-04 optional scanner/barcode ingestion, and the audit's items 4 (N+1 manifest
 * lookup) and 5 (unbatched per-row inserts) - both fixed in {@link DispatchScanService}; these tests
 * are the regression coverage the audit found missing.
 */
class DispatchScanServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-21T08:00:00Z");
    private static final String SITE = "ACCRA";

    private DispatchTestDoubles.InMemoryDispatchRepository repository;
    private DispatchScanService service;
    private UUID dispatchId;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        repository = new DispatchTestDoubles.InMemoryDispatchRepository();
        var exceptionService = new DispatchExceptionService(repository, new DispatchAccessPolicy(),
                new FleetTestDoubles.RecordingAuditPort(clock), new FleetTestDoubles.RecordingEventPublisher(),
                new FleetTestDoubles.RecordingNotificationPort(), new FleetTestDoubles.FixedRuntimeConfiguration(),
                new DispatchTestDoubles.RecordingSecurityVisibilityPort(), new DispatchTestDoubles.StubOutboxAdminPort(),
                clock);
        service = new DispatchScanService(repository, new DispatchAccessPolicy(), exceptionService,
                new FleetTestDoubles.RecordingAuditPort(clock), new FleetTestDoubles.RecordingEventPublisher(), clock);
        dispatchId = seedDispatchWithManifestItems("ITM-1", "ITM-2", "ITM-3");
    }

    @Test
    @DisplayName("importing a CSV against a multi-item manifest resolves the manifest in one batch lookup, not one per item")
    void importCsv_resolves_manifest_items_with_a_single_batch_lookup() {
        String csv = "rowReference,scannedCode\r\nr1,ITM-1\r\nr2,ITM-2\r\nr3,ITM-3\r\n";

        var batch = service.importCsv(importCommand(csv));

        assertThat(batch.acceptedRows()).isEqualTo(3);
        assertThat(batch.mismatchRows()).isEqualTo(0);
        // The N+1 this regresses against: one findManifestItems + one findItemsByIds for the whole
        // manifest, never one findItemsByIds call per line scanned.
        assertThat(repository.findManifestItemsCallCount()).isEqualTo(1);
        assertThat(repository.findItemsByIdsCallCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("importing a CSV writes every row with a single batch insert, not one INSERT per row")
    void importCsv_writes_rows_with_a_single_batch_insert() {
        String csv = "rowReference,scannedCode\r\nr1,ITM-1\r\nr2,ITM-2\r\nr3,ITM-3\r\n";

        var batch = service.importCsv(importCommand(csv));

        assertThat(repository.saveScanRowsCallCount()).isEqualTo(1);
        assertThat(repository.saveScanRowCallCount()).isEqualTo(0);
        assertThat(repository.findScanRows(batch.id())).hasSize(3);
    }

    @Test
    @DisplayName("importing a scan CSV is denied to an actor without DISPATCH_INTEGRATION_INGEST")
    void importCsv_is_denied_without_integration_ingest_permission() {
        String csv = "rowReference,scannedCode\r\nr1,ITM-1\r\n";
        var command = new DispatchScanService.ImportScanBatch(SITE, "SCANNER-1", null, dispatchId,
                csv.getBytes(StandardCharsets.UTF_8), DispatchTestDoubles.centreManager(SITE), SourceChannel.WEB);

        assertThatThrownBy(() -> service.importCsv(command)).isInstanceOf(FleetAuthorizationException.class);
    }

    @Test
    @DisplayName("a CSV with only a header and no data rows is refused")
    void importCsv_with_no_data_rows_is_refused() {
        String csv = "rowReference,scannedCode\r\n";

        assertThatThrownBy(() -> service.importCsv(importCommand(csv))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a scanned code that is not on the manifest is a mismatch that opens a scan-mismatch exception")
    void importCsv_off_manifest_code_opens_a_scan_mismatch_exception() {
        registerUnmanifestedItem("ITM-OTHER");
        String csv = "rowReference,scannedCode\r\nr1,ITM-OTHER\r\n";

        var batch = service.importCsv(importCommand(csv));

        assertThat(batch.status()).isEqualTo(ScanImportBatch.Status.FAILED);
        assertThat(repository.hasOpenException(dispatchId)).isTrue();
        assertThat(repository.findScanRows(batch.id()).get(0).outcome()).isEqualTo(ScanImportRow.Outcome.MISMATCH);
    }

    @Test
    @DisplayName("a single provider scan event is classified and persisted the same way an imported row is")
    void recordScanEvent_classifies_and_persists_a_single_event() {
        var row = service.recordScanEvent(SITE, dispatchId, "SCANNER-1", "r1", "ITM-1",
                DispatchTestDoubles.dispatchManager(SITE), SourceChannel.INTEGRATION);

        assertThat(row.outcome()).isEqualTo(ScanImportRow.Outcome.MATCHED);
    }

    private DispatchScanService.ImportScanBatch importCommand(String csv) {
        return new DispatchScanService.ImportScanBatch(SITE, "SCANNER-1", null, dispatchId,
                csv.getBytes(StandardCharsets.UTF_8), DispatchTestDoubles.dispatchManager(SITE), SourceChannel.WEB);
    }

    private void registerUnmanifestedItem(String itemNumber) {
        repository.saveItem(new CourierItem(UUID.randomUUID(), itemNumber, SiteCode.of(SITE),
                CourierItem.Direction.OUTBOUND, CourierItem.Type.ORDINARY_MAIL, CourierItem.Sensitivity.ORDINARY,
                false, "Warehouse", "Centre 1", "Registry", "Centre Manager", null, CourierItem.Status.RECEIVED, null,
                null, null, null, null, false, null,
                RecordMetadata.createdBy("clerk", NOW, SourceChannel.WEB, "corr-test")));
    }

    private UUID seedDispatchWithManifestItems(String... itemNumbers) {
        UUID id = UUID.randomUUID();
        var dispatch = new Dispatch(id, "DSP-1", SiteCode.of(SITE), "Route 1", "handler-1", "Centre 1", null, null,
                null, null, itemNumbers.length, List.of("SEAL-1"), Dispatch.Status.SEALED, null, null, null, null,
                RecordMetadata.createdBy("clerk", NOW, SourceChannel.WEB, "corr-test"));
        repository.saveDispatch(dispatch);
        int sequence = 1;
        for (String itemNumber : itemNumbers) {
            UUID courierItemId = UUID.randomUUID();
            repository.saveItem(new CourierItem(courierItemId, itemNumber, SiteCode.of(SITE),
                    CourierItem.Direction.OUTBOUND, CourierItem.Type.ORDINARY_MAIL, CourierItem.Sensitivity.ORDINARY,
                    false, "Warehouse", "Centre 1", "Registry", "Centre Manager", null, CourierItem.Status.RECEIVED,
                    null, null, null, null, null, false, null,
                    RecordMetadata.createdBy("clerk", NOW, SourceChannel.WEB, "corr-test")));
            repository.saveManifestItem(new DispatchManifestItem(UUID.randomUUID(), id, courierItemId,
                    SiteCode.of(SITE), sequence++, "SEAL-1", 1, DispatchManifestItem.ReturnStatus.PENDING, null, null,
                    NOW));
        }
        return id;
    }
}
