package gh.edu.clet.sfl.fleetlogistics.dispatch.support;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.common.security.SiteScopedPrincipal;
import gh.edu.clet.sfl.fleetlogistics.dispatch.application.port.DispatchEvidencePort;
import gh.edu.clet.sfl.fleetlogistics.dispatch.application.port.DispatchFleetReferencePort;
import gh.edu.clet.sfl.fleetlogistics.dispatch.application.port.DispatchOutboxAdminPort;
import gh.edu.clet.sfl.fleetlogistics.dispatch.application.port.DispatchRepository;
import gh.edu.clet.sfl.fleetlogistics.dispatch.application.port.SecurityVisibilityPort;
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
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.AuditChainVerification;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * In-memory port implementations for S171 dispatch application-service tests, mirroring
 * {@code fuel.support.FuelTestDoubles} and {@code fleet.support.FleetTestDoubles}: real
 * implementations of the contracts, not mocks, so a test exercises behaviour rather than
 * interaction bookkeeping.
 */
public final class DispatchTestDoubles {

    private DispatchTestDoubles() {
    }

    public static ActorContext actor(String subject, Set<SflRole> roles, Set<String> sites) {
        return new ActorContext(new SiteScopedPrincipal(subject, subject, roles, sites, false), "corr-test");
    }

    /** Holds every DISPATCH_ permission - the S171 equivalent of a fleet manager. */
    public static ActorContext dispatchManager(String... sites) {
        return actor("dispatch-manager@clet.edu.gh", Set.of(SflRole.FLEET_MANAGER), Set.of(sites));
    }

    /** Day-to-day operator: item/manifest CRUD, custody recording, return reconciliation, exception management. */
    public static ActorContext dispatchController(String... sites) {
        return actor("controller@clet.edu.gh", Set.of(SflRole.DISPATCH_CONTROLLER), Set.of(sites));
    }

    /** Destination-side: item/manifest read, custody recording, receipt confirmation, exception read. No manifest create. */
    public static ActorContext centreManager(String... sites) {
        return actor("centre-manager@clet.edu.gh", Set.of(SflRole.CENTRE_MANAGER), Set.of(sites));
    }

    /** Read plus escalate only - no manifest create, no exception approve/manage, no custody/receipt/return. */
    public static ActorContext securityOfficer(String... sites) {
        return actor("security@clet.edu.gh", Set.of(SflRole.SECURITY_OFFICER), Set.of(sites));
    }

    /** Inbound registration and distribution - no manifest create, no custody/receipt/return. */
    public static ActorContext mailroomOfficer(String... sites) {
        return actor("mailroom@clet.edu.gh", Set.of(SflRole.MAILROOM_OFFICER), Set.of(sites));
    }

    // ---- repository ----------------------------------------------------------------------------

    public static final class InMemoryDispatchRepository implements DispatchRepository {

        private final Map<UUID, CourierItem> items = new LinkedHashMap<>();
        private final Map<UUID, Dispatch> dispatches = new LinkedHashMap<>();
        private final Map<UUID, DispatchManifestItem> manifestItems = new LinkedHashMap<>();
        private final Map<UUID, CustodyHandover> handovers = new LinkedHashMap<>();
        private final Map<UUID, DispatchReceipt> receipts = new LinkedHashMap<>();
        private final Map<UUID, ReturnReconciliation> returns = new LinkedHashMap<>();
        private final Map<UUID, DispatchExceptionCase> exceptions = new LinkedHashMap<>();
        private final List<String> exceptionHistory = new ArrayList<>();
        private final Map<UUID, ScanImportBatch> scanBatches = new LinkedHashMap<>();
        private final Map<UUID, List<ScanImportRow>> scanRowsByBatch = new LinkedHashMap<>();
        private final Map<String, Map<String, Object>> dashboardSnapshots = new LinkedHashMap<>();

        // ---- courier items -----------------------------------------------------------------

        @Override
        public CourierItem saveItem(CourierItem item) {
            items.put(item.id(), item);
            return item;
        }

        @Override
        public Optional<CourierItem> findItem(UUID id) {
            return Optional.ofNullable(items.get(id));
        }

        @Override
        public Optional<CourierItem> findItemByNumber(String siteCode, String itemNumber) {
            return items.values().stream()
                    .filter(i -> i.siteCode().value().equals(siteCode))
                    .filter(i -> i.itemNumber().equals(itemNumber))
                    .findFirst();
        }

        @Override
        public DispatchPage<CourierItem> findItems(ItemQuery query) {
            List<CourierItem> matching = items.values().stream()
                    .filter(i -> query.sites().contains(i.siteCode().value()))
                    .filter(i -> query.direction() == null || i.direction() == query.direction())
                    .filter(i -> query.status() == null || i.status() == query.status())
                    .filter(i -> query.sensitivity() == null || i.sensitivity() == query.sensitivity())
                    .filter(i -> query.itemType() == null || i.itemType() == query.itemType())
                    .filter(i -> query.handler() == null || query.handler().equals(i.assignedHandler()))
                    .filter(i -> query.reference() == null || i.itemNumber().equals(query.reference()))
                    .filter(i -> query.undelivered() == null || query.undelivered() == i.undelivered())
                    .toList();
            return paged(matching, query.paging());
        }

        @Override
        public List<CourierItem> findItemsByIds(List<UUID> ids) {
            findItemsByIdsCalls++;
            if (ids == null || ids.isEmpty()) return List.of();
            return ids.stream().map(items::get).filter(java.util.Objects::nonNull).toList();
        }

        private int findItemsByIdsCalls;

        public int findItemsByIdsCallCount() {
            return findItemsByIdsCalls;
        }

        // ---- dispatch manifests --------------------------------------------------------------

        @Override
        public Dispatch saveDispatch(Dispatch dispatch) {
            dispatches.put(dispatch.id(), dispatch);
            return dispatch;
        }

        @Override
        public Optional<Dispatch> findDispatch(UUID id) {
            return Optional.ofNullable(dispatches.get(id));
        }

        @Override
        public Optional<Dispatch> findDispatchByNumber(String siteCode, String manifestNumber) {
            return dispatches.values().stream()
                    .filter(d -> d.siteCode().value().equals(siteCode))
                    .filter(d -> d.manifestNumber().equals(manifestNumber))
                    .findFirst();
        }

        @Override
        public DispatchPage<Dispatch> findDispatches(DispatchQuery query) {
            List<Dispatch> matching = dispatches.values().stream()
                    .filter(d -> query.sites().contains(d.siteCode().value()))
                    .filter(d -> query.status() == null || d.status() == query.status())
                    .filter(d -> query.destinationCentre() == null || query.destinationCentre().equals(d.destinationCentre()))
                    .filter(d -> query.tripId() == null || query.tripId().equals(d.tripId()))
                    .filter(d -> query.handler() == null || query.handler().equals(d.assignedHandler()))
                    .toList();
            return paged(matching, query.paging());
        }

        // ---- manifest items -------------------------------------------------------------------

        @Override
        public DispatchManifestItem saveManifestItem(DispatchManifestItem item) {
            manifestItems.put(item.id(), item);
            return item;
        }

        @Override
        public List<DispatchManifestItem> findManifestItems(UUID dispatchId) {
            findManifestItemsCalls++;
            return manifestItems.values().stream()
                    .filter(mi -> mi.dispatchId().equals(dispatchId))
                    .sorted(Comparator.comparingInt(DispatchManifestItem::sequenceNo))
                    .toList();
        }

        private int findManifestItemsCalls;

        public int findManifestItemsCallCount() {
            return findManifestItemsCalls;
        }

        @Override
        public int nextManifestSequence(UUID dispatchId) {
            return (int) manifestItems.values().stream().filter(mi -> mi.dispatchId().equals(dispatchId)).count() + 1;
        }

        // ---- custody handovers ------------------------------------------------------------------

        @Override
        public CustodyHandover saveHandover(CustodyHandover handover) {
            handovers.put(handover.id(), handover);
            return handover;
        }

        @Override
        public List<CustodyHandover> findHandovers(UUID dispatchId) {
            return handovers.values().stream()
                    .filter(h -> h.dispatchId().equals(dispatchId))
                    .sorted(Comparator.comparingInt(CustodyHandover::sequenceNo))
                    .toList();
        }

        @Override
        public DispatchPage<CustodyHandover> findHandovers(CustodyQuery query) {
            List<CustodyHandover> matching = handovers.values().stream()
                    .filter(h -> query.sites().contains(h.siteCode().value()))
                    .filter(h -> query.dispatchId() == null || query.dispatchId().equals(h.dispatchId()))
                    .filter(h -> query.hop() == null || h.hop() == query.hop())
                    .filter(h -> query.custodian() == null
                            || query.custodian().equals(h.transferringCustodian())
                            || query.custodian().equals(h.receivingCustodian()))
                    .filter(h -> query.sealState() == null || h.sealState() == query.sealState())
                    .toList();
            return paged(matching, query.paging());
        }

        @Override
        public int nextHandoverSequence(UUID dispatchId) {
            return (int) handovers.values().stream().filter(h -> h.dispatchId().equals(dispatchId)).count() + 1;
        }

        // ---- destination receipts --------------------------------------------------------------

        @Override
        public DispatchReceipt saveReceipt(DispatchReceipt receipt) {
            receipts.put(receipt.id(), receipt);
            return receipt;
        }

        @Override
        public Optional<DispatchReceipt> findReceipt(UUID id) {
            return Optional.ofNullable(receipts.get(id));
        }

        @Override
        public Optional<DispatchReceipt> findReceiptByCapture(UUID dispatchId, String captureCorrelationId) {
            return receipts.values().stream()
                    .filter(r -> r.dispatchId().equals(dispatchId))
                    .filter(r -> r.captureCorrelationId().equals(captureCorrelationId))
                    .findFirst();
        }

        @Override
        public List<DispatchReceipt> findReceipts(UUID dispatchId) {
            return receipts.values().stream().filter(r -> r.dispatchId().equals(dispatchId)).toList();
        }

        @Override
        public DispatchPage<DispatchReceipt> findReceipts(ReceiptQuery query) {
            List<DispatchReceipt> matching = receipts.values().stream()
                    .filter(r -> query.sites().contains(r.siteCode().value()))
                    .filter(r -> query.dispatchId() == null || query.dispatchId().equals(r.dispatchId()))
                    .filter(r -> query.outcome() == null || r.outcome() == query.outcome())
                    .filter(r -> query.varianceType() == null || r.varianceType() == query.varianceType())
                    .filter(r -> query.recipient() == null || query.recipient().equals(r.recipientName()))
                    .toList();
            return paged(matching, query.paging());
        }

        // ---- return reconciliation -------------------------------------------------------------

        @Override
        public ReturnReconciliation saveReturn(ReturnReconciliation reconciliation) {
            returns.put(reconciliation.id(), reconciliation);
            return reconciliation;
        }

        @Override
        public Optional<ReturnReconciliation> findReturn(UUID id) {
            return Optional.ofNullable(returns.get(id));
        }

        @Override
        public List<ReturnReconciliation> findReturns(UUID dispatchId) {
            return returns.values().stream().filter(r -> r.dispatchId().equals(dispatchId)).toList();
        }

        // ---- exception cases --------------------------------------------------------------------

        @Override
        public DispatchExceptionCase saveException(DispatchExceptionCase exceptionCase) {
            exceptions.put(exceptionCase.id(), exceptionCase);
            return exceptionCase;
        }

        @Override
        public Optional<DispatchExceptionCase> findException(UUID id) {
            return Optional.ofNullable(exceptions.get(id));
        }

        @Override
        public Optional<DispatchExceptionCase> findExceptionByOccurrence(String siteCode, String occurrenceKey) {
            return exceptions.values().stream()
                    .filter(e -> e.siteCode().value().equals(siteCode))
                    .filter(e -> e.occurrenceKey().equals(occurrenceKey))
                    .findFirst();
        }

        @Override
        public DispatchPage<DispatchExceptionCase> findExceptions(ExceptionQuery query) {
            List<DispatchExceptionCase> matching = exceptions.values().stream()
                    .filter(e -> query.sites().contains(e.siteCode().value()))
                    .filter(e -> query.type() == null || e.type() == query.type())
                    .filter(e -> query.status() == null || e.status() == query.status())
                    .filter(e -> query.severity() == null || e.severity() == query.severity())
                    .filter(e -> query.assignee() == null || query.assignee().equals(e.assignee()))
                    .filter(e -> !Boolean.TRUE.equals(query.unassigned()) || e.assignee() == null)
                    .filter(e -> query.securityRelevant() == null || query.securityRelevant() == e.securityRelevant())
                    .filter(e -> !Boolean.TRUE.equals(query.openOnly()) || e.open())
                    .filter(e -> query.dueBefore() == null
                            || (e.slaDueAt() != null && e.slaDueAt().isBefore(query.dueBefore())))
                    .filter(e -> query.dispatchId() == null || query.dispatchId().equals(e.dispatchId()))
                    .filter(e -> query.courierItemId() == null || query.courierItemId().equals(e.courierItemId()))
                    .toList();
            return paged(matching, query.paging());
        }

        @Override
        public boolean hasOpenException(UUID dispatchId) {
            return exceptions.values().stream()
                    .filter(e -> dispatchId.equals(e.dispatchId()))
                    .anyMatch(DispatchExceptionCase::open);
        }

        @Override
        public void saveExceptionHistory(UUID caseId, String fromStatus, String toStatus, String action,
                String actor, String comment, Instant occurredAt, String correlationId) {
            exceptionHistory.add(caseId + ":" + fromStatus + "->" + toStatus + ":" + action);
        }

        public List<String> exceptionHistory() {
            return List.copyOf(exceptionHistory);
        }

        // ---- optional scan ingestion -------------------------------------------------------------

        @Override
        public ScanImportBatch saveScanBatch(ScanImportBatch batch) {
            scanBatches.put(batch.id(), batch);
            return batch;
        }

        @Override
        public Optional<ScanImportBatch> findScanBatch(UUID id) {
            return Optional.ofNullable(scanBatches.get(id));
        }

        @Override
        public Optional<ScanImportBatch> findScanBatchByReference(String siteCode, String sourceSystem,
                String batchReference) {
            return scanBatches.values().stream()
                    .filter(b -> b.siteCode().value().equals(siteCode))
                    .filter(b -> b.sourceSystem().equals(sourceSystem))
                    .filter(b -> b.batchReference().equals(batchReference))
                    .findFirst();
        }

        @Override
        public DispatchPage<ScanImportBatch> findScanBatches(ScanBatchQuery query) {
            List<ScanImportBatch> matching = scanBatches.values().stream()
                    .filter(b -> query.sites().contains(b.siteCode().value()))
                    .filter(b -> query.sourceSystem() == null || b.sourceSystem().equals(query.sourceSystem()))
                    .filter(b -> query.dispatchId() == null || query.dispatchId().equals(b.dispatchId()))
                    .filter(b -> query.status() == null || b.status() == query.status())
                    .toList();
            return paged(matching, query.paging());
        }

        @Override
        public ScanImportRow saveScanRow(ScanImportRow row) {
            saveScanRowCalls++;
            scanRowsByBatch.computeIfAbsent(row.batchId(), id -> new ArrayList<>()).add(row);
            return row;
        }

        private int saveScanRowCalls;
        private int saveScanRowsCalls;

        public int saveScanRowCallCount() {
            return saveScanRowCalls;
        }

        public int saveScanRowsCallCount() {
            return saveScanRowsCalls;
        }

        @Override
        public void saveScanRows(List<ScanImportRow> rows) {
            saveScanRowsCalls++;
            for (ScanImportRow row : rows) {
                scanRowsByBatch.computeIfAbsent(row.batchId(), id -> new ArrayList<>()).add(row);
            }
        }

        @Override
        public List<ScanImportRow> findScanRows(UUID batchId) {
            return List.copyOf(scanRowsByBatch.getOrDefault(batchId, List.of()));
        }

        // ---- dashboard read model ---------------------------------------------------------------

        private Instant dashboardSourceUpdatedAt = Instant.now();

        /** Lets a dashboard-staleness test control the timestamp deterministically instead of the wall clock. */
        public void withDashboardSourceUpdatedAt(Instant value) {
            this.dashboardSourceUpdatedAt = value;
        }

        @Override
        public Map<String, Object> dashboardCounts(List<String> sites, String site) {
            Map<String, Object> counts = new LinkedHashMap<>();
            counts.put("sourceUpdatedAt", dashboardSourceUpdatedAt);
            counts.put("openExceptionCount",
                    exceptions.values().stream().filter(e -> sites.contains(e.siteCode().value()) && e.open()).count());
            counts.put("inTransitCount", dispatches.values().stream()
                    .filter(d -> sites.contains(d.siteCode().value()))
                    .filter(d -> d.status() == Dispatch.Status.DISPATCHED || d.status() == Dispatch.Status.IN_TRANSIT)
                    .count());
            return counts;
        }

        @Override
        public void saveDashboardSnapshot(String scopeKey, String siteCode, Instant generatedAt, boolean stale,
                Map<String, Object> counts, Instant sourceUpdatedAt, String warnings) {
            dashboardSnapshots.put(scopeKey, counts);
        }

        @Override
        public Optional<Map<String, Object>> latestDashboardSnapshot(String scopeKey) {
            return Optional.ofNullable(dashboardSnapshots.get(scopeKey));
        }

        // ---- scheduled-sweep support --------------------------------------------------------------

        @Override
        public List<UUID> findUndeliveredInboundItemIds(String siteCode, Instant olderThan, int limit) {
            return List.of();
        }

        @Override
        public List<OutstandingReturn> findOutstandingReturns(String siteCode, Instant olderThan, int limit) {
            return List.of();
        }

        @Override
        public List<String> activeSites() {
            Set<String> sites = new java.util.LinkedHashSet<>();
            dispatches.values().forEach(d -> sites.add(d.siteCode().value()));
            items.values().forEach(i -> sites.add(i.siteCode().value()));
            return List.copyOf(sites);
        }

        // ---- helpers ---------------------------------------------------------------------------

        private static <T> DispatchPage<T> paged(List<T> matching, Paging paging) {
            int from = Math.min(paging.offset(), matching.size());
            int to = Math.min(from + paging.size(), matching.size());
            return DispatchPage.of(matching.subList(from, to), paging.page(), paging.size(), matching.size(),
                    paging.sort());
        }
    }

    // ---- other ports -----------------------------------------------------------------------

    /** Validates nothing; every optional trip/vehicle/driver reference is accepted. */
    public static final class StubFleetReferencePort implements DispatchFleetReferencePort {
        @Override
        public void validate(UUID tripId, UUID vehicleId, UUID driverId, String siteCode) {
            // No-op: dispatch tests exercise S171 in isolation from S166's own trip register.
        }
    }

    /** Registers evidence in memory and returns a fresh id; the audit-chain replay is unused by these tests. */
    public static final class InMemoryEvidencePort implements DispatchEvidencePort {

        private final List<EvidenceRegistration> registered = new ArrayList<>();

        @Override
        public UUID register(EvidenceRegistration registration) {
            registered.add(registration);
            return UUID.randomUUID();
        }

        @Override
        public AuditChainVerification verifyAuditChain() {
            throw new UnsupportedOperationException("not exercised by dispatch application-service tests");
        }

        public List<EvidenceRegistration> registered() {
            return List.copyOf(registered);
        }
    }

    /** An outbox with nothing pending. */
    public static final class StubOutboxAdminPort implements DispatchOutboxAdminPort {
        @Override
        public OutboxHealth health() {
            return new OutboxHealth(0, 0, 0, List.of());
        }

        @Override
        public boolean replay(UUID messageId) {
            return false;
        }
    }

    /** Records security-relevant variances surfaced to SSEMP. */
    public static final class RecordingSecurityVisibilityPort implements SecurityVisibilityPort {

        private final List<UUID> surfaced = new ArrayList<>();

        @Override
        public void surfaceSecurityVariance(DispatchExceptionCase exceptionCase, ActorContext actor) {
            surfaced.add(exceptionCase.id());
        }

        public List<UUID> surfaced() {
            return List.copyOf(surfaced);
        }
    }
}
