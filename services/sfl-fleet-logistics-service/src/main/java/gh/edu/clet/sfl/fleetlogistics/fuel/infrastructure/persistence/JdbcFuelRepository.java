package gh.edu.clet.sfl.fleetlogistics.fuel.infrastructure.persistence;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.RecordMetadata;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import gh.edu.clet.sfl.fleetlogistics.fuel.application.port.FuelRepository;
import gh.edu.clet.sfl.fleetlogistics.fuel.domain.model.DriverLogbook;
import gh.edu.clet.sfl.fleetlogistics.fuel.domain.model.FuelAnomalyCase;
import gh.edu.clet.sfl.fleetlogistics.fuel.domain.model.FuelPolicy;
import gh.edu.clet.sfl.fleetlogistics.fuel.domain.model.FuelTransaction;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Compact JDBC adapter; all business transitions remain in domain/application code.
 *  Instant values are bound as UTC OffsetDateTime because the PostgreSQL JDBC driver cannot
 *  infer a SQL type for java.time.Instant; columns are TIMESTAMPTZ so UTC OffsetDateTime is exact. */
@Repository
public class JdbcFuelRepository implements FuelRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public JdbcFuelRepository(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }

    @Override public FuelPolicy savePolicy(FuelPolicy p) {
        jdbc.update("""
            INSERT INTO fleet_logistics.fuel_policies (id,site_code,policy_name,effective_from,effective_to,policy_version,max_per_transaction,daily_limit,monthly_limit,tank_capacity,min_consumption,max_consumption,odometer_jump_tolerance,receipt_required,receipt_grace_hours,materiality_amount,anomaly_sla_hours,allowed_fuel_products,approved_vendors,status,created_by,created_at,last_modified_by,last_modified_at,source_channel,audit_correlation_id,version)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """, p.id(), p.siteCode().value(), p.name(), ts(p.effectiveFrom()), ts(p.effectiveTo()), p.policyVersion(), p.maxPerTransaction(), p.dailyLimit(), p.monthlyLimit(), p.tankCapacity(), p.minConsumption(), p.maxConsumption(), p.odometerJumpTolerance(), p.receiptRequired(), p.receiptGraceHours(), p.materialityAmount(), p.anomalySlaHours(), String.join(",",p.allowedFuelProducts()), String.join(",",p.approvedVendors()), p.status().name(), p.metadata().createdBy(), ts(p.metadata().createdAt()), p.metadata().lastModifiedBy(), ts(p.metadata().lastModifiedAt()), p.metadata().sourceChannel().name(), p.metadata().auditCorrelationId(), p.metadata().version());
        return p;
    }
    @Override public Optional<FuelPolicy> findApplicablePolicy(String site, Instant at) {
        return one("SELECT * FROM fleet_logistics.fuel_policies WHERE site_code=? AND status='ACTIVE' AND effective_from<=? AND (effective_to IS NULL OR effective_to>?) ORDER BY effective_from DESC,policy_version DESC LIMIT 1", this::policy, site, ts(at), ts(at));
    }
    @Override public List<FuelPolicy> findPolicies(List<String> sites) {
        if (sites.isEmpty()) return List.of(); return jdbc.query("SELECT * FROM fleet_logistics.fuel_policies WHERE site_code = ANY (?) ORDER BY effective_from DESC", ps -> ps.setArray(1, ps.getConnection().createArrayOf("varchar", sites.toArray())), this::policy);
    }

    @Override public FuelTransaction saveTransaction(FuelTransaction t) {
        int updated = jdbc.update("""
            UPDATE fleet_logistics.fuel_transactions SET status=?,lifecycle_status=?,comments=?,receipt_evidence_id=?,last_modified_by=?,last_modified_at=?,source_channel=?,audit_correlation_id=?,version=version+1 WHERE id=? AND version=?
            """, t.status().name(), t.lifecycle().name(), t.comments(), t.receiptEvidenceId(), t.metadata().lastModifiedBy(), ts(t.metadata().lastModifiedAt()), t.metadata().sourceChannel().name(), t.metadata().auditCorrelationId(), t.id(), t.metadata().version());
        if (updated == 0 && findTransaction(t.id()).isEmpty()) {
            jdbc.update("""
                INSERT INTO fleet_logistics.fuel_transactions (id,site_code,provider_transaction_id,source_system,vehicle_id,driver_id,trip_id,occurred_at,vendor_reference,station_reference,fuel_product,quantity,quantity_unit,unit_price,total_cost,currency,masked_card_reference,odometer_reading,receipt_evidence_id,comments,status,lifecycle_status,ingestion_timestamp,idempotency_key,created_by,created_at,last_modified_by,last_modified_at,source_channel,audit_correlation_id,version)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, t.id(),t.siteCode().value(),t.providerTransactionId(),t.sourceSystem(),t.vehicleId(),t.driverId(),t.tripId(),ts(t.occurredAt()),t.vendorReference(),t.stationReference(),t.fuelProduct(),t.quantity(),t.quantityUnit(),t.unitPrice(),t.totalCost(),t.currency().getCurrencyCode(),t.maskedCardReference(),t.odometerReading(),t.receiptEvidenceId(),t.comments(),t.status().name(),t.lifecycle().name(),ts(t.ingestionTimestamp()),t.idempotencyKey(),t.metadata().createdBy(),ts(t.metadata().createdAt()),t.metadata().lastModifiedBy(),ts(t.metadata().lastModifiedAt()),t.metadata().sourceChannel().name(),t.metadata().auditCorrelationId(),t.metadata().version());
        } else if (updated == 0) throw new org.springframework.dao.OptimisticLockingFailureException("FuelTransaction version conflict");
        return findTransaction(t.id()).orElseThrow();
    }
    @Override public Optional<FuelTransaction> findTransaction(UUID id) { return one("SELECT * FROM fleet_logistics.fuel_transactions WHERE id=?", this::transaction, id); }
    @Override public Optional<FuelTransaction> findProviderTransaction(String site, String source, String providerId) { if (providerId == null) return Optional.empty(); return one("SELECT * FROM fleet_logistics.fuel_transactions WHERE site_code=? AND source_system=? AND provider_transaction_id=?",this::transaction,site,source,providerId); }
    @Override public List<FuelTransaction> findTransactions(List<String> sites,String site,FuelTransaction.Status status,UUID vehicle,UUID driver,Instant from,Instant to,int limit) {
        if (sites.isEmpty()) return List.of(); StringBuilder sql=new StringBuilder("SELECT * FROM fleet_logistics.fuel_transactions WHERE site_code = ANY (?)"); List<Object> args=new java.util.ArrayList<>();
        if(site!=null){sql.append(" AND site_code=?");args.add(site);} if(status!=null){sql.append(" AND status=?");args.add(status.name());} if(vehicle!=null){sql.append(" AND vehicle_id=?");args.add(vehicle);} if(driver!=null){sql.append(" AND driver_id=?");args.add(driver);} if(from!=null){sql.append(" AND occurred_at>=?");args.add(ts(from));} if(to!=null){sql.append(" AND occurred_at<?");args.add(ts(to));} sql.append(" ORDER BY occurred_at DESC LIMIT ?");args.add(Math.min(Math.max(limit,1),500));
        return jdbc.query(con -> { var ps=con.prepareStatement(sql.toString()); ps.setArray(1,con.createArrayOf("varchar",sites.toArray())); for(int i=0;i<args.size();i++) ps.setObject(i+2,args.get(i)); return ps; },this::transaction);
    }

    @Override public DriverLogbook saveLogbook(DriverLogbook l) {
        int updated=jdbc.update("UPDATE fleet_logistics.driver_logbooks SET status=?,review_comment=?,transition_reason=?,submitted_at=?,approved_at=?,evidence_id=?,last_modified_by=?,last_modified_at=?,source_channel=?,audit_correlation_id=?,version=version+1 WHERE id=? AND version=?",l.status().name(),l.reviewComment(),l.transitionReason(),ts(l.submittedAt()),ts(l.approvedAt()),l.evidenceId(),l.metadata().lastModifiedBy(),ts(l.metadata().lastModifiedAt()),l.metadata().sourceChannel().name(),l.metadata().auditCorrelationId(),l.id(),l.metadata().version());
        if(updated==0&&findLogbook(l.id()).isEmpty()) jdbc.update("""
            INSERT INTO fleet_logistics.driver_logbooks (id,logbook_number,site_code,driver_id,vehicle_id,trip_id,journey_date,start_time,end_time,origin,destination,route_notes,use_classification,purpose,passenger_load_notes,start_odometer,end_odometer,declaration_accepted,evidence_id,status,review_comment,transition_reason,submitted_at,approved_at,created_by,created_at,last_modified_by,last_modified_at,source_channel,audit_correlation_id,version)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,l.id(),l.logbookNumber(),l.siteCode().value(),l.driverId(),l.vehicleId(),l.tripId(),l.journeyDate(),ts(l.startTime()),ts(l.endTime()),l.origin(),l.destination(),l.routeNotes(),l.useClassification().name(),l.purpose(),l.passengerLoadNotes(),l.startOdometer(),l.endOdometer(),l.declarationAccepted(),l.evidenceId(),l.status().name(),l.reviewComment(),l.transitionReason(),ts(l.submittedAt()),ts(l.approvedAt()),l.metadata().createdBy(),ts(l.metadata().createdAt()),l.metadata().lastModifiedBy(),ts(l.metadata().lastModifiedAt()),l.metadata().sourceChannel().name(),l.metadata().auditCorrelationId(),l.metadata().version());
        else if(updated==0) throw new org.springframework.dao.OptimisticLockingFailureException("DriverLogbook version conflict");
        return findLogbook(l.id()).orElseThrow();
    }
    @Override public Optional<DriverLogbook> findLogbook(UUID id){return one("SELECT * FROM fleet_logistics.driver_logbooks WHERE id=?",this::logbook,id);}
    @Override public List<DriverLogbook> findLogbooks(List<String> sites,String actor,boolean ownOnly,DriverLogbook.Status status,int limit){if(sites.isEmpty())return List.of(); String sql="SELECT * FROM fleet_logistics.driver_logbooks WHERE site_code = ANY (?)"+(ownOnly?" AND created_by=?":"")+(status!=null?" AND status=?":"")+" ORDER BY journey_date DESC LIMIT ?"; return jdbc.query(con->{var ps=con.prepareStatement(sql);int i=1;ps.setArray(i++,con.createArrayOf("varchar",sites.toArray()));if(ownOnly)ps.setString(i++,actor);if(status!=null)ps.setString(i++,status.name());ps.setInt(i,Math.min(Math.max(limit,1),500));return ps;},this::logbook);}

    @Override public FuelAnomalyCase saveAnomaly(FuelAnomalyCase a){int updated=jdbc.update("UPDATE fleet_logistics.fuel_anomaly_cases SET status=?,assignee=?,explanation=?,evidence_id=?,manager_decision=?,closure_reason=?,escalation_level=?,last_modified_by=?,last_modified_at=?,source_channel=?,audit_correlation_id=?,version=version+1 WHERE id=? AND version=?",a.status().name(),a.assignee(),a.explanation(),a.evidenceId(),a.decision()==null?null:a.decision().name(),a.closureReason(),a.escalationLevel(),a.metadata().lastModifiedBy(),ts(a.metadata().lastModifiedAt()),a.metadata().sourceChannel().name(),a.metadata().auditCorrelationId(),a.id(),a.metadata().version()); if(updated==0&&findAnomaly(a.id()).isEmpty())jdbc.update("""
        INSERT INTO fleet_logistics.fuel_anomaly_cases (id,anomaly_number,site_code,transaction_id,logbook_id,vehicle_id,driver_id,trip_id,anomaly_type,severity,material,status,assignee,sla_due_at,explanation,evidence_id,manager_decision,closure_reason,escalation_level,detected_rules,created_by,created_at,last_modified_by,last_modified_at,source_channel,audit_correlation_id,version) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?,?,?,?,?,?,?)
        """,a.id(),a.anomalyNumber(),a.siteCode().value(),a.transactionId(),a.logbookId(),a.vehicleId(),a.driverId(),a.tripId(),a.type().name(),a.severity().name(),a.material(),a.status().name(),a.assignee(),ts(a.slaDueAt()),a.explanation(),a.evidenceId(),a.decision()==null?null:a.decision().name(),a.closureReason(),a.escalationLevel(),json(a.detectedRules()),a.metadata().createdBy(),ts(a.metadata().createdAt()),a.metadata().lastModifiedBy(),ts(a.metadata().lastModifiedAt()),a.metadata().sourceChannel().name(),a.metadata().auditCorrelationId(),a.metadata().version());else if(updated==0)throw new org.springframework.dao.OptimisticLockingFailureException("FuelAnomalyCase version conflict");return findAnomaly(a.id()).orElseThrow();}
    @Override public Optional<FuelAnomalyCase> findAnomaly(UUID id){return one("SELECT * FROM fleet_logistics.fuel_anomaly_cases WHERE id=?",this::anomaly,id);}
    @Override public Optional<FuelAnomalyCase> findAnomaly(UUID tx,FuelAnomalyCase.Type type){return one("SELECT * FROM fleet_logistics.fuel_anomaly_cases WHERE transaction_id=? AND anomaly_type=?",this::anomaly,tx,type.name());}
    @Override public Optional<FuelAnomalyCase> findAnomalyForTrip(UUID trip,FuelAnomalyCase.Type type){return one("SELECT * FROM fleet_logistics.fuel_anomaly_cases WHERE trip_id=? AND transaction_id IS NULL AND anomaly_type=?",this::anomaly,trip,type.name());}
    @Override public List<FuelAnomalyCase> findAnomalies(List<String> sites,FuelAnomalyCase.Status status,Instant due,int limit){if(sites.isEmpty())return List.of();String sql="SELECT * FROM fleet_logistics.fuel_anomaly_cases WHERE site_code = ANY (?)"+(status==null?"":" AND status=?")+(due==null?"":" AND sla_due_at<?")+" ORDER BY sla_due_at LIMIT ?";return jdbc.query(con->{var ps=con.prepareStatement(sql);int i=1;ps.setArray(i++,con.createArrayOf("varchar",sites.toArray()));if(status!=null)ps.setString(i++,status.name());if(due!=null)ps.setObject(i++,ts(due));ps.setInt(i,Math.min(Math.max(limit,1),500));return ps;},this::anomaly);}

    @Override public Optional<FuelTransaction> findPreviousTransaction(String site,UUID vehicle,Instant before){return one("SELECT * FROM fleet_logistics.fuel_transactions WHERE site_code=? AND vehicle_id=? AND occurred_at<? AND lifecycle_status='ACTIVE' ORDER BY occurred_at DESC LIMIT 1",this::transaction,site,vehicle,ts(before));}
    @Override public long countRecentAnomalies(List<String> sites,UUID vehicle,UUID driver,Instant since){if(sites.isEmpty())return 0L;Long count=jdbc.query(con->{var ps=con.prepareStatement("SELECT COUNT(*) FROM fleet_logistics.fuel_anomaly_cases WHERE site_code = ANY (?) AND (vehicle_id=? OR driver_id=?) AND created_at>=?");ps.setArray(1,con.createArrayOf("varchar",sites.toArray()));ps.setObject(2,vehicle);ps.setObject(3,driver);ps.setObject(4,ts(since));return ps;},rs->rs.next()?rs.getLong(1):0L);return count==null?0L:count;}
    @Override public Optional<DriverLogbook> findLogbookForTrip(UUID trip){if(trip==null)return Optional.empty();return one("SELECT * FROM fleet_logistics.driver_logbooks WHERE trip_id=? AND status<>'CANCELLED' ORDER BY journey_date DESC LIMIT 1",this::logbook,trip);}
    @Override public void saveReconciliation(UUID id,UUID tx,UUID policy,Integer version,String outcome,BigDecimal consumption,Instant at,String actor,Map<String,Object> rules,String correlation){jdbc.update("INSERT INTO fleet_logistics.fuel_reconciliations(id,transaction_id,policy_id,policy_version,outcome,calculated_consumption,evaluated_at,evaluated_by,rule_results,correlation_id) VALUES (?,?,?,?,?,?,?,?,?::jsonb,?)",id,tx,policy,version,outcome,consumption,ts(at),actor,json(rules),correlation);}
    @Override public Map<String,Object> dashboard(List<String> sites,String site){if(sites.isEmpty())return Map.of();String sql="SELECT COALESCE(SUM(transaction_count),0),COALESCE(SUM(fuel_volume),0),COALESCE(SUM(fuel_spend),0),COALESCE(SUM(reconciled_count),0),COALESCE(SUM(exception_count),0),MAX(source_updated_at) AS source_updated_at FROM fleet_logistics.fuel_dashboard_summary WHERE site_code = ANY (?)"+(site==null?"":" AND site_code=?");return jdbc.query(con->{var ps=con.prepareStatement(sql);ps.setArray(1,con.createArrayOf("varchar",sites.toArray()));if(site!=null)ps.setString(2,site);return ps;},rs->{if(!rs.next())return Map.of();Map<String,Object> m=new LinkedHashMap<>();m.put("transactionCount",rs.getLong(1));m.put("fuelVolume",rs.getBigDecimal(2));m.put("fuelSpend",rs.getBigDecimal(3));m.put("reconciledCount",rs.getLong(4));m.put("exceptionCount",rs.getLong(5));m.put("sourceUpdatedAt",instant(rs,"source_updated_at"));return m;});}

    private FuelPolicy policy(ResultSet r,int n)throws SQLException{return new FuelPolicy(uuid(r,"id"),SiteCode.of(r.getString("site_code")),r.getString("policy_name"),instant(r,"effective_from"),instant(r,"effective_to"),r.getInt("policy_version"),r.getBigDecimal("max_per_transaction"),r.getBigDecimal("daily_limit"),r.getBigDecimal("monthly_limit"),r.getBigDecimal("tank_capacity"),r.getBigDecimal("min_consumption"),r.getBigDecimal("max_consumption"),r.getLong("odometer_jump_tolerance"),r.getBoolean("receipt_required"),r.getInt("receipt_grace_hours"),r.getBigDecimal("materiality_amount"),r.getInt("anomaly_sla_hours"),csv(r.getString("allowed_fuel_products")),csv(r.getString("approved_vendors")),FuelPolicy.Status.valueOf(r.getString("status")),metadata(r));}
    private FuelTransaction transaction(ResultSet r,int n)throws SQLException{return new FuelTransaction(uuid(r,"id"),SiteCode.of(r.getString("site_code")),r.getString("provider_transaction_id"),r.getString("source_system"),uuid(r,"vehicle_id"),uuid(r,"driver_id"),uuid(r,"trip_id"),instant(r,"occurred_at"),r.getString("vendor_reference"),r.getString("station_reference"),r.getString("fuel_product"),r.getBigDecimal("quantity"),r.getString("quantity_unit"),r.getBigDecimal("unit_price"),r.getBigDecimal("total_cost"),Currency.getInstance(r.getString("currency")),r.getString("masked_card_reference"),r.getLong("odometer_reading"),uuid(r,"receipt_evidence_id"),r.getString("comments"),FuelTransaction.Status.valueOf(r.getString("status")),FuelTransaction.Lifecycle.valueOf(r.getString("lifecycle_status")),instant(r,"ingestion_timestamp"),r.getString("idempotency_key"),metadata(r));}
    private DriverLogbook logbook(ResultSet r,int n)throws SQLException{return new DriverLogbook(uuid(r,"id"),r.getString("logbook_number"),SiteCode.of(r.getString("site_code")),uuid(r,"driver_id"),uuid(r,"vehicle_id"),uuid(r,"trip_id"),r.getObject("journey_date",LocalDate.class),instant(r,"start_time"),instant(r,"end_time"),r.getString("origin"),r.getString("destination"),r.getString("route_notes"),DriverLogbook.UseClassification.valueOf(r.getString("use_classification")),r.getString("purpose"),r.getString("passenger_load_notes"),r.getLong("start_odometer"),(Long)r.getObject("end_odometer"),r.getBoolean("declaration_accepted"),uuid(r,"evidence_id"),DriverLogbook.Status.valueOf(r.getString("status")),r.getString("review_comment"),r.getString("transition_reason"),instant(r,"submitted_at"),instant(r,"approved_at"),metadata(r));}
    private FuelAnomalyCase anomaly(ResultSet r,int n)throws SQLException{return new FuelAnomalyCase(uuid(r,"id"),r.getString("anomaly_number"),SiteCode.of(r.getString("site_code")),uuid(r,"transaction_id"),uuid(r,"logbook_id"),uuid(r,"vehicle_id"),uuid(r,"driver_id"),uuid(r,"trip_id"),FuelAnomalyCase.Type.valueOf(r.getString("anomaly_type")),FuelAnomalyCase.Severity.valueOf(r.getString("severity")),r.getBoolean("material"),FuelAnomalyCase.Status.valueOf(r.getString("status")),r.getString("assignee"),instant(r,"sla_due_at"),r.getString("explanation"),uuid(r,"evidence_id"),r.getString("manager_decision")==null?null:FuelAnomalyCase.Decision.valueOf(r.getString("manager_decision")),r.getString("closure_reason"),r.getInt("escalation_level"),readList(r.getString("detected_rules")),metadata(r));}
    private RecordMetadata metadata(ResultSet r)throws SQLException{return RecordMetadata.rehydrate(r.getString("created_by"),instant(r,"created_at"),r.getString("last_modified_by"),instant(r,"last_modified_at"),r.getLong("version"),SourceChannel.valueOf(r.getString("source_channel")),r.getString("audit_correlation_id"));}
    private <T> Optional<T> one(String sql,org.springframework.jdbc.core.RowMapper<T> mapper,Object...args){try{return Optional.ofNullable(jdbc.queryForObject(sql,mapper,args));}catch(EmptyResultDataAccessException e){return Optional.empty();}}
    private UUID uuid(ResultSet r,String name)throws SQLException{Object v=r.getObject(name);return v==null?null:(UUID)v;}
    private Instant instant(ResultSet r,String name)throws SQLException{var v=r.getTimestamp(name);return v==null?null:v.toInstant();}
    /** Bind an Instant as a UTC OffsetDateTime; the pgjdbc driver cannot infer a type for Instant. */
    private static OffsetDateTime ts(Instant i){return i==null?null:OffsetDateTime.ofInstant(i,ZoneOffset.UTC);}
    private Set<String> csv(String v){return v==null||v.isBlank()?Set.of():Set.copyOf(Arrays.asList(v.split(",")));}
    private String json(Object v){try{return json.writeValueAsString(v);}catch(JacksonException e){throw new IllegalArgumentException("Cannot serialize fuel record",e);}}
    private List<String> readList(String v){try{return v==null?List.of():json.readValue(v,json.getTypeFactory().constructCollectionType(List.class,String.class));}catch(JacksonException e){throw new IllegalStateException("Invalid stored rule list",e);}}
}
