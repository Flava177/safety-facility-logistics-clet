package gh.edu.clet.sfl.fleetlogistics.dispatch.infrastructure.persistence;

import gh.edu.clet.sfl.fleetlogistics.dispatch.application.port.DispatchRepository;
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
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.RecordMetadata;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * JDBC persistence adapter for S171 (V16–V20 tables). It maps the dispatch domain records to/from the
 * {@code fleet_logistics} schema, preserving optimistic locking on mutable operational records, site
 * scope, edge/scan idempotency and audit correlation. Business transitions stay in domain/application
 * code — this adapter never decides state. Instant values bind as UTC {@link OffsetDateTime} because the
 * pgjdbc driver cannot infer a SQL type for {@link Instant}; the columns are TIMESTAMPTZ so UTC is exact.
 */
@Repository
public class JdbcDispatchRepository implements DispatchRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public JdbcDispatchRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    // ---- Courier items ---------------------------------------------------------------------------

    @Override
    public CourierItem saveItem(CourierItem i) {
        int updated = jdbc.update("""
                UPDATE fleet_logistics.courier_items SET assigned_handler=?, status=?, acknowledged_by=?,
                    acknowledged_at=?, acknowledgement_evidence_id=?, distribution_reference=?, misroute_reason=?,
                    undelivered=?, exception_reason=?, last_modified_by=?, last_modified_at=?, source_channel=?,
                    audit_correlation_id=?, version=version+1 WHERE id=? AND version=?
                """, i.assignedHandler(), i.status().name(), i.acknowledgedBy(), ts(i.acknowledgedAt()),
                i.acknowledgementEvidenceId(), i.distributionReference(), i.misrouteReason(), i.undelivered(),
                i.exceptionReason(), i.metadata().lastModifiedBy(), ts(i.metadata().lastModifiedAt()),
                i.metadata().sourceChannel().name(), i.metadata().auditCorrelationId(), i.id(), i.metadata().version());
        if (updated == 0 && findItem(i.id()).isEmpty()) {
            jdbc.update("""
                    INSERT INTO fleet_logistics.courier_items (id,site_code,item_number,direction,item_type,sensitivity,
                        chain_of_custody_required,origin,destination,sender,recipient,assigned_handler,status,
                        acknowledged_by,acknowledged_at,acknowledgement_evidence_id,distribution_reference,misroute_reason,
                        undelivered,exception_reason,created_by,created_at,last_modified_by,last_modified_at,source_channel,
                        audit_correlation_id,version)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """, i.id(), i.siteCode().value(), i.itemNumber(), i.direction().name(), i.itemType().name(),
                    i.sensitivity().name(), i.chainOfCustodyRequired(), i.origin(), i.destination(), i.sender(),
                    i.recipient(), i.assignedHandler(), i.status().name(), i.acknowledgedBy(), ts(i.acknowledgedAt()),
                    i.acknowledgementEvidenceId(), i.distributionReference(), i.misrouteReason(), i.undelivered(),
                    i.exceptionReason(), i.metadata().createdBy(), ts(i.metadata().createdAt()),
                    i.metadata().lastModifiedBy(), ts(i.metadata().lastModifiedAt()), i.metadata().sourceChannel().name(),
                    i.metadata().auditCorrelationId(), i.metadata().version());
        } else if (updated == 0) {
            throw new OptimisticLockingFailureException("CourierItem version conflict");
        }
        return findItem(i.id()).orElseThrow();
    }

    @Override
    public Optional<CourierItem> findItem(UUID id) {
        return one("SELECT * FROM fleet_logistics.courier_items WHERE id=?", this::item, id);
    }

    @Override
    public Optional<CourierItem> findItemByNumber(String siteCode, String itemNumber) {
        return one("SELECT * FROM fleet_logistics.courier_items WHERE site_code=? AND item_number=?", this::item,
                siteCode, itemNumber);
    }

    @Override
    public List<CourierItem> findItems(List<String> sites, CourierItem.Direction direction, CourierItem.Status status,
            CourierItem.Sensitivity sensitivity, String handler, Instant from, Instant to, int limit) {
        if (sites.isEmpty()) return List.of();
        StringBuilder sql = new StringBuilder("SELECT * FROM fleet_logistics.courier_items WHERE site_code = ANY (?)");
        List<Object> args = new ArrayList<>();
        if (direction != null) { sql.append(" AND direction=?"); args.add(direction.name()); }
        if (status != null) { sql.append(" AND status=?"); args.add(status.name()); }
        if (sensitivity != null) { sql.append(" AND sensitivity=?"); args.add(sensitivity.name()); }
        if (handler != null && !handler.isBlank()) { sql.append(" AND assigned_handler=?"); args.add(handler.strip()); }
        if (from != null) { sql.append(" AND created_at>=?"); args.add(ts(from)); }
        if (to != null) { sql.append(" AND created_at<?"); args.add(ts(to)); }
        sql.append(" ORDER BY created_at DESC LIMIT ?");
        args.add(bound(limit));
        return query(sql.toString(), sites, args, this::item);
    }

    // ---- Dispatch manifests ----------------------------------------------------------------------

    @Override
    public Dispatch saveDispatch(Dispatch d) {
        int updated = jdbc.update("""
                UPDATE fleet_logistics.dispatches SET route=?, assigned_handler=?, destination_centre=?,
                    examination_context=?, trip_id=?, vehicle_id=?, driver_id=?, item_count=?, seal_ids=?, status=?,
                    dispatched_at=?, received_at=?, reconciled_at=?, closure_reason=?, last_modified_by=?,
                    last_modified_at=?, source_channel=?, audit_correlation_id=?, version=version+1
                    WHERE id=? AND version=?
                """, d.route(), d.assignedHandler(), d.destinationCentre(), d.examinationContext(), d.tripId(),
                d.vehicleId(), d.driverId(), d.itemCount(), String.join(",", d.sealIds()), d.status().name(),
                ts(d.dispatchedAt()), ts(d.receivedAt()), ts(d.reconciledAt()), d.closureReason(),
                d.metadata().lastModifiedBy(), ts(d.metadata().lastModifiedAt()), d.metadata().sourceChannel().name(),
                d.metadata().auditCorrelationId(), d.id(), d.metadata().version());
        if (updated == 0 && findDispatch(d.id()).isEmpty()) {
            jdbc.update("""
                    INSERT INTO fleet_logistics.dispatches (id,site_code,manifest_number,route,assigned_handler,
                        destination_centre,examination_context,trip_id,vehicle_id,driver_id,item_count,seal_ids,status,
                        dispatched_at,received_at,reconciled_at,closure_reason,created_by,created_at,last_modified_by,
                        last_modified_at,source_channel,audit_correlation_id,version)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """, d.id(), d.siteCode().value(), d.manifestNumber(), d.route(), d.assignedHandler(),
                    d.destinationCentre(), d.examinationContext(), d.tripId(), d.vehicleId(), d.driverId(), d.itemCount(),
                    String.join(",", d.sealIds()), d.status().name(), ts(d.dispatchedAt()), ts(d.receivedAt()),
                    ts(d.reconciledAt()), d.closureReason(), d.metadata().createdBy(), ts(d.metadata().createdAt()),
                    d.metadata().lastModifiedBy(), ts(d.metadata().lastModifiedAt()), d.metadata().sourceChannel().name(),
                    d.metadata().auditCorrelationId(), d.metadata().version());
        } else if (updated == 0) {
            throw new OptimisticLockingFailureException("Dispatch version conflict");
        }
        return findDispatch(d.id()).orElseThrow();
    }

    @Override
    public Optional<Dispatch> findDispatch(UUID id) {
        return one("SELECT * FROM fleet_logistics.dispatches WHERE id=?", this::dispatch, id);
    }

    @Override
    public Optional<Dispatch> findDispatchByNumber(String siteCode, String manifestNumber) {
        return one("SELECT * FROM fleet_logistics.dispatches WHERE site_code=? AND manifest_number=?", this::dispatch,
                siteCode, manifestNumber);
    }

    @Override
    public List<Dispatch> findDispatches(List<String> sites, Dispatch.Status status, String destinationCentre,
            UUID tripId, Instant from, Instant to, int limit) {
        if (sites.isEmpty()) return List.of();
        StringBuilder sql = new StringBuilder("SELECT * FROM fleet_logistics.dispatches WHERE site_code = ANY (?)");
        List<Object> args = new ArrayList<>();
        if (status != null) { sql.append(" AND status=?"); args.add(status.name()); }
        if (destinationCentre != null && !destinationCentre.isBlank()) {
            sql.append(" AND destination_centre=?"); args.add(destinationCentre.strip());
        }
        if (tripId != null) { sql.append(" AND trip_id=?"); args.add(tripId); }
        if (from != null) { sql.append(" AND created_at>=?"); args.add(ts(from)); }
        if (to != null) { sql.append(" AND created_at<?"); args.add(ts(to)); }
        sql.append(" ORDER BY created_at DESC LIMIT ?");
        args.add(bound(limit));
        return query(sql.toString(), sites, args, this::dispatch);
    }

    // ---- Manifest items --------------------------------------------------------------------------

    @Override
    public DispatchManifestItem saveManifestItem(DispatchManifestItem m) {
        int updated = jdbc.update("""
                UPDATE fleet_logistics.dispatch_manifest_items SET expected_seal_id=?, expected_quantity=?,
                    return_status=?, returned_at=?, return_seal_state=? WHERE id=?
                """, m.expectedSealId(), m.expectedQuantity(), m.returnStatus() == null ? null : m.returnStatus().name(),
                ts(m.returnedAt()), m.returnSealState() == null ? null : m.returnSealState().name(), m.id());
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO fleet_logistics.dispatch_manifest_items (id,dispatch_id,courier_item_id,site_code,
                        sequence_no,expected_seal_id,expected_quantity,return_status,returned_at,return_seal_state,
                        created_at)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?)
                    """, m.id(), m.dispatchId(), m.courierItemId(), m.siteCode().value(), m.sequenceNo(),
                    m.expectedSealId(), m.expectedQuantity(), m.returnStatus() == null ? null : m.returnStatus().name(),
                    ts(m.returnedAt()), m.returnSealState() == null ? null : m.returnSealState().name(),
                    ts(m.createdAt()));
        }
        return m;
    }

    @Override
    public List<DispatchManifestItem> findManifestItems(UUID dispatchId) {
        return jdbc.query("SELECT * FROM fleet_logistics.dispatch_manifest_items WHERE dispatch_id=? ORDER BY sequence_no",
                this::manifestItem, dispatchId);
    }

    @Override
    public int nextManifestSequence(UUID dispatchId) {
        Integer next = jdbc.queryForObject(
                "SELECT COALESCE(MAX(sequence_no),0)+1 FROM fleet_logistics.dispatch_manifest_items WHERE dispatch_id=?",
                Integer.class, dispatchId);
        return next == null ? 1 : next;
    }

    // ---- Custody handovers (append-only) ---------------------------------------------------------

    @Override
    public CustodyHandover saveHandover(CustodyHandover h) {
        jdbc.update("""
                INSERT INTO fleet_logistics.custody_handovers (id,dispatch_id,site_code,hop,sequence_no,
                    transferring_custodian,receiving_custodian,occurred_at,seal_state,verified_count,notes,evidence_id,
                    created_by,created_at,source_channel,audit_correlation_id)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, h.id(), h.dispatchId(), h.siteCode().value(), h.hop().name(), h.sequenceNo(),
                h.transferringCustodian(), h.receivingCustodian(), ts(h.occurredAt()), h.sealState().name(),
                h.verifiedCount(), h.notes(), h.evidenceId(), h.createdBy(), ts(h.createdAt()),
                h.sourceChannel().name(), h.correlationId());
        return h;
    }

    @Override
    public List<CustodyHandover> findHandovers(UUID dispatchId) {
        return jdbc.query("SELECT * FROM fleet_logistics.custody_handovers WHERE dispatch_id=? ORDER BY sequence_no",
                this::handover, dispatchId);
    }

    @Override
    public int nextHandoverSequence(UUID dispatchId) {
        Integer next = jdbc.queryForObject(
                "SELECT COALESCE(MAX(sequence_no),0)+1 FROM fleet_logistics.custody_handovers WHERE dispatch_id=?",
                Integer.class, dispatchId);
        return next == null ? 1 : next;
    }

    // ---- Destination receipts --------------------------------------------------------------------

    @Override
    public DispatchReceipt saveReceipt(DispatchReceipt r) {
        int updated = jdbc.update("""
                UPDATE fleet_logistics.dispatch_receipts SET seal_state=?, seal_verified=?, expected_count=?,
                    verified_count=?, recipient_name=?, signature_evidence_id=?, outcome=?, variance_type=?,
                    captured_at=?, edge_captured=?, reconciled_at=?, last_modified_by=?, last_modified_at=?,
                    source_channel=?, audit_correlation_id=?, version=version+1 WHERE id=? AND version=?
                """, r.sealState().name(), r.sealVerified(), r.expectedCount(), r.verifiedCount(), r.recipientName(),
                r.signatureEvidenceId(), r.outcome().name(), r.varianceType() == null ? null : r.varianceType().name(),
                ts(r.capturedAt()), r.edgeCaptured(), ts(r.reconciledAt()), r.metadata().lastModifiedBy(),
                ts(r.metadata().lastModifiedAt()), r.metadata().sourceChannel().name(),
                r.metadata().auditCorrelationId(), r.id(), r.metadata().version());
        if (updated == 0 && findReceipt(r.id()).isEmpty()) {
            jdbc.update("""
                    INSERT INTO fleet_logistics.dispatch_receipts (id,dispatch_id,site_code,seal_state,seal_verified,
                        expected_count,verified_count,recipient_name,signature_evidence_id,outcome,variance_type,
                        captured_at,edge_captured,capture_correlation_id,reconciled_at,created_by,created_at,
                        last_modified_by,last_modified_at,source_channel,audit_correlation_id,version)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """, r.id(), r.dispatchId(), r.siteCode().value(), r.sealState().name(), r.sealVerified(),
                    r.expectedCount(), r.verifiedCount(), r.recipientName(), r.signatureEvidenceId(),
                    r.outcome().name(), r.varianceType() == null ? null : r.varianceType().name(), ts(r.capturedAt()),
                    r.edgeCaptured(), r.captureCorrelationId(), ts(r.reconciledAt()), r.metadata().createdBy(),
                    ts(r.metadata().createdAt()), r.metadata().lastModifiedBy(), ts(r.metadata().lastModifiedAt()),
                    r.metadata().sourceChannel().name(), r.metadata().auditCorrelationId(), r.metadata().version());
        } else if (updated == 0) {
            throw new OptimisticLockingFailureException("DispatchReceipt version conflict");
        }
        return findReceipt(r.id()).orElseThrow();
    }

    @Override
    public Optional<DispatchReceipt> findReceipt(UUID id) {
        return one("SELECT * FROM fleet_logistics.dispatch_receipts WHERE id=?", this::receipt, id);
    }

    @Override
    public Optional<DispatchReceipt> findReceiptByCapture(UUID dispatchId, String captureCorrelationId) {
        return one("SELECT * FROM fleet_logistics.dispatch_receipts WHERE dispatch_id=? AND capture_correlation_id=?",
                this::receipt, dispatchId, captureCorrelationId);
    }

    @Override
    public List<DispatchReceipt> findReceipts(UUID dispatchId) {
        return jdbc.query("SELECT * FROM fleet_logistics.dispatch_receipts WHERE dispatch_id=? ORDER BY captured_at DESC",
                this::receipt, dispatchId);
    }

    // ---- Return reconciliation -------------------------------------------------------------------

    @Override
    public ReturnReconciliation saveReturn(ReturnReconciliation r) {
        jdbc.update("""
                INSERT INTO fleet_logistics.return_reconciliations (id,dispatch_id,site_code,expected_count,
                    returned_count,shortfall,extras,broken_seals,outcome,notes,evidence_id,reconciled_by,reconciled_at,
                    created_by,created_at,last_modified_by,last_modified_at,source_channel,audit_correlation_id,version)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, r.id(), r.dispatchId(), r.siteCode().value(), r.expectedCount(), r.returnedCount(), r.shortfall(),
                r.extras(), r.brokenSeals(), r.outcome().name(), r.notes(), r.evidenceId(), r.reconciledBy(),
                ts(r.reconciledAt()), r.metadata().createdBy(), ts(r.metadata().createdAt()),
                r.metadata().lastModifiedBy(), ts(r.metadata().lastModifiedAt()), r.metadata().sourceChannel().name(),
                r.metadata().auditCorrelationId(), r.metadata().version());
        return r;
    }

    @Override
    public Optional<ReturnReconciliation> findReturn(UUID id) {
        return one("SELECT * FROM fleet_logistics.return_reconciliations WHERE id=?", this::reconciliation, id);
    }

    @Override
    public List<ReturnReconciliation> findReturns(UUID dispatchId) {
        return jdbc.query(
                "SELECT * FROM fleet_logistics.return_reconciliations WHERE dispatch_id=? ORDER BY reconciled_at DESC",
                this::reconciliation, dispatchId);
    }

    // ---- Exception cases -------------------------------------------------------------------------

    @Override
    public DispatchExceptionCase saveException(DispatchExceptionCase e) {
        int updated = jdbc.update("""
                UPDATE fleet_logistics.dispatch_exception_cases SET status=?, assignee=?, explanation=?, evidence_id=?,
                    manager_decision=?, closure_reason=?, escalation_level=?, security_relevant=?, last_modified_by=?,
                    last_modified_at=?, source_channel=?, audit_correlation_id=?, version=version+1 WHERE id=? AND version=?
                """, e.status().name(), e.assignee(), e.explanation(), e.evidenceId(),
                e.decision() == null ? null : e.decision().name(), e.closureReason(), e.escalationLevel(),
                e.securityRelevant(), e.metadata().lastModifiedBy(), ts(e.metadata().lastModifiedAt()),
                e.metadata().sourceChannel().name(), e.metadata().auditCorrelationId(), e.id(), e.metadata().version());
        if (updated == 0 && findException(e.id()).isEmpty()) {
            jdbc.update("""
                    INSERT INTO fleet_logistics.dispatch_exception_cases (id,exception_number,site_code,occurrence_key,
                        courier_item_id,dispatch_id,handover_id,receipt_id,trip_id,exception_type,severity,
                        security_relevant,status,assignee,sla_due_at,explanation,evidence_id,manager_decision,
                        closure_reason,escalation_level,detected_rules,created_by,created_at,last_modified_by,
                        last_modified_at,source_channel,audit_correlation_id,version)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?,?,?,?,?,?,?)
                    """, e.id(), e.exceptionNumber(), e.siteCode().value(), e.occurrenceKey(), e.courierItemId(),
                    e.dispatchId(), e.handoverId(), e.receiptId(), e.tripId(), e.type().name(), e.severity().name(),
                    e.securityRelevant(), e.status().name(), e.assignee(), ts(e.slaDueAt()), e.explanation(),
                    e.evidenceId(), e.decision() == null ? null : e.decision().name(), e.closureReason(),
                    e.escalationLevel(), json(e.detectedRules()), e.metadata().createdBy(), ts(e.metadata().createdAt()),
                    e.metadata().lastModifiedBy(), ts(e.metadata().lastModifiedAt()), e.metadata().sourceChannel().name(),
                    e.metadata().auditCorrelationId(), e.metadata().version());
        } else if (updated == 0) {
            throw new OptimisticLockingFailureException("DispatchExceptionCase version conflict");
        }
        return findException(e.id()).orElseThrow();
    }

    @Override
    public Optional<DispatchExceptionCase> findException(UUID id) {
        return one("SELECT * FROM fleet_logistics.dispatch_exception_cases WHERE id=?", this::exceptionCase, id);
    }

    @Override
    public Optional<DispatchExceptionCase> findExceptionByOccurrence(String siteCode, String occurrenceKey) {
        return one("SELECT * FROM fleet_logistics.dispatch_exception_cases WHERE site_code=? AND occurrence_key=?",
                this::exceptionCase, siteCode, occurrenceKey);
    }

    @Override
    public List<DispatchExceptionCase> findExceptions(List<String> sites, DispatchExceptionCase.Type type,
            DispatchExceptionCase.Status status, Instant dueBefore, int limit) {
        if (sites.isEmpty()) return List.of();
        StringBuilder sql = new StringBuilder(
                "SELECT * FROM fleet_logistics.dispatch_exception_cases WHERE site_code = ANY (?)");
        List<Object> args = new ArrayList<>();
        if (type != null) { sql.append(" AND exception_type=?"); args.add(type.name()); }
        if (status != null) { sql.append(" AND status=?"); args.add(status.name()); }
        if (dueBefore != null) {
            sql.append(" AND sla_due_at<? AND status NOT IN ('CLOSED','CANCELLED')");
            args.add(ts(dueBefore));
        }
        sql.append(" ORDER BY sla_due_at LIMIT ?");
        args.add(bound(limit));
        return query(sql.toString(), sites, args, this::exceptionCase);
    }

    @Override
    public boolean hasOpenException(UUID dispatchId) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM fleet_logistics.dispatch_exception_cases
                WHERE dispatch_id=? AND status NOT IN ('CLOSED','CANCELLED')
                """, Long.class, dispatchId);
        return count != null && count > 0;
    }

    @Override
    public void saveExceptionHistory(UUID caseId, String fromStatus, String toStatus, String action, String actor,
            String comment, Instant occurredAt, String correlationId) {
        jdbc.update("""
                INSERT INTO fleet_logistics.dispatch_exception_case_history (id,case_id,from_status,to_status,action,
                    actor,comment,occurred_at,correlation_id)
                VALUES (?,?,?,?,?,?,?,?,?)
                """, UUID.randomUUID(), caseId, fromStatus, toStatus, action, actor, comment, ts(occurredAt),
                correlationId);
    }

    // ---- Optional scan ingestion -----------------------------------------------------------------

    @Override
    public ScanImportBatch saveScanBatch(ScanImportBatch b) {
        int updated = jdbc.update("""
                UPDATE fleet_logistics.scan_import_batches SET total_rows=?, accepted_rows=?, mismatch_rows=?, status=?,
                    last_modified_by=?, last_modified_at=?, source_channel=?, audit_correlation_id=?, version=version+1
                    WHERE id=? AND version=?
                """, b.totalRows(), b.acceptedRows(), b.mismatchRows(), b.status().name(), b.metadata().lastModifiedBy(),
                ts(b.metadata().lastModifiedAt()), b.metadata().sourceChannel().name(),
                b.metadata().auditCorrelationId(), b.id(), b.metadata().version());
        if (updated == 0 && findScanBatch(b.id()).isEmpty()) {
            jdbc.update("""
                    INSERT INTO fleet_logistics.scan_import_batches (id,site_code,batch_reference,source_system,
                        dispatch_id,total_rows,accepted_rows,mismatch_rows,status,created_by,created_at,last_modified_by,
                        last_modified_at,source_channel,audit_correlation_id,version)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """, b.id(), b.siteCode().value(), b.batchReference(), b.sourceSystem(), b.dispatchId(),
                    b.totalRows(), b.acceptedRows(), b.mismatchRows(), b.status().name(), b.metadata().createdBy(),
                    ts(b.metadata().createdAt()), b.metadata().lastModifiedBy(), ts(b.metadata().lastModifiedAt()),
                    b.metadata().sourceChannel().name(), b.metadata().auditCorrelationId(), b.metadata().version());
        } else if (updated == 0) {
            throw new OptimisticLockingFailureException("ScanImportBatch version conflict");
        }
        return findScanBatch(b.id()).orElseThrow();
    }

    @Override
    public Optional<ScanImportBatch> findScanBatch(UUID id) {
        return one("SELECT * FROM fleet_logistics.scan_import_batches WHERE id=?", this::scanBatch, id);
    }

    @Override
    public Optional<ScanImportBatch> findScanBatchByReference(String siteCode, String sourceSystem,
            String batchReference) {
        return one("""
                SELECT * FROM fleet_logistics.scan_import_batches
                WHERE site_code=? AND source_system=? AND batch_reference=?
                """, this::scanBatch, siteCode, sourceSystem, batchReference);
    }

    @Override
    public ScanImportRow saveScanRow(ScanImportRow r) {
        jdbc.update("""
                INSERT INTO fleet_logistics.scan_import_rows (id,batch_id,site_code,row_reference,scanned_code,
                    courier_item_id,outcome,message,created_at)
                VALUES (?,?,?,?,?,?,?,?,?)
                ON CONFLICT (batch_id,row_reference) DO NOTHING
                """, r.id(), r.batchId(), r.siteCode().value(), r.rowReference(), r.scannedCode(), r.courierItemId(),
                r.outcome().name(), r.message(), ts(r.createdAt()));
        return r;
    }

    @Override
    public List<ScanImportRow> findScanRows(UUID batchId) {
        return jdbc.query("SELECT * FROM fleet_logistics.scan_import_rows WHERE batch_id=? ORDER BY row_reference",
                this::scanRow, batchId);
    }

    // ---- Dashboard read model --------------------------------------------------------------------

    @Override
    public Map<String, Object> dashboardCounts(List<String> sites, String site) {
        if (sites.isEmpty()) return Map.of();
        String scope = site == null ? null : SiteCode.of(site).value();
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("inTransitCount", count(sites, scope,
                "SELECT COUNT(*) FROM fleet_logistics.dispatches WHERE %s AND status IN ('DISPATCHED','IN_TRANSIT')"));
        counts.put("openExceptionCount", count(sites, scope,
                "SELECT COUNT(*) FROM fleet_logistics.dispatch_exception_cases WHERE %s AND status NOT IN ('CLOSED','CANCELLED')"));
        counts.put("custodyGapCount", count(sites, scope,
                "SELECT COUNT(*) FROM fleet_logistics.dispatch_exception_cases WHERE %s AND exception_type='CUSTODY_GAP' AND status NOT IN ('CLOSED','CANCELLED')"));
        counts.put("receiptVarianceCount", count(sites, scope,
                "SELECT COUNT(*) FROM fleet_logistics.dispatch_exception_cases WHERE %s AND exception_type='RECEIPT_VARIANCE' AND status NOT IN ('CLOSED','CANCELLED')"));
        counts.put("outstandingReturnCount", count(sites, scope,
                "SELECT COUNT(*) FROM fleet_logistics.dispatch_manifest_items WHERE %s AND return_status='OUTSTANDING'"));
        counts.put("undeliveredCount", count(sites, scope,
                "SELECT COUNT(*) FROM fleet_logistics.courier_items WHERE %s AND undelivered=true AND status<>'CLOSED'"));
        counts.put("overdueReceiptCount", count(sites, scope,
                "SELECT COUNT(*) FROM fleet_logistics.dispatches WHERE %s AND status IN ('DISPATCHED','IN_TRANSIT') AND dispatched_at < now() - interval '2 days'"));
        counts.put("slaBreachCount", count(sites, scope,
                "SELECT COUNT(*) FROM fleet_logistics.dispatch_exception_cases WHERE %s AND status NOT IN ('CLOSED','CANCELLED') AND sla_due_at < now()"));
        counts.put("sourceUpdatedAt", maxSourceUpdatedAt(sites, scope));
        return counts;
    }

    @Override
    public void saveDashboardSnapshot(String scopeKey, String siteCode, Instant generatedAt, boolean stale,
            Map<String, Object> counts, Instant sourceUpdatedAt, String warnings) {
        jdbc.update("""
                INSERT INTO fleet_logistics.dispatch_dashboard_snapshots (id,scope_key,site_code,generated_at,stale,
                    in_transit_count,open_exception_count,custody_gap_count,receipt_variance_count,
                    outstanding_return_count,undelivered_count,overdue_receipt_count,sla_breach_count,source_updated_at,
                    warnings)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, UUID.randomUUID(), scopeKey, siteCode, ts(generatedAt), stale,
                asInt(counts.get("inTransitCount")), asInt(counts.get("openExceptionCount")),
                asInt(counts.get("custodyGapCount")), asInt(counts.get("receiptVarianceCount")),
                asInt(counts.get("outstandingReturnCount")), asInt(counts.get("undeliveredCount")),
                asInt(counts.get("overdueReceiptCount")), asInt(counts.get("slaBreachCount")), ts(sourceUpdatedAt),
                warnings);
    }

    @Override
    public Optional<Map<String, Object>> latestDashboardSnapshot(String scopeKey) {
        return one("""
                SELECT * FROM fleet_logistics.dispatch_dashboard_snapshots WHERE scope_key=?
                ORDER BY generated_at DESC LIMIT 1
                """, (rs, n) -> snapshot(rs), scopeKey);
    }

    // ---- Scheduled-sweep support -----------------------------------------------------------------

    @Override
    public List<UUID> findUndeliveredInboundItemIds(String siteCode, Instant olderThan, int limit) {
        return jdbc.query("""
                SELECT id FROM fleet_logistics.courier_items
                WHERE site_code=? AND direction='INBOUND' AND undelivered=false
                  AND status NOT IN ('DELIVERED','RETURNED','CLOSED','EXCEPTION') AND created_at < ?
                ORDER BY created_at LIMIT ?
                """, (rs, n) -> (UUID) rs.getObject("id"), siteCode, ts(olderThan), bound(limit));
    }

    @Override
    public List<OutstandingReturn> findOutstandingReturns(String siteCode, Instant olderThan, int limit) {
        return jdbc.query("""
                SELECT mi.dispatch_id, mi.id AS manifest_item_id, mi.courier_item_id
                FROM fleet_logistics.dispatch_manifest_items mi
                JOIN fleet_logistics.dispatches d ON d.id = mi.dispatch_id
                WHERE mi.site_code=? AND mi.return_status='PENDING' AND d.dispatched_at IS NOT NULL
                  AND d.dispatched_at < ? AND d.status NOT IN ('CLOSED','RECONCILED')
                ORDER BY d.dispatched_at LIMIT ?
                """, (rs, n) -> new OutstandingReturn((UUID) rs.getObject("dispatch_id"),
                        (UUID) rs.getObject("manifest_item_id"), (UUID) rs.getObject("courier_item_id")),
                siteCode, ts(olderThan), bound(limit));
    }

    @Override
    public List<String> activeSites() {
        return jdbc.query("""
                SELECT DISTINCT site_code FROM fleet_logistics.courier_items
                UNION SELECT DISTINCT site_code FROM fleet_logistics.dispatches
                UNION SELECT DISTINCT site_code FROM fleet_logistics.dispatch_exception_cases
                """, (rs, n) -> rs.getString(1));
    }

    // ---- Row mappers -----------------------------------------------------------------------------

    private CourierItem item(ResultSet r, int n) throws SQLException {
        return new CourierItem(uuid(r, "id"), r.getString("item_number"), SiteCode.of(r.getString("site_code")),
                CourierItem.Direction.valueOf(r.getString("direction")),
                CourierItem.Type.valueOf(r.getString("item_type")),
                CourierItem.Sensitivity.valueOf(r.getString("sensitivity")), r.getBoolean("chain_of_custody_required"),
                r.getString("origin"), r.getString("destination"), r.getString("sender"), r.getString("recipient"),
                r.getString("assigned_handler"), CourierItem.Status.valueOf(r.getString("status")),
                r.getString("acknowledged_by"), instant(r, "acknowledged_at"),
                uuid(r, "acknowledgement_evidence_id"), r.getString("distribution_reference"),
                r.getString("misroute_reason"), r.getBoolean("undelivered"), r.getString("exception_reason"),
                metadata(r));
    }

    private Dispatch dispatch(ResultSet r, int n) throws SQLException {
        return new Dispatch(uuid(r, "id"), r.getString("manifest_number"), SiteCode.of(r.getString("site_code")),
                r.getString("route"), r.getString("assigned_handler"), r.getString("destination_centre"),
                r.getString("examination_context"), uuid(r, "trip_id"), uuid(r, "vehicle_id"), uuid(r, "driver_id"),
                r.getInt("item_count"), csv(r.getString("seal_ids")),
                Dispatch.Status.valueOf(r.getString("status")), instant(r, "dispatched_at"), instant(r, "received_at"),
                instant(r, "reconciled_at"), r.getString("closure_reason"), metadata(r));
    }

    private DispatchManifestItem manifestItem(ResultSet r, int n) throws SQLException {
        String returnStatus = r.getString("return_status");
        String returnSeal = r.getString("return_seal_state");
        return new DispatchManifestItem(uuid(r, "id"), uuid(r, "dispatch_id"), uuid(r, "courier_item_id"),
                SiteCode.of(r.getString("site_code")), r.getInt("sequence_no"), r.getString("expected_seal_id"),
                r.getInt("expected_quantity"),
                returnStatus == null ? DispatchManifestItem.ReturnStatus.PENDING
                        : DispatchManifestItem.ReturnStatus.valueOf(returnStatus),
                instant(r, "returned_at"), returnSeal == null ? null : SealState.valueOf(returnSeal),
                instant(r, "created_at"));
    }

    private CustodyHandover handover(ResultSet r, int n) throws SQLException {
        Integer verified = (Integer) r.getObject("verified_count");
        return new CustodyHandover(uuid(r, "id"), uuid(r, "dispatch_id"), SiteCode.of(r.getString("site_code")),
                CustodyHop.valueOf(r.getString("hop")), r.getInt("sequence_no"), r.getString("transferring_custodian"),
                r.getString("receiving_custodian"), instant(r, "occurred_at"),
                SealState.valueOf(r.getString("seal_state")), verified, r.getString("notes"), uuid(r, "evidence_id"),
                r.getString("created_by"), instant(r, "created_at"),
                SourceChannel.valueOf(r.getString("source_channel")), r.getString("audit_correlation_id"));
    }

    private DispatchReceipt receipt(ResultSet r, int n) throws SQLException {
        String variance = r.getString("variance_type");
        return new DispatchReceipt(uuid(r, "id"), uuid(r, "dispatch_id"), SiteCode.of(r.getString("site_code")),
                SealState.valueOf(r.getString("seal_state")), r.getBoolean("seal_verified"), r.getInt("expected_count"),
                r.getInt("verified_count"), r.getString("recipient_name"), uuid(r, "signature_evidence_id"),
                DispatchReceipt.ReceiptOutcome.valueOf(r.getString("outcome")),
                variance == null ? null : DispatchReceipt.VarianceType.valueOf(variance), instant(r, "captured_at"),
                r.getBoolean("edge_captured"), r.getString("capture_correlation_id"), instant(r, "reconciled_at"),
                metadata(r));
    }

    private ReturnReconciliation reconciliation(ResultSet r, int n) throws SQLException {
        return new ReturnReconciliation(uuid(r, "id"), uuid(r, "dispatch_id"), SiteCode.of(r.getString("site_code")),
                r.getInt("expected_count"), r.getInt("returned_count"), r.getInt("shortfall"), r.getInt("extras"),
                r.getInt("broken_seals"), ReturnReconciliation.ReturnOutcome.valueOf(r.getString("outcome")),
                r.getString("notes"), uuid(r, "evidence_id"), r.getString("reconciled_by"),
                instant(r, "reconciled_at"), metadata(r));
    }

    private DispatchExceptionCase exceptionCase(ResultSet r, int n) throws SQLException {
        String decision = r.getString("manager_decision");
        return new DispatchExceptionCase(uuid(r, "id"), r.getString("exception_number"),
                SiteCode.of(r.getString("site_code")), r.getString("occurrence_key"), uuid(r, "courier_item_id"),
                uuid(r, "dispatch_id"), uuid(r, "handover_id"), uuid(r, "receipt_id"), uuid(r, "trip_id"),
                DispatchExceptionCase.Type.valueOf(r.getString("exception_type")),
                DispatchExceptionCase.Severity.valueOf(r.getString("severity")), r.getBoolean("security_relevant"),
                DispatchExceptionCase.Status.valueOf(r.getString("status")), r.getString("assignee"),
                instant(r, "sla_due_at"), r.getString("explanation"), uuid(r, "evidence_id"),
                decision == null ? null : DispatchExceptionCase.Decision.valueOf(decision), r.getString("closure_reason"),
                r.getInt("escalation_level"), readList(r.getString("detected_rules")), metadata(r));
    }

    private ScanImportBatch scanBatch(ResultSet r, int n) throws SQLException {
        return new ScanImportBatch(uuid(r, "id"), SiteCode.of(r.getString("site_code")), r.getString("batch_reference"),
                r.getString("source_system"), uuid(r, "dispatch_id"), r.getInt("total_rows"), r.getInt("accepted_rows"),
                r.getInt("mismatch_rows"), ScanImportBatch.Status.valueOf(r.getString("status")), metadata(r));
    }

    private ScanImportRow scanRow(ResultSet r, int n) throws SQLException {
        return new ScanImportRow(uuid(r, "id"), uuid(r, "batch_id"), SiteCode.of(r.getString("site_code")),
                r.getString("row_reference"), r.getString("scanned_code"), uuid(r, "courier_item_id"),
                ScanImportRow.Outcome.valueOf(r.getString("outcome")), r.getString("message"), instant(r, "created_at"));
    }

    private Map<String, Object> snapshot(ResultSet r) throws SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("scopeKey", r.getString("scope_key"));
        m.put("siteCode", r.getString("site_code"));
        m.put("generatedAt", instant(r, "generated_at"));
        m.put("stale", r.getBoolean("stale"));
        m.put("inTransitCount", r.getInt("in_transit_count"));
        m.put("openExceptionCount", r.getInt("open_exception_count"));
        m.put("custodyGapCount", r.getInt("custody_gap_count"));
        m.put("receiptVarianceCount", r.getInt("receipt_variance_count"));
        m.put("outstandingReturnCount", r.getInt("outstanding_return_count"));
        m.put("undeliveredCount", r.getInt("undelivered_count"));
        m.put("overdueReceiptCount", r.getInt("overdue_receipt_count"));
        m.put("slaBreachCount", r.getInt("sla_breach_count"));
        m.put("sourceUpdatedAt", instant(r, "source_updated_at"));
        m.put("warnings", r.getString("warnings"));
        return m;
    }

    private RecordMetadata metadata(ResultSet r) throws SQLException {
        return RecordMetadata.rehydrate(r.getString("created_by"), instant(r, "created_at"),
                r.getString("last_modified_by"), instant(r, "last_modified_at"), r.getLong("version"),
                SourceChannel.valueOf(r.getString("source_channel")), r.getString("audit_correlation_id"));
    }

    // ---- Helpers ---------------------------------------------------------------------------------

    private long count(List<String> sites, String site, String template) {
        String scoped = site == null ? "site_code = ANY (?)" : "site_code = ANY (?) AND site_code=?";
        String sql = String.format(template, scoped);
        Long value = jdbc.query(con -> {
            var ps = con.prepareStatement(sql);
            ps.setArray(1, con.createArrayOf("varchar", sites.toArray()));
            if (site != null) ps.setString(2, site);
            return ps;
        }, rs -> rs.next() ? rs.getLong(1) : 0L);
        return value == null ? 0L : value;
    }

    private Instant maxSourceUpdatedAt(List<String> sites, String site) {
        String scoped = site == null ? "site_code = ANY (?)" : "site_code = ANY (?) AND site_code=?";
        String sql = "SELECT MAX(m) FROM (SELECT MAX(last_modified_at) AS m FROM fleet_logistics.courier_items WHERE "
                + scoped + " UNION ALL SELECT MAX(last_modified_at) FROM fleet_logistics.dispatches WHERE " + scoped
                + " UNION ALL SELECT MAX(last_modified_at) FROM fleet_logistics.dispatch_exception_cases WHERE "
                + scoped + ") s";
        return jdbc.query(con -> {
            var ps = con.prepareStatement(sql);
            int i = 1;
            for (int block = 0; block < 3; block++) {
                ps.setArray(i++, con.createArrayOf("varchar", sites.toArray()));
                if (site != null) ps.setString(i++, site);
            }
            return ps;
        }, rs -> rs.next() && rs.getTimestamp(1) != null ? rs.getTimestamp(1).toInstant() : null);
    }

    private <T> List<T> query(String sql, List<String> sites, List<Object> args, RowMapper<T> mapper) {
        return jdbc.query(con -> {
            var ps = con.prepareStatement(sql);
            ps.setArray(1, con.createArrayOf("varchar", sites.toArray()));
            for (int i = 0; i < args.size(); i++) ps.setObject(i + 2, args.get(i));
            return ps;
        }, mapper);
    }

    private <T> Optional<T> one(String sql, RowMapper<T> mapper, Object... args) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(sql, mapper, args));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    private static int bound(int limit) {
        return Math.min(Math.max(limit, 1), 500);
    }

    private static int asInt(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private UUID uuid(ResultSet r, String name) throws SQLException {
        Object v = r.getObject(name);
        return v == null ? null : (UUID) v;
    }

    private Instant instant(ResultSet r, String name) throws SQLException {
        var v = r.getTimestamp(name);
        return v == null ? null : v.toInstant();
    }

    /** Bind an Instant as a UTC OffsetDateTime; the pgjdbc driver cannot infer a type for Instant. */
    private static OffsetDateTime ts(Instant i) {
        return i == null ? null : OffsetDateTime.ofInstant(i, ZoneOffset.UTC);
    }

    private static List<String> csv(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(",")).map(String::strip).filter(s -> !s.isBlank()).toList();
    }

    private String json(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Cannot serialize dispatch record", e);
        }
    }

    private List<String> readList(String value) {
        try {
            return value == null ? List.of()
                    : json.readValue(value, json.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (JacksonException e) {
            throw new IllegalStateException("Invalid stored rule list", e);
        }
    }
}
