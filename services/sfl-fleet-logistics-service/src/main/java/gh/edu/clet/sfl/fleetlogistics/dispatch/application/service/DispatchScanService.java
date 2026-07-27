package gh.edu.clet.sfl.fleetlogistics.dispatch.application.service;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.fleetlogistics.dispatch.application.port.DispatchRepository;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.CourierItem;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.DispatchExceptionCase;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.ScanImportBatch;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.ScanImportRow;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.AuditPort;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.IntegrationEventPublisher;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.event.FleetEventType;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.RecordNotFoundException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.AuditAction;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.RecordMetadata;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * S171-04: optional scanner/barcode ingestion. The full chain-of-custody works with no scanner; when a
 * scan batch or provider event is ingested, each scanned code is classified against the manifest. A code
 * that does not match its manifest entry is flagged (SCAN_MISMATCH) and routed to variance handling with
 * the {@code dispatch-scan-mismatch.v1} event. Vendor layouts stay in adapters; this service is neutral.
 */
@Service
public class DispatchScanService {

    private final DispatchRepository repository;
    private final DispatchAccessPolicy access;
    private final DispatchExceptionService exceptions;
    private final AuditPort audit;
    private final IntegrationEventPublisher events;
    private final Clock clock;

    public DispatchScanService(DispatchRepository repository, DispatchAccessPolicy access,
            DispatchExceptionService exceptions, AuditPort audit, IntegrationEventPublisher events, Clock clock) {
        this.repository = repository;
        this.access = access;
        this.exceptions = exceptions;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    public record ImportScanBatch(String siteCode, String sourceSystem, String batchReference, UUID dispatchId,
            byte[] content, ActorContext actor, SourceChannel channel) {}

    @Transactional
    public ScanImportBatch importCsv(ImportScanBatch c) {
        access.require(c.actor(), SflPermission.DISPATCH_INTEGRATION_INGEST, c.siteCode(), "ScanImportBatch", null);
        String reference = c.batchReference() == null || c.batchReference().isBlank()
                ? DispatchNumbers.next("SCAN") : c.batchReference().strip();
        var existing = repository.findScanBatchByReference(c.siteCode(), c.sourceSystem(), reference);
        if (existing.isPresent()) {
            return existing.get();
        }
        SiteCode site = SiteCode.of(c.siteCode());
        Set<String> manifestNumbers = manifestItemNumbers(c.dispatchId());
        Instant now = clock.instant();
        UUID batchId = UUID.randomUUID();
        List<String> lines = new String(c.content(), StandardCharsets.UTF_8).lines()
                .filter(l -> !l.isBlank()).toList();
        if (lines.size() < 2) {
            throw new IllegalArgumentException("Scan CSV must contain a header and at least one row");
        }
        int accepted = 0;
        List<String> mismatchRows = new ArrayList<>();
        for (int n = 1; n < lines.size(); n++) {
            String[] cols = lines.get(n).split(",", -1);
            String rowReference = cols.length > 0 && !cols[0].isBlank() ? cols[0].strip() : "row-" + n;
            String scannedCode = cols.length > 1 ? cols[1].strip() : cols[0].strip();
            var classified = classify(site, manifestNumbers, scannedCode);
            repository.saveScanRow(new ScanImportRow(UUID.randomUUID(), batchId, site, rowReference, scannedCode,
                    classified.courierItemId(), classified.outcome(), classified.message(), now));
            if (classified.outcome() == ScanImportRow.Outcome.MATCHED) {
                accepted++;
            } else {
                mismatchRows.add(rowReference + ":" + scannedCode + ":" + classified.outcome());
            }
        }
        int total = lines.size() - 1;
        int mismatch = total - accepted;
        ScanImportBatch.Status status = mismatch == 0 ? ScanImportBatch.Status.PROCESSED
                : accepted == 0 ? ScanImportBatch.Status.FAILED : ScanImportBatch.Status.PARTIAL;
        var batch = repository.saveScanBatch(new ScanImportBatch(batchId, site, reference, c.sourceSystem(),
                c.dispatchId(), total, accepted, mismatch, status,
                RecordMetadata.createdBy(c.actor().actorId(), now, c.channel(), c.actor().correlationId())));
        audit.record(c.actor(), c.channel(), site, AuditAction.CREATE, "ScanImportBatch", batchId.toString(), null,
                batch);
        if (!mismatchRows.isEmpty()) {
            raiseScanMismatch(site, c.dispatchId(), batchId, mismatchRows, c.actor(), c.channel());
        }
        return batch;
    }

    /** Single provider/scanner event (already accepted by the secure inbox). Returns the classified row. */
    @Transactional
    public ScanImportRow recordScanEvent(String siteCode, UUID dispatchId, String sourceSystem, String rowReference,
            String scannedCode, ActorContext actor, SourceChannel channel) {
        access.require(actor, SflPermission.DISPATCH_INTEGRATION_INGEST, siteCode, "ScanImportRow", null);
        SiteCode site = SiteCode.of(siteCode);
        Instant now = clock.instant();
        UUID batchId = UUID.randomUUID();
        String reference = rowReference == null || rowReference.isBlank() ? DispatchNumbers.next("SCANEVT")
                : rowReference.strip();
        var classified = classify(site, manifestItemNumbers(dispatchId), scannedCode);
        var row = repository.saveScanRow(new ScanImportRow(UUID.randomUUID(), batchId, site, reference, scannedCode,
                classified.courierItemId(), classified.outcome(), classified.message(), now));
        var batch = repository.saveScanBatch(new ScanImportBatch(batchId, site, reference, sourceSystem, dispatchId, 1,
                classified.outcome() == ScanImportRow.Outcome.MATCHED ? 1 : 0,
                classified.outcome() == ScanImportRow.Outcome.MATCHED ? 0 : 1,
                classified.outcome() == ScanImportRow.Outcome.MATCHED ? ScanImportBatch.Status.PROCESSED
                        : ScanImportBatch.Status.PARTIAL,
                RecordMetadata.createdBy(actor.actorId(), now, channel, actor.correlationId())));
        audit.record(actor, channel, site, AuditAction.CREATE, "ScanImportBatch", batchId.toString(), null, batch);
        if (classified.outcome() != ScanImportRow.Outcome.MATCHED) {
            raiseScanMismatch(site, dispatchId, batchId, List.of(reference + ":" + scannedCode + ":"
                    + classified.outcome()), actor, channel);
        }
        return row;
    }

    public ScanImportBatch batch(UUID id, ActorContext actor) {
        var batch = repository.findScanBatch(id).orElseThrow(() -> RecordNotFoundException.of("ScanImportBatch", id));
        access.require(actor, SflPermission.DISPATCH_MANIFEST_READ, batch.siteCode().value(), "ScanImportBatch",
                id.toString());
        return batch;
    }

    public List<ScanImportRow> rows(UUID batchId, ActorContext actor) {
        batch(batchId, actor);
        return repository.findScanRows(batchId);
    }

    private void raiseScanMismatch(SiteCode site, UUID dispatchId, UUID batchId, List<String> rows, ActorContext actor,
            SourceChannel channel) {
        events.publish(FleetEventType.DISPATCH_SCAN_MISMATCH, "ScanImportBatch", batchId.toString(), site, actor,
                Map.of("batchId", batchId, "dispatchId", dispatchId == null ? "" : dispatchId, "mismatchCount",
                        rows.size()));
        exceptions.openCase(new DispatchExceptionService.OpenCase(site.value(),
                DispatchExceptionCase.Type.SCAN_MISMATCH, DispatchExceptionCase.Severity.MEDIUM, false,
                "SCAN_MISMATCH:" + batchId, null, dispatchId, null, null, null, rows, actor, channel));
    }

    private Set<String> manifestItemNumbers(UUID dispatchId) {
        Set<String> numbers = new HashSet<>();
        if (dispatchId == null) return numbers;
        for (var manifestItem : repository.findManifestItems(dispatchId)) {
            repository.findItem(manifestItem.courierItemId())
                    .map(CourierItem::itemNumber).ifPresent(numbers::add);
        }
        return numbers;
    }

    private Classification classify(SiteCode site, Set<String> manifestNumbers, String scannedCode) {
        if (scannedCode == null || scannedCode.isBlank()) {
            return new Classification(ScanImportRow.Outcome.UNREGISTERED, null, "Blank scan code");
        }
        var item = repository.findItemByNumber(site.value(), scannedCode.strip());
        if (item.isEmpty()) {
            return new Classification(ScanImportRow.Outcome.UNREGISTERED, null,
                    "No courier item registered for code " + scannedCode);
        }
        if (!manifestNumbers.isEmpty() && !manifestNumbers.contains(scannedCode.strip())) {
            return new Classification(ScanImportRow.Outcome.MISMATCH, item.get().id(),
                    "Scanned item is not on the dispatch manifest");
        }
        return new Classification(ScanImportRow.Outcome.MATCHED, item.get().id(), null);
    }

    private record Classification(ScanImportRow.Outcome outcome, UUID courierItemId, String message) {}
}
