package gh.edu.clet.sfl.fleetlogistics.dispatch.application.port;

import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.CourierItem;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.CustodyHandover;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.CustodyHop;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.Dispatch;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.DispatchExceptionCase;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.DispatchManifestItem;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.DispatchReceipt;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.ReturnReconciliation;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.ScanImportBatch;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.ScanImportRow;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.SealState;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Persistence port for S171. It exposes domain records, never JDBC/JPA types. */
public interface DispatchRepository {

    /**
     * A page of dispatch records.
     *
     * <p>Shaped like the fleet {@code PageResponse} and the fuel {@code FuelPage} so all three
     * modules of this service page the same way from an operator's point of view, but declared here
     * rather than imported from the API layer — the port must not depend on a transport type.
     *
     * <p>{@code sort} is echoed back because the caller may have asked for a default: a client that
     * cannot see which ordering it got cannot tell a stable page from a shifting one.
     */
    record DispatchPage<T>(List<T> content, int page, int size, long totalElements, int totalPages, String sort) {
        public static <T> DispatchPage<T> of(List<T> content, int page, int size, long totalElements, String sort) {
            int pages = size <= 0 ? 0 : (int) Math.ceil((double) totalElements / size);
            return new DispatchPage<>(content, page, size, totalElements, pages, sort);
        }
        public static <T> DispatchPage<T> empty(int page, int size, String sort) {
            return new DispatchPage<>(List.of(), page, size, 0L, 0, sort);
        }
    }

    /**
     * Paging and ordering, normalised.
     *
     * <p>{@code sort} is a key from the resource's own allow-list, never raw SQL — the adapter maps
     * it to a column plus a deterministic tiebreak on {@code id}, because a page over rows that
     * share a sort value will otherwise skip or repeat records between requests.
     */
    record Paging(int page, int size, String sort) {
        public static final int MAX_SIZE = 200;
        public Paging {
            page = Math.max(page, 0);
            size = size <= 0 ? 25 : Math.min(size, MAX_SIZE);
        }
        public int offset() {
            return page * size;
        }
    }

    /** Filters {@code GET /api/v1/dispatch/items} accepts. Any field may be null. */
    record ItemQuery(List<String> sites, CourierItem.Direction direction, CourierItem.Status status,
            CourierItem.Sensitivity sensitivity, CourierItem.Type itemType, String handler, String reference,
            UUID dispatchId, Boolean undelivered, Instant from, Instant to, Paging paging) {
    }

    /** Filters {@code GET /api/v1/dispatch/manifests} accepts. */
    record DispatchQuery(List<String> sites, Dispatch.Status status, String destinationCentre, UUID tripId,
            String handler, Instant from, Instant to, Paging paging) {
    }

    /**
     * Filters {@code GET /api/v1/dispatch/exceptions} accepts.
     *
     * <p>{@code dueBefore} was reachable only from the sweep scheduler before; exposing it is what
     * makes a "breaching SLA" queue a server-side query rather than a client-side guess over
     * whatever window happened to come back.
     */
    record ExceptionQuery(List<String> sites, DispatchExceptionCase.Type type, DispatchExceptionCase.Status status,
            DispatchExceptionCase.Severity severity, String assignee, Boolean unassigned, Boolean securityRelevant,
            Boolean openOnly, Instant dueBefore, UUID dispatchId, UUID courierItemId, Paging paging) {
    }

    /** Filters {@code GET /api/v1/dispatch/scans/imports} accepts. */
    record ScanBatchQuery(List<String> sites, String sourceSystem, UUID dispatchId,
            ScanImportBatch.Status status, Paging paging) {
    }

    /** Filters the site-wide custody read. A {@code dispatchId} narrows it to one consignment. */
    record CustodyQuery(List<String> sites, UUID dispatchId, CustodyHop hop, String custodian, SealState sealState,
            Instant from, Instant to, Paging paging) {
    }

    /** Filters the site-wide receipt read. */
    record ReceiptQuery(List<String> sites, UUID dispatchId, DispatchReceipt.ReceiptOutcome outcome,
            DispatchReceipt.VarianceType varianceType, String recipient, Instant from, Instant to, Paging paging) {
    }

    // Courier items
    CourierItem saveItem(CourierItem item);
    Optional<CourierItem> findItem(UUID id);
    Optional<CourierItem> findItemByNumber(String siteCode, String itemNumber);
    DispatchPage<CourierItem> findItems(ItemQuery query);
    /** Resolves several items at once, for expanding manifest lines without a fetch per line. */
    List<CourierItem> findItemsByIds(List<UUID> ids);

    // Dispatch manifests
    Dispatch saveDispatch(Dispatch dispatch);
    Optional<Dispatch> findDispatch(UUID id);
    Optional<Dispatch> findDispatchByNumber(String siteCode, String manifestNumber);
    DispatchPage<Dispatch> findDispatches(DispatchQuery query);

    // Manifest items
    DispatchManifestItem saveManifestItem(DispatchManifestItem item);
    List<DispatchManifestItem> findManifestItems(UUID dispatchId);
    int nextManifestSequence(UUID dispatchId);

    // Custody handovers (append-only)
    CustodyHandover saveHandover(CustodyHandover handover);
    List<CustodyHandover> findHandovers(UUID dispatchId);
    /** Site-wide custody read. Closes gap 7 — before this, custody was readable per consignment only. */
    DispatchPage<CustodyHandover> findHandovers(CustodyQuery query);
    int nextHandoverSequence(UUID dispatchId);

    // Destination receipts
    DispatchReceipt saveReceipt(DispatchReceipt receipt);
    Optional<DispatchReceipt> findReceipt(UUID id);
    Optional<DispatchReceipt> findReceiptByCapture(UUID dispatchId, String captureCorrelationId);
    List<DispatchReceipt> findReceipts(UUID dispatchId);
    /** Site-wide receipt read. Closes gap 7. */
    DispatchPage<DispatchReceipt> findReceipts(ReceiptQuery query);

    // Return reconciliation
    ReturnReconciliation saveReturn(ReturnReconciliation reconciliation);
    Optional<ReturnReconciliation> findReturn(UUID id);
    List<ReturnReconciliation> findReturns(UUID dispatchId);

    // Exception cases
    DispatchExceptionCase saveException(DispatchExceptionCase exceptionCase);
    Optional<DispatchExceptionCase> findException(UUID id);
    Optional<DispatchExceptionCase> findExceptionByOccurrence(String siteCode, String occurrenceKey);
    DispatchPage<DispatchExceptionCase> findExceptions(ExceptionQuery query);
    /** True when an open (non-closed, non-cancelled) exception exists for the dispatch. Used to block closure. */
    boolean hasOpenException(UUID dispatchId);
    void saveExceptionHistory(UUID caseId, String fromStatus, String toStatus, String action, String actor,
            String comment, Instant occurredAt, String correlationId);

    // Optional scan ingestion
    ScanImportBatch saveScanBatch(ScanImportBatch batch);
    Optional<ScanImportBatch> findScanBatch(UUID id);
    Optional<ScanImportBatch> findScanBatchByReference(String siteCode, String sourceSystem, String batchReference);
    /** Lists scan batches. Closes gap 3 — a batch was previously reachable only by an id nobody kept. */
    DispatchPage<ScanImportBatch> findScanBatches(ScanBatchQuery query);
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
