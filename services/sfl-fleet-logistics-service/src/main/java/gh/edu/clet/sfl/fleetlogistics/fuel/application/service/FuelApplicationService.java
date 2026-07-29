package gh.edu.clet.sfl.fleetlogistics.fuel.application.service;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.AuditPort;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.IdempotencyPort;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.IntegrationEventPublisher;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.NotificationPort;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.NotificationPort.NotificationKind;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.event.FleetEventType;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.RecordNotFoundException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.AuditAction;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.AuditEvent;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.RecordMetadata;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import gh.edu.clet.sfl.fleetlogistics.fuel.application.port.FinanceAuditVisibilityPort;
import gh.edu.clet.sfl.fleetlogistics.fuel.application.port.FuelFleetReferencePort;
import gh.edu.clet.sfl.fleetlogistics.fuel.application.port.FuelOutboxAdminPort;
import gh.edu.clet.sfl.fleetlogistics.fuel.application.port.FuelRepository;
import gh.edu.clet.sfl.fleetlogistics.fuel.domain.exception.FuelPolicyPeriodOverlapException;
import gh.edu.clet.sfl.fleetlogistics.fuel.domain.model.DriverLogbook;
import gh.edu.clet.sfl.fleetlogistics.fuel.domain.model.FuelAnomalyCase;
import gh.edu.clet.sfl.fleetlogistics.fuel.domain.model.FuelImportBatch;
import gh.edu.clet.sfl.fleetlogistics.fuel.domain.model.FuelImportRow;
import gh.edu.clet.sfl.fleetlogistics.fuel.domain.model.FuelPolicy;
import gh.edu.clet.sfl.fleetlogistics.fuel.domain.model.FuelReconciliation;
import gh.edu.clet.sfl.fleetlogistics.fuel.domain.model.FuelTransaction;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FuelApplicationService {
    private static final AtomicLong NUMBERS=new AtomicLong();
    /** Documented default cost-variance tolerance (30%) applied when a prior transaction exists; move to versioned policy when a dedicated column is added. */
    private static final BigDecimal COST_VARIANCE_TOLERANCE=new BigDecimal("0.30");
    /** Documented default look-back window (30 days) and count for repeated-anomaly pattern detection. */
    private static final long REPEAT_WINDOW_SECONDS=30L*24*3600;
    private static final long REPEAT_THRESHOLD=3;
    private final FuelRepository repository; private final FuelFleetReferencePort fleet; private final FuelAccessPolicy access;
    private final AuditPort audit; private final IntegrationEventPublisher events; private final IdempotencyPort idempotency; private final Clock clock;
    private final NotificationPort notifications; private final FinanceAuditVisibilityPort financeAudit; private final FuelOutboxAdminPort outboxAdmin;
    public FuelApplicationService(FuelRepository r,FuelFleetReferencePort f,FuelAccessPolicy a,AuditPort audit,IntegrationEventPublisher e,IdempotencyPort i,Clock c,NotificationPort n,FinanceAuditVisibilityPort fa,FuelOutboxAdminPort ox){repository=r;fleet=f;access=a;this.audit=audit;events=e;idempotency=i;clock=c;notifications=n;financeAudit=fa;outboxAdmin=ox;}

    public FuelOutboxAdminPort.OutboxHealth integrationHealth(ActorContext actor){access.requirePermission(actor,SflPermission.FUEL_INTEGRATION_REPLAY,"FuelOutbox");return outboxAdmin.health();}
    @Transactional public boolean replayIntegration(UUID messageId,ActorContext actor,SourceChannel channel){access.requirePermission(actor,SflPermission.FUEL_INTEGRATION_REPLAY,"FuelOutbox");boolean requeued=outboxAdmin.replay(messageId);audit.record(actor,channel,SiteCode.of("SYSTEM"),AuditAction.INTEGRATION_REPLAYED,"FuelOutboxMessage",messageId.toString(),null,Map.of("requeued",requeued));return requeued;}

    public record CreatePolicy(String siteCode,String name,Instant effectiveFrom,Instant effectiveTo,int policyVersion,BigDecimal maxPerTransaction,BigDecimal dailyLimit,BigDecimal monthlyLimit,BigDecimal tankCapacity,BigDecimal minConsumption,BigDecimal maxConsumption,long odometerJumpTolerance,boolean receiptRequired,int receiptGraceHours,BigDecimal materialityAmount,int anomalySlaHours,Set<String> allowedFuelProducts,Set<String> approvedVendors,ActorContext actor,SourceChannel channel){}
    public record CaptureFuel(String siteCode,String providerTransactionId,String sourceSystem,UUID vehicleId,UUID driverId,UUID tripId,Instant occurredAt,String vendorReference,String stationReference,String fuelProduct,BigDecimal quantity,String quantityUnit,BigDecimal unitPrice,BigDecimal totalCost,String currency,String cardReference,long odometerReading,UUID receiptEvidenceId,String comments,String idempotencyKey,ActorContext actor,SourceChannel channel){}
    public record CreateLogbook(String siteCode,UUID driverId,UUID vehicleId,UUID tripId,LocalDate journeyDate,Instant startTime,Instant endTime,String origin,String destination,String routeNotes,DriverLogbook.UseClassification useClassification,String purpose,String passengerLoadNotes,long startOdometer,Long endOdometer,boolean declarationAccepted,UUID evidenceId,ActorContext actor,SourceChannel channel){}

    /**
     * Creates an effective-dated policy, refusing one that overlaps an active policy for the site.
     *
     * <p>The domain model documented "no overlapping active policy for the same scope" as an
     * invariant and nothing enforced it — not the record, which cannot see its siblings, and not the
     * database. With two active policies covering one instant, {@code findApplicablePolicy} returns
     * whichever row the ordering surfaces, so the rules a transaction is judged against and the
     * policy version stamped on its reconciliation stop being reproducible. That defeats the point
     * of an effective-dated policy, so the overlap is refused here, inside the same transaction that
     * writes the record.
     */
    @Transactional public FuelPolicy createPolicy(CreatePolicy c){
        access.require(c.actor(),SflPermission.FUEL_POLICY_MANAGE,c.siteCode(),"FuelPolicy",null);
        requireNoOverlap(SiteCode.of(c.siteCode()).value(),c.effectiveFrom(),c.effectiveTo());
        return persistPolicy(c);
    }

    private void requireNoOverlap(String site,Instant from,Instant to){
        var clashes=repository.findOverlappingActivePolicies(site,from,to,null);
        if(clashes.isEmpty())return;
        throw FuelPolicyPeriodOverlapException.of(site,from,to,clashes.stream()
                .map(p->new FuelPolicyPeriodOverlapException.Conflict(p.id(),p.name(),p.policyVersion(),p.effectiveFrom(),p.effectiveTo()))
                .toList());
    }

    private FuelPolicy persistPolicy(CreatePolicy c){Instant now=clock.instant();var p=new FuelPolicy(UUID.randomUUID(),SiteCode.of(c.siteCode()),c.name(),c.effectiveFrom(),c.effectiveTo(),c.policyVersion(),c.maxPerTransaction(),c.dailyLimit(),c.monthlyLimit(),c.tankCapacity(),c.minConsumption(),c.maxConsumption(),c.odometerJumpTolerance(),c.receiptRequired(),c.receiptGraceHours(),c.materialityAmount(),c.anomalySlaHours(),c.allowedFuelProducts(),c.approvedVendors(),FuelPolicy.Status.ACTIVE,RecordMetadata.createdBy(c.actor().actorId(),now,c.channel(),c.actor().correlationId()));var saved=repository.savePolicy(p);audit.record(c.actor(),c.channel(),saved.siteCode(),AuditAction.CREATE,"FuelPolicy",saved.id().toString(),null,saved);return saved;}

    @Transactional public FuelTransaction capture(CaptureFuel c){SflPermission capturePermission=c.sourceSystem().equalsIgnoreCase("MANUAL")?SflPermission.FUEL_TRANSACTION_CAPTURE:access.has(c.actor(),SflPermission.FUEL_TRANSACTION_IMPORT)?SflPermission.FUEL_TRANSACTION_IMPORT:SflPermission.FUEL_INTEGRATION_INGEST;access.require(c.actor(),capturePermission,c.siteCode(),"FuelTransaction",null);String fp=idempotency.fingerprint(c);Optional<UUID> replay=idempotency.findExistingResult("capture-fuel",c.idempotencyKey(),fp);if(replay.isPresent())return transaction(replay.get(),c.actor());var duplicate=repository.findProviderTransaction(c.siteCode(),c.sourceSystem(),c.providerTransactionId());if(duplicate.isPresent())return duplicate.get();fleet.resolve(c.vehicleId(),c.driverId(),c.tripId(),SiteCode.of(c.siteCode()).value());Instant now=clock.instant();var tx=new FuelTransaction(UUID.randomUUID(),SiteCode.of(c.siteCode()),c.providerTransactionId(),c.sourceSystem(),c.vehicleId(),c.driverId(),c.tripId(),c.occurredAt(),c.vendorReference(),c.stationReference(),c.fuelProduct(),c.quantity(),c.quantityUnit(),c.unitPrice(),c.totalCost(),Currency.getInstance(c.currency().toUpperCase()),c.cardReference(),c.odometerReading(),c.receiptEvidenceId(),c.comments(),FuelTransaction.Status.RECEIVED,FuelTransaction.Lifecycle.ACTIVE,now,c.idempotencyKey(),RecordMetadata.createdBy(c.actor().actorId(),now,c.channel(),c.actor().correlationId()));var saved=repository.saveTransaction(tx);audit.record(c.actor(),c.channel(),saved.siteCode(),AuditAction.CREATE,"FuelTransaction",saved.id().toString(),null,saved);events.publish(FleetEventType.FUEL_TRANSACTION_RECEIVED,"FuelTransaction",saved.id().toString(),saved.siteCode(),c.actor(),Map.of("transactionId",saved.id(),"vehicleId",saved.vehicleId(),"driverId",saved.driverId(),"quantity",saved.quantity(),"currency",saved.currency().getCurrencyCode()));idempotency.recordResult("capture-fuel",c.idempotencyKey(),fp,saved.id(),saved.siteCode().value(),c.actor().actorId());return saved;}

    @Transactional public FuelTransaction reconcile(UUID id,ActorContext actor,SourceChannel channel){var before=transaction(id,actor);access.require(actor,SflPermission.FUEL_RECONCILIATION_RUN,before.siteCode().value(),"FuelTransaction",id.toString());var policy=repository.findApplicablePolicy(before.siteCode().value(),before.occurredAt()).orElseThrow(()->new IllegalStateException("No active fuel policy applies to this transaction"));var snapshot=fleet.resolve(before.vehicleId(),before.driverId(),before.tripId(),before.siteCode().value());Map<String,Object> rules=new LinkedHashMap<>();List<FuelAnomalyCase.Type> failures=new ArrayList<>();check(rules,failures,"MAX_PER_TRANSACTION",before.quantity().compareTo(policy.maxPerTransaction())<=0,FuelAnomalyCase.Type.LIMIT_EXCEEDED);if(policy.tankCapacity()!=null)check(rules,failures,"TANK_CAPACITY",before.quantity().compareTo(policy.tankCapacity())<=0,FuelAnomalyCase.Type.TANK_CAPACITY);check(rules,failures,"FUEL_PRODUCT",policy.allowsProduct(before.fuelProduct()),FuelAnomalyCase.Type.FUEL_PRODUCT);check(rules,failures,"APPROVED_VENDOR",policy.allowsVendor(before.vendorReference()),FuelAnomalyCase.Type.VENDOR);check(rules,failures,"DRIVER_ELIGIBLE","ELIGIBLE".equals(snapshot.driverEligibility()),FuelAnomalyCase.Type.DRIVER_INELIGIBLE);check(rules,failures,"VEHICLE_OPERATIONAL","ACTIVE".equals(snapshot.vehicleLifecycle())&&!"UNAVAILABLE".equals(snapshot.vehicleAvailability()),FuelAnomalyCase.Type.VEHICLE_UNAVAILABLE);check(rules,failures,"TRIP_MATCH",snapshot.tripMatches(),FuelAnomalyCase.Type.OUTSIDE_TRIP);check(rules,failures,"ODOMETER_NON_REGRESSION",before.odometerReading()>=snapshot.acceptedOdometer(),FuelAnomalyCase.Type.ODOMETER_REGRESSION);check(rules,failures,"ODOMETER_JUMP",before.odometerReading()-snapshot.acceptedOdometer()<=policy.odometerJumpTolerance(),FuelAnomalyCase.Type.ODOMETER_JUMP);boolean receiptOk=!policy.receiptRequired()||before.receiptEvidenceId()!=null||before.occurredAt().plusSeconds(policy.receiptGraceHours()*3600L).isAfter(clock.instant());check(rules,failures,"RECEIPT",receiptOk,FuelAnomalyCase.Type.MISSING_RECEIPT);var previous=repository.findPreviousTransaction(before.siteCode().value(),before.vehicleId(),before.occurredAt());BigDecimal consumption=null;if(previous.isPresent()){long km=before.odometerReading()-previous.get().odometerReading();if(km>0){consumption=before.quantity().divide(BigDecimal.valueOf(km),4,RoundingMode.HALF_UP);if(policy.minConsumption()!=null&&policy.maxConsumption()!=null)check(rules,failures,"CONSUMPTION_RANGE",consumption.compareTo(policy.minConsumption())>=0&&consumption.compareTo(policy.maxConsumption())<=0,FuelAnomalyCase.Type.ABNORMAL_CONSUMPTION);}if(previous.get().unitPrice().signum()>0){BigDecimal variance=before.unitPrice().subtract(previous.get().unitPrice()).abs().divide(previous.get().unitPrice(),4,RoundingMode.HALF_UP);check(rules,failures,"COST_VARIANCE",variance.compareTo(COST_VARIANCE_TOLERANCE)<=0,FuelAnomalyCase.Type.COST_VARIANCE);}}if(before.tripId()!=null){var logbook=repository.findLogbookForTrip(before.tripId());if(logbook.isPresent()&&logbook.get().endOdometer()!=null)check(rules,failures,"LOGBOOK_MATCH",Math.abs(before.odometerReading()-logbook.get().endOdometer())<=policy.odometerJumpTolerance(),FuelAnomalyCase.Type.LOGBOOK_MISMATCH);}long recentAnomalies=repository.countRecentAnomalies(List.of(before.siteCode().value()),before.vehicleId(),before.driverId(),clock.instant().minusSeconds(REPEAT_WINDOW_SECONDS));check(rules,failures,"REPEATED_PATTERN",recentAnomalies<REPEAT_THRESHOLD,FuelAnomalyCase.Type.UNUSUAL_PATTERN);Instant now=clock.instant();boolean passed=failures.isEmpty();var after=before.withStatus(passed?FuelTransaction.Status.RECONCILED:FuelTransaction.Status.EXCEPTION,before.metadata().modifiedBy(actor.actorId(),now,channel,actor.correlationId()));after=repository.saveTransaction(after);repository.saveReconciliation(UUID.randomUUID(),id,policy.id(),policy.policyVersion(),after.status().name(),consumption,now,actor.actorId(),rules,actor.correlationId());if(before.odometerReading()>=snapshot.acceptedOdometer()&&before.odometerReading()-snapshot.acceptedOdometer()<=policy.odometerJumpTolerance())fleet.acceptOdometer(before.vehicleId(),before.odometerReading(),before.occurredAt(),actor,channel);for(var failure:failures)createAnomaly(after,policy,failure,List.of(failure.name()),actor,channel);audit.record(actor,channel,after.siteCode(),AuditAction.STATE_TRANSITION,"FuelTransaction",id.toString(),before,after);events.publish(passed?FleetEventType.FUEL_TRANSACTION_RECONCILED:FleetEventType.FUEL_EXCEPTION_DETECTED,"FuelTransaction",id.toString(),after.siteCode(),actor,rules);return after;}

    @Transactional public DriverLogbook createLogbook(CreateLogbook c){access.require(c.actor(),SflPermission.FUEL_LOGBOOK_CREATE,c.siteCode(),"DriverLogbook",null);var refs=fleet.resolve(c.vehicleId(),c.driverId(),c.tripId(),SiteCode.of(c.siteCode()).value());if(access.isDriverOnly(c.actor())&&!c.actor().actorId().equalsIgnoreCase(refs.driverStaffReference()))throw new IllegalStateException("Drivers may create only their own logbooks");Instant now=clock.instant();var l=new DriverLogbook(UUID.randomUUID(),number("LOG"),SiteCode.of(c.siteCode()),c.driverId(),c.vehicleId(),c.tripId(),c.journeyDate(),c.startTime(),c.endTime(),c.origin(),c.destination(),c.routeNotes(),c.useClassification(),c.purpose(),c.passengerLoadNotes(),c.startOdometer(),c.endOdometer(),c.declarationAccepted(),c.evidenceId(),DriverLogbook.Status.DRAFT,null,null,null,null,RecordMetadata.createdBy(c.actor().actorId(),now,c.channel(),c.actor().correlationId()));var saved=repository.saveLogbook(l);audit.record(c.actor(),c.channel(),saved.siteCode(),AuditAction.CREATE,"DriverLogbook",saved.id().toString(),null,saved);return saved;}
    @Transactional public DriverLogbook transitionLogbook(UUID id,String action,String comment,ActorContext actor,SourceChannel channel){var before=logbook(id,actor);SflPermission permission=switch(action){case"submit"->SflPermission.FUEL_LOGBOOK_SUBMIT;case"reopen"->SflPermission.FUEL_LOGBOOK_REOPEN;default->SflPermission.FUEL_LOGBOOK_REVIEW;};access.require(actor,permission,before.siteCode().value(),"DriverLogbook",id.toString());if(access.isDriverOnly(actor)&&!actor.actorId().equalsIgnoreCase(before.metadata().createdBy()))throw new IllegalStateException("Drivers may update only their own logbooks");var meta=before.metadata().modifiedBy(actor.actorId(),clock.instant(),channel,actor.correlationId());var after=switch(action){case"submit"->before.submit(clock.instant(),meta);case"review"->before.startReview(meta);case"return"->before.returned(comment,meta);case"approve"->before.approved(clock.instant(),comment,meta);case"reopen"->before.reopened(comment,meta);case"cancel"->before.cancelled(comment,meta);default->throw new IllegalArgumentException("Unknown logbook transition");};after=repository.saveLogbook(after);audit.record(actor,channel,after.siteCode(),AuditAction.STATE_TRANSITION,"DriverLogbook",id.toString(),before,after);FleetEventType event=switch(action){case"submit"->FleetEventType.DRIVER_LOGBOOK_SUBMITTED;case"return"->FleetEventType.DRIVER_LOGBOOK_RETURNED;case"approve"->FleetEventType.DRIVER_LOGBOOK_APPROVED;default->null;};if(event!=null)events.publish(event,"DriverLogbook",id.toString(),after.siteCode(),actor,Map.of("logbookId",id,"status",after.status()));notifyLogbook(action,after);return after;}
    private void notifyLogbook(String action,DriverLogbook l){Map<String,String> ctx=logbookContext(l);switch(action){case"submit"->notifications.notifyRole(l.siteCode(),SflRole.FLEET_MANAGER,NotificationKind.WORK_ASSIGNED,l.logbookNumber(),ctx);case"return"->notifications.notifyAssignee(l.siteCode(),l.metadata().createdBy(),NotificationKind.WORK_BLOCKED,l.logbookNumber(),ctx);case"approve"->notifications.notifyAssignee(l.siteCode(),l.metadata().createdBy(),NotificationKind.WORK_ASSIGNED,l.logbookNumber(),ctx);default->{}}}
    private static Map<String,String> logbookContext(DriverLogbook l){Map<String,String> m=new LinkedHashMap<>();m.put("logbookNumber",l.logbookNumber());m.put("status",l.status().name());if(l.tripId()!=null)m.put("tripId",l.tripId().toString());if(l.driverId()!=null)m.put("driverId",l.driverId().toString());if(l.metadata().auditCorrelationId()!=null)m.put("correlationId",l.metadata().auditCorrelationId());return m;}

    @Transactional public FuelAnomalyCase transitionAnomaly(UUID id,String action,String value,UUID evidence,ActorContext actor,SourceChannel channel){
        var before=anomaly(id,actor);
        SflPermission p=Set.of("approve","reject","close").contains(action)?SflPermission.FUEL_ANOMALY_APPROVE:action.equals("escalate")?SflPermission.FUEL_ANOMALY_ESCALATE:SflPermission.FUEL_ANOMALY_MANAGE;
        access.require(actor,p,before.siteCode().value(),"FuelAnomalyCase",id.toString());
        var meta=before.metadata().modifiedBy(actor.actorId(),clock.instant(),channel,actor.correlationId());
        var after=switch(action){
            case"assign"->before.assign(value,meta);
            case"reassign"->before.reassign(value,meta);
            case"review"->before.review(meta);
            case"request-explanation"->before.requestExplanation(meta);
            case"explain"->before.explain(value,evidence,meta);
            case"approve"->before.decide(FuelAnomalyCase.Decision.APPROVED,value,meta);
            case"reject"->before.decide(FuelAnomalyCase.Decision.REJECTED,value,meta);
            case"escalate"->before.escalate(value,meta);
            case"hold"->before.hold(value,meta);
            case"resume"->before.resume(meta);
            case"cancel"->before.cancel(value,meta);
            case"close"->before.close(value,evidence,meta);
            case"reopen"->before.reopen(value,meta);
            default->throw new IllegalArgumentException("Unknown anomaly transition");
        };
        after=repository.saveAnomaly(after);
        audit.record(actor,channel,after.siteCode(),anomalyAuditAction(action),"FuelAnomalyCase",id.toString(),before,after);
        notifyAnomaly(action,after);
        FleetEventType event=switch(action){case"assign","reassign"->FleetEventType.FUEL_ANOMALY_ASSIGNED;case"approve"->FleetEventType.FUEL_ANOMALY_APPROVED;case"reject"->FleetEventType.FUEL_ANOMALY_REJECTED;case"escalate"->FleetEventType.FUEL_ANOMALY_ESCALATED;default->null;};
        if(event!=null)events.publish(event,"FuelAnomalyCase",id.toString(),after.siteCode(),actor,Map.of("anomalyId",id,"status",after.status()));
        if(action.equals("escalate")&&after.material())financeAudit.surfaceMaterialException(after,actor);
        return after;
    }
    private static AuditAction anomalyAuditAction(String action){return switch(action){case"assign"->AuditAction.ASSIGN;case"reassign"->AuditAction.REASSIGN;case"hold"->AuditAction.HOLD;case"resume"->AuditAction.RESUME;case"escalate"->AuditAction.ESCALATE;case"cancel"->AuditAction.CANCEL;case"close"->AuditAction.CLOSE;case"reopen"->AuditAction.REOPEN;default->AuditAction.STATE_TRANSITION;};}
    private void notifyAnomaly(String action,FuelAnomalyCase a){switch(action){
        case"assign","reassign"->{if(a.assignee()!=null)notifications.notifyAssignee(a.siteCode(),a.assignee(),NotificationKind.WORK_ASSIGNED,a.anomalyNumber(),anomalyContext(a));}
        case"escalate"->notifications.notifyRole(a.siteCode(),SflRole.FLEET_MANAGER,NotificationKind.WORK_ESCALATED,a.anomalyNumber(),anomalyContext(a));
        case"hold"->{if(a.assignee()!=null)notifications.notifyAssignee(a.siteCode(),a.assignee(),NotificationKind.WORK_BLOCKED,a.anomalyNumber(),anomalyContext(a));}
        default->{}
    }}
    private static Map<String,String> anomalyContext(FuelAnomalyCase a){Map<String,String> m=new LinkedHashMap<>();m.put("anomalyNumber",a.anomalyNumber());m.put("type",a.type().name());m.put("severity",a.severity().name());m.put("status",a.status().name());m.put("material",Boolean.toString(a.material()));if(a.transactionId()!=null)m.put("transactionId",a.transactionId().toString());if(a.vehicleId()!=null)m.put("vehicleId",a.vehicleId().toString());if(a.driverId()!=null)m.put("driverId",a.driverId().toString());if(a.metadata().auditCorrelationId()!=null)m.put("correlationId",a.metadata().auditCorrelationId());return m;}

    @Transactional public FuelTransaction voidTransaction(UUID id,String reason,ActorContext actor,SourceChannel channel){var before=transaction(id,actor);access.require(actor,SflPermission.FUEL_TRANSACTION_VOID,before.siteCode().value(),"FuelTransaction",id.toString());var after=repository.saveTransaction(before.voided(reason,before.metadata().modifiedBy(actor.actorId(),clock.instant(),channel,actor.correlationId())));audit.record(actor,channel,after.siteCode(),AuditAction.CANCEL,"FuelTransaction",id.toString(),before,after);return after;}
    public FuelTransaction transaction(UUID id,ActorContext actor){var t=repository.findTransaction(id).orElseThrow(()->RecordNotFoundException.of("FuelTransaction",id));access.require(actor,SflPermission.FUEL_TRANSACTION_READ,t.siteCode().value(),"FuelTransaction",id.toString());return t;}
    public DriverLogbook logbook(UUID id,ActorContext actor){var l=repository.findLogbook(id).orElseThrow(()->RecordNotFoundException.of("DriverLogbook",id));access.require(actor,SflPermission.FUEL_LOGBOOK_READ,l.siteCode().value(),"DriverLogbook",id.toString());return l;}
    public FuelAnomalyCase anomaly(UUID id,ActorContext actor){var a=repository.findAnomaly(id).orElseThrow(()->RecordNotFoundException.of("FuelAnomalyCase",id));access.require(actor,SflPermission.FUEL_ANOMALY_READ,a.siteCode().value(),"FuelAnomalyCase",id.toString());return a;}
    /* ---------------------------------------------------------------------------- paged reads */

    public FuelRepository.FuelPage<FuelTransaction> transactions(String site,FuelTransaction.Status status,UUID vehicle,UUID driver,String source,String vendor,Instant from,Instant to,FuelRepository.Paging paging,ActorContext actor){
        access.require(actor,SflPermission.FUEL_TRANSACTION_READ,site,"FuelTransaction",null);
        return repository.findTransactions(new FuelRepository.TransactionQuery(List.of(SiteCode.of(site).value()),site,status,vehicle,driver,source,vendor,from,to,paging));
    }

    /**
     * A driver-only actor is restricted to their own records here, not in the dashboard.
     *
     * <p>Scoping a query by what the caller may see belongs on this side of the wire: a client-side
     * filter is a display convention, and the records would still have crossed the boundary.
     */
    public FuelRepository.FuelPage<DriverLogbook> logbooks(String site,DriverLogbook.Status status,UUID driver,UUID vehicle,DriverLogbook.UseClassification use,LocalDate journeyFrom,LocalDate journeyTo,FuelRepository.Paging paging,ActorContext actor){
        access.require(actor,SflPermission.FUEL_LOGBOOK_READ,site,"DriverLogbook",null);
        return repository.findLogbooks(new FuelRepository.LogbookQuery(List.of(SiteCode.of(site).value()),actor.actorId(),access.isDriverOnly(actor),status,driver,vehicle,use,journeyFrom,journeyTo,paging));
    }

    /**
     * The anomaly queue, filtered server-side.
     *
     * <p>{@code dueBefore} is what makes "breaching SLA" a query rather than a guess: the sweep
     * scheduler already used it, and until now nothing else could reach it.
     */
    public FuelRepository.FuelPage<FuelAnomalyCase> anomalies(String site,FuelAnomalyCase.Status status,FuelAnomalyCase.Type type,FuelAnomalyCase.Severity severity,String assignee,Boolean unassigned,Boolean material,Boolean openOnly,Instant dueBefore,UUID transactionId,FuelRepository.Paging paging,ActorContext actor){
        access.require(actor,SflPermission.FUEL_ANOMALY_READ,site,"FuelAnomalyCase",null);
        return repository.findAnomalies(new FuelRepository.AnomalyQuery(List.of(SiteCode.of(site).value()),status,type,severity,assignee,unassigned,material,openOnly,dueBefore,transactionId,null,null,paging));
    }

    public FuelRepository.FuelPage<FuelPolicy> policies(String site,FuelPolicy.Status status,boolean inForceOnly,FuelRepository.Paging paging,ActorContext actor){
        access.require(actor,SflPermission.FUEL_POLICY_READ,site,"FuelPolicy",null);
        return repository.findPolicies(new FuelRepository.PolicyQuery(List.of(SiteCode.of(site).value()),status,inForceOnly?clock.instant():null,paging));
    }

    public FuelPolicy policy(UUID id,ActorContext actor){
        var p=repository.findPolicy(id).orElseThrow(()->RecordNotFoundException.of("FuelPolicy",id));
        access.require(actor,SflPermission.FUEL_POLICY_READ,p.siteCode().value(),"FuelPolicy",id.toString());
        return p;
    }

    /**
     * Every reconciliation run against the transaction, newest first.
     *
     * <p>The rows were written from the first release and readable from none of it, so a screen could
     * report that a transaction failed but never which rules it passed. Reading them is what makes a
     * decision reproducible: the policy version it was judged against and the full rule result map.
     */
    public List<FuelReconciliation> reconciliations(UUID transactionId,ActorContext actor){
        var tx=transaction(transactionId,actor);
        access.require(actor,SflPermission.FUEL_TRANSACTION_READ,tx.siteCode().value(),"FuelReconciliation",transactionId.toString());
        return repository.findReconciliations(transactionId);
    }

    public FuelRepository.FuelPage<FuelImportBatch> importBatches(String site,String source,FuelRepository.Paging paging,ActorContext actor){
        access.require(actor,SflPermission.FUEL_TRANSACTION_READ,site,"FuelImportBatch",null);
        return repository.findImportBatches(new FuelRepository.ImportQuery(List.of(SiteCode.of(site).value()),source,paging));
    }

    public FuelImportBatch importBatch(UUID id,ActorContext actor){
        var batch=repository.findImportBatch(id).orElseThrow(()->RecordNotFoundException.of("FuelImportBatch",id));
        access.require(actor,SflPermission.FUEL_TRANSACTION_READ,batch.siteCode().value(),"FuelImportBatch",id.toString());
        return batch;
    }

    /**
     * The audit trail for one fuel record.
     *
     * <p>Every fuel state change was already written through {@link AuditPort}; what was missing was
     * a way to read it back against a single record. The read is authorised against the record
     * itself, so a caller cannot enumerate another site's history through it.
     */
    public List<AuditEvent> history(String resourceType,UUID id,ActorContext actor){
        String site=switch(resourceType){
            case "FuelTransaction"->transaction(id,actor).siteCode().value();
            case "DriverLogbook"->logbook(id,actor).siteCode().value();
            case "FuelAnomalyCase"->anomaly(id,actor).siteCode().value();
            case "FuelPolicy"->policy(id,actor).siteCode().value();
            default->throw new IllegalArgumentException("Unknown fuel resource type");
        };
        return audit.search(new AuditPort.AuditQuery(List.of(site),resourceType,id.toString(),null,null,null,null,0,200));
    }

    /**
     * One batch's rows, paged.
     *
     * <p>The detail read returns every row, which is fine for a hundred and not for a file with
     * thousands.
     */
    public FuelRepository.FuelPage<FuelImportRow> importRows(UUID id,FuelRepository.Paging paging,ActorContext actor){
        // importBatch already authorises against the batch's own site, so the rows inherit that check
        // rather than repeating it with a permission that does not exist.
        importBatch(id,actor);
        return repository.findImportRows(id,paging);
    }

    /**
     * Fuel spend and volume by day, aggregated by the service.
     *
     * <p>The dashboard chart bucketed this in the browser from a page of fetched transactions, so it
     * described that page rather than the site. Aggregated in SQL it describes the site, and the
     * screen can stop captioning it as derived.
     */
    public List<FuelRepository.DailyFuelTotals> dailyTotals(String site,Instant from,Instant to,ActorContext actor){
        access.require(actor,SflPermission.FUEL_REPORT_READ,site,"FuelDashboard",null);
        return repository.dailyTotals(List.of(SiteCode.of(site).value()),site,from,to);
    }

    /** Open anomaly counts by type, so a by-type chart stops reading a page of records. */
    public Map<String,Long> anomalyCountsByType(String site,ActorContext actor){
        access.require(actor,SflPermission.FUEL_REPORT_READ,site,"FuelDashboard",null);
        return repository.anomalyCountsByType(List.of(SiteCode.of(site).value()),site);
    }

    public Map<String,Object> dashboard(String site,ActorContext actor){
        access.require(actor,SflPermission.FUEL_REPORT_READ,site,"FuelDashboard",null);
        Instant now=clock.instant();
        var result=new LinkedHashMap<>(repository.dashboard(List.of(SiteCode.of(site).value()),site,now));
        Instant updated=(Instant)result.get("sourceUpdatedAt");
        result.put("stale",updated==null||updated.isBefore(now.minusSeconds(900)));
        return result;
    }

    public String transactionReportCsv(String site,ActorContext actor){
        access.require(actor,SflPermission.FUEL_REPORT_EXPORT,site,"FuelTransactionReport",null);
        StringBuilder csv=new StringBuilder("transactionId,occurredAt,vehicleId,driverId,product,quantity,unit,totalCost,currency,status,vendor,receiptEvidenceId\r\n");
        var window=new FuelRepository.TransactionQuery(List.of(SiteCode.of(site).value()),site,null,null,null,null,null,null,null,
                new FuelRepository.Paging(0,FuelRepository.Paging.MAX_SIZE,"occurredAt,desc"));
        for(var t:repository.findTransactions(window).content()){
            csv.append(t.id()).append(',').append(t.occurredAt()).append(',').append(t.vehicleId()).append(',')
                    .append(t.driverId()).append(',').append(csv(t.fuelProduct())).append(',').append(t.quantity())
                    .append(',').append(csv(t.quantityUnit())).append(',').append(t.totalCost()).append(',')
                    .append(t.currency().getCurrencyCode()).append(',').append(t.status()).append(',')
                    .append(csv(t.vendorReference())).append(',').append(t.receiptEvidenceId()==null?"":t.receiptEvidenceId())
                    .append("\r\n");
        }
        return csv.toString();
    }

    @Transactional public FuelAnomalyCase raiseMissingLogbook(String site,UUID tripId,UUID vehicleId,UUID driverId,
            ActorContext actor,SourceChannel channel){
        var existing=repository.findAnomalyForTrip(tripId,FuelAnomalyCase.Type.MISSING_LOGBOOK);
        if(existing.isPresent())return existing.get();
        access.require(actor,SflPermission.FUEL_ANOMALY_MANAGE,site,"FuelAnomalyCase",null);
        Instant now=clock.instant();int sla=repository.findApplicablePolicy(SiteCode.of(site).value(),now)
                .map(FuelPolicy::anomalySlaHours).orElse(24);
        var anomaly=new FuelAnomalyCase(UUID.randomUUID(),number("ANM"),SiteCode.of(site),null,null,vehicleId,
                driverId,tripId,FuelAnomalyCase.Type.MISSING_LOGBOOK,FuelAnomalyCase.Severity.MEDIUM,false,
                FuelAnomalyCase.Status.DETECTED,null,now.plusSeconds(sla*3600L),null,null,null,null,0,
                List.of("COMPLETED_TRIP_WITHOUT_LOGBOOK"),RecordMetadata.createdBy(actor.actorId(),now,channel,
                        actor.correlationId()));
        anomaly=repository.saveAnomaly(anomaly);
        audit.record(actor,channel,anomaly.siteCode(),AuditAction.CREATE,"FuelAnomalyCase",anomaly.id().toString(),null,anomaly);
        events.publish(FleetEventType.DRIVER_LOGBOOK_OVERDUE,"DriverLogbook",tripId.toString(),anomaly.siteCode(),
                actor,Map.of("tripId",tripId,"driverId",driverId,"vehicleId",vehicleId,"anomalyId",anomaly.id()));
        return anomaly;
    }

    private void createAnomaly(FuelTransaction tx,FuelPolicy p,FuelAnomalyCase.Type type,List<String> rules,ActorContext actor,SourceChannel channel){if(repository.findAnomaly(tx.id(),type).isPresent())return;Instant now=clock.instant();boolean material=tx.totalCost().compareTo(p.materialityAmount())>=0;var severity=material?FuelAnomalyCase.Severity.HIGH:FuelAnomalyCase.Severity.MEDIUM;var a=new FuelAnomalyCase(UUID.randomUUID(),number("ANM"),tx.siteCode(),tx.id(),null,tx.vehicleId(),tx.driverId(),tx.tripId(),type,severity,material,FuelAnomalyCase.Status.DETECTED,null,now.plusSeconds(p.anomalySlaHours()*3600L),null,null,null,null,0,rules,RecordMetadata.createdBy(actor.actorId(),now,channel,actor.correlationId()));a=repository.saveAnomaly(a);audit.record(actor,channel,a.siteCode(),AuditAction.CREATE,"FuelAnomalyCase",a.id().toString(),null,a);events.publish(FleetEventType.FUEL_EXCEPTION_DETECTED,"FuelAnomalyCase",a.id().toString(),a.siteCode(),actor,Map.of("anomalyId",a.id(),"type",a.type(),"material",a.material()));notifications.notifyRole(a.siteCode(),SflRole.FLEET_MANAGER,NotificationKind.WORK_ASSIGNED,a.anomalyNumber(),anomalyContext(a));if(material)financeAudit.surfaceMaterialException(a,actor);}
    private static void check(Map<String,Object> results,List<FuelAnomalyCase.Type> failures,String rule,boolean passed,FuelAnomalyCase.Type type){results.put(rule,Map.of("passed",passed));if(!passed&&!failures.contains(type))failures.add(type);}
    private static String number(String prefix){return prefix+"-"+Instant.now().toEpochMilli()+"-"+NUMBERS.incrementAndGet();}
    private static String csv(String value){return "\""+String.valueOf(value).replace("\"","\"\"")+"\"";}
}
