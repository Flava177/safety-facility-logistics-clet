package gh.edu.clet.sfl.facilities.maintenance.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.common.security.SiteScopedPrincipal;
import gh.edu.clet.sfl.facilities.maintenance.application.ports.MaintenanceRepository;
import gh.edu.clet.sfl.facilities.maintenance.domain.FacilityFault;
import gh.edu.clet.sfl.facilities.maintenance.domain.FacilityFaultStatus;
import gh.edu.clet.sfl.facilities.maintenance.domain.FaultPriority;
import gh.edu.clet.sfl.facilities.maintenance.domain.WorkOrder;
import gh.edu.clet.sfl.facilities.maintenance.domain.WorkOrderStatus;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.model.EstateCodes;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one door through which another IFIMP module raises S153 work without a person reporting it.
 *
 * <p>Three Phase 2 systems need it and each for a different reason: S156 when a building system
 * breaches a rule long enough to be real, S173 when an event needs pre-event maintenance, and S176
 * when a defect is found during a project's liability period. All three would otherwise re-derive
 * the S153 sequence - report, triage, cut the order - and each would get a different part of it
 * wrong. So it lives here, once, beside the services it drives.
 *
 * <h2>Why it goes through a fault</h2>
 *
 * <p>S153 only cuts work orders from faults. That is not ceremony: the fault is what holds the
 * readiness blocker on the room, carries the SLA clock and appears in the fault register a supervisor
 * reviews. A work order that appeared without one would fix the air-conditioning while the hall still
 * read "ready" to everybody else.
 *
 * <h2>Who raises it</h2>
 *
 * <p>A platform service account, not the calling user. The calling module has already authorised its
 * own actor for its own act - an event coordinator requesting maintenance holds
 * {@code FACILITIES_EVENT_COORDINATE}, not {@code FACILITIES_WORK_ORDER_CREATE}, and should not need
 * to. Who asked is recorded in the fault's description and in the {@code requestedBy} the origin module
 * keeps, so the audit trail answers "who wanted this" as well as "what raised it".
 *
 * <h2>Replay</h2>
 *
 * <p>The idempotency key is the caller's and must be derived from the subject, not the message -
 * {@code bms-alert:<alertId>}, {@code construction-defect:<defectId>}. The same key twice returns
 * the same fault and the same work order, which is what makes a retried scheduler run harmless.
 */
@Service
public class AutomatedWorkOrderIntake {

    /** Recorded on every fault and work order this raises, so an auditor can tell them from staff reports. */
    static final SiteScopedPrincipal PLATFORM = new SiteScopedPrincipal("system.ifimp-automation",
            "IFIMP automation", Set.of(SflRole.SFL_ADMIN), Set.of("*"), true);

    private final FacilityFaultService faults;
    private final WorkOrderApplicationService workOrders;
    private final MaintenanceRepository maintenance;

    public AutomatedWorkOrderIntake(FacilityFaultService faults, WorkOrderApplicationService workOrders,
            MaintenanceRepository maintenance) {
        this.faults = faults;
        this.workOrders = workOrders;
        this.maintenance = maintenance;
    }

    /**
     * Raises - or, for a key already used, returns - the fault and work order for this request.
     *
     * <p>One transaction with the caller's, so an origin module that records "work order raised" and
     * then fails rolls the work order back with it rather than leaving an orphan in the queue.
     */
    @Transactional
    public RaisedWorkOrder raise(AutomatedWorkOrderRequest request) {
        Objects.requireNonNull(request, "request is required");
        ActorContext platform = new ActorContext(PLATFORM, request.correlationId() == null
                ? UUID.randomUUID().toString()
                : request.correlationId());

        FacilityFault fault = faults.report(new MaintenanceCommands.ReportFault(
                request.siteCode(), request.roomId(), request.locationCode(), request.assetId(),
                request.title(), describe(request), request.category(), request.priority(), platform,
                request.channel(), "auto-fault:" + request.idempotencyKey(),
                Map.of("origin", request.originModule(), "reference", request.originReference())));

        Optional<WorkOrder> existing = maintenance.findWorkOrderForFault(fault.id());
        if (existing.isPresent()) {
            return RaisedWorkOrder.of(fault, existing.get());
        }
        if (fault.status() == FacilityFaultStatus.REPORTED) {
            fault = faults.triage(new MaintenanceCommands.TriageFault(fault.id(), request.priority(),
                    "Triaged automatically on behalf of " + request.originModule() + " ("
                            + request.originReference() + ").",
                    null, platform, request.channel()));
        }
        WorkOrder order = workOrders.createFromFault(new MaintenanceCommands.CreateWorkOrderFromFault(
                fault.id(), request.vendorId(), request.assignTo(), platform, request.channel(),
                "auto-work-order:" + request.idempotencyKey(), request.originReference()));
        return RaisedWorkOrder.of(fault, order);
    }

    /** The current state of a work order this intake raised, for correlation and close-out checks. */
    @Transactional(readOnly = true)
    public Optional<RaisedWorkOrder> find(UUID workOrderId) {
        return maintenance.findWorkOrder(workOrderId)
                .flatMap(order -> maintenance.findFault(order.facilityFaultId())
                        .map(fault -> RaisedWorkOrder.of(fault, order)));
    }

    private static String describe(AutomatedWorkOrderRequest request) {
        StringBuilder text = new StringBuilder();
        if (request.description() != null && !request.description().isBlank()) {
            text.append(request.description().strip()).append("\n\n");
        }
        text.append("Raised automatically by ").append(request.originModule())
                .append(". Origin reference: ").append(request.originReference()).append('.');
        if (request.evidenceReference() != null && !request.evidenceReference().isBlank()) {
            text.append(" Evidence: ").append(request.evidenceReference().strip()).append('.');
        }
        if (request.requestedBy() != null && !request.requestedBy().isBlank()) {
            text.append(" Requested by ").append(request.requestedBy().strip()).append('.');
        }
        return text.toString();
    }

    /**
     * What an origin module asks S153 for.
     *
     * @param category the classification an S153 reader filters on - {@code BMS_TELEMETRY},
     *        {@code EVENT_PRE_MAINTENANCE}, {@code CONSTRUCTION_DEFECT}. It is how a defects-liability
     *        job is told apart from ordinary maintenance in the register (SRS-SFL-S176-04)
     * @param originModule the SRS system that raised it, {@code S156} / {@code S173} / {@code S176}
     * @param originReference the origin module's own record id, held here by value
     * @param evidenceReference what justifies the work - a telemetry reading id, a snag photograph
     *        reference. Carried into the fault so the technician sees it (SRS-SFL-S156-02)
     * @param idempotencyKey derived from the subject, never from a message id
     */
    public record AutomatedWorkOrderRequest(
            String siteCode,
            UUID roomId,
            String locationCode,
            UUID assetId,
            String title,
            String description,
            String category,
            FaultPriority priority,
            String originModule,
            String originReference,
            String evidenceReference,
            String requestedBy,
            UUID vendorId,
            String assignTo,
            SourceChannel channel,
            String correlationId,
            String idempotencyKey) {

        public AutomatedWorkOrderRequest {
            siteCode = EstateCodes.normalize(siteCode);
            EstateCodes.require(title, "title");
            EstateCodes.require(category, "category");
            Objects.requireNonNull(priority, "priority is required");
            EstateCodes.require(originModule, "originModule");
            EstateCodes.require(originReference, "originReference");
            EstateCodes.require(idempotencyKey, "idempotencyKey");
            channel = channel == null ? SourceChannel.SYSTEM : channel;
        }
    }

    /** The fault and work order an intake request resolved to. */
    public record RaisedWorkOrder(
            UUID faultId,
            String faultNumber,
            UUID workOrderId,
            String workOrderNumber,
            WorkOrderStatus status,
            String siteCode) {

        static RaisedWorkOrder of(FacilityFault fault, WorkOrder order) {
            return new RaisedWorkOrder(fault.id(), fault.faultNumber(), order.id(), order.workOrderNumber(),
                    order.status(), order.siteCode());
        }

        /** Still in somebody's queue - open, assigned, in progress, on hold or awaiting closure. */
        public boolean isOpen() {
            return status.isOpen();
        }
    }
}
