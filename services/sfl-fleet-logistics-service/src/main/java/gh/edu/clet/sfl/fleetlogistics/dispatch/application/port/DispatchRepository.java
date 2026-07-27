package gh.edu.clet.sfl.fleetlogistics.dispatch.application.port;

import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.CourierItem;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.CustodyHandover;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.Dispatch;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.DispatchExceptionCase;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.DispatchManifestItem;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.DispatchReceipt;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.ReturnReconciliation;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.ScanImportBatch;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.ScanImportRow;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Persistence port for S171. It exposes domain records, never JDBC/JPA types. */
public interface DispatchRepository {

    // Courier items
    CourierItem saveItem(CourierItem item);
    Optional<CourierItem> findItem(UUID id);
    Optional<CourierItem> findItemByNumber(String siteCode, String itemNumber);
    List<CourierItem> findItems(List<String> sites, CourierItem.Direction direction, CourierItem.Status status,
            CourierItem.Sensitivity sensitivity, String handler, Instant from, Instant to, int limit);

    // Dispatch manifests
    Dispatch saveDispatch(Dispatch dispatch);
    Optional<Dispatch> findDispatch(UUID id);
    Optional<Dispatch> findDispatchByNumber(String siteCode, String manifestNumber);
    List<Dispatch> findDispatches(List<String> sites, Dispatch.Status status, String destinationCentre, UUID tripId,
            Instant from, Instant to, int limit);

    // Manifest items
    DispatchManifestItem saveManifestItem(DispatchManifestItem item);
    List<DispatchManifestItem> findManifestItems(UUID dispatchId);
    int nextManifestSequence(UUID dispatchId);

    // Custody handovers (append-only)
    CustodyHandover saveHandover(CustodyHandover handover);
    List<CustodyHandover> findHandovers(UUID dispatchId);
    int nextHandoverSequence(UUID dispatchId);

    // Destination receipts
    DispatchReceipt saveReceipt(DispatchReceipt receipt);
    Optional<DispatchReceipt> findReceipt(UUID id);
    Optional<DispatchReceipt> findReceiptByCapture(UUID dispatchId, String captureCorrelationId);
    List<DispatchReceipt> findReceipts(UUID dispatchId);

    // Return reconciliation
    ReturnReconciliation saveReturn(ReturnReconciliation reconciliation);
    Optional<ReturnReconciliation> findReturn(UUID id);
    List<ReturnReconciliation> findReturns(UUID dispatchId);

    // Exception cases
    DispatchExceptionCase saveException(DispatchExceptionCase exceptionCase);
    Optional<DispatchExceptionCase> findException(UUID id);
    Optional<DispatchExceptionCase> findExceptionByOccurrence(String siteCode, String occurrenceKey);
    List<DispatchExceptionCase> findExceptions(List<String> sites, DispatchExceptionCase.Type type,
            DispatchExceptionCase.Status status, Instant dueBefore, int limit);
    /** True when an open (non-closed, non-cancelled) exception exists for the dispatch. Used to block closure. */
    boolean hasOpenException(UUID dispatchId);
    void saveExceptionHistory(UUID caseId, String fromStatus, String toStatus, String action, String actor,
            String comment, Instant occurredAt, String correlationId);

    // Optional scan ingestion
    ScanImportBatch saveScanBatch(ScanImportBatch batch);
    Optional<ScanImportBatch> findScanBatch(UUID id);
    Optional<ScanImportBatch> findScanBatchByReference(String siteCode, String sourceSystem, String batchReference);
    ScanImportRow saveScanRow(ScanImportRow row);
    List<ScanImportRow> findScanRows(UUID batchId);

    // Dashboard read model
    Map<String, Object> dashboardCounts(List<String> sites, String site);
    void saveDashboardSnapshot(String scopeKey, String siteCode, Instant generatedAt, boolean stale,
            Map<String, Object> counts, Instant sourceUpdatedAt, String warnings);
    Optional<Map<String, Object>> latestDashboardSnapshot(String scopeKey);

    // Scheduled-sweep support
    List<UUID> findUndeliveredInboundItemIds(String siteCode, Instant olderThan, int limit);
    List<OutstandingReturn> findOutstandingReturns(String siteCode, Instant olderThan, int limit);
    List<String> activeSites();

    record OutstandingReturn(UUID dispatchId, UUID manifestItemId, UUID courierItemId) {}
}
