package gh.edu.clet.sfl.facilities.maintenance.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.maintenance.application.ports.MaintenanceRepository;
import gh.edu.clet.sfl.facilities.maintenance.domain.FacilityFault;
import gh.edu.clet.sfl.facilities.maintenance.domain.WorkOrder;
import gh.edu.clet.sfl.facilities.maintenance.domain.policy.SlaPolicy;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The SLA sweep — SRS-SFL-S153-02.
 *
 * <blockquote>"Given an SLA threshold is breached, when the scheduled evaluation runs, then the
 * system escalates the item and notifies the configured role."</blockquote>
 *
 * <h2>Three things this class is deliberate about</h2>
 *
 * <p><strong>It reads the configuration on every run.</strong> The requirement says escalation must
 * be evaluated "using the runtime configuration active at the time of evaluation", so the ladder is
 * fetched per site per run rather than held in a field. A rule tightened this morning applies at the
 * next sweep, not at the next deployment. The per-site policies are cached for the duration of one
 * run only — long enough to avoid a configuration read per work order, short enough that the
 * guarantee holds.
 *
 * <p><strong>It is idempotent.</strong> The level is a pure function of the deadline and the clock
 * ({@link SlaPolicy#escalationLevelFor}), and the aggregates refuse to move backwards, so running the
 * sweep twice in a minute escalates nothing twice. That matters because a scheduler is at-least-once
 * and because each escalation publishes a notification: a non-idempotent sweep would page the same
 * manager every time it ran.
 *
 * <p><strong>It does not notify.</strong> It publishes {@code ifimp.work-order.escalated} and
 * {@code ifimp.facility-fault.escalated} to the outbox and stops. Delivering to a person is the
 * notification service's job, and building a second notifier here would be a second place for
 * CLET's escalation contact list to be wrong.
 */
@Service
public class MaintenanceEscalationService {

    /** A bound on one sweep, so a backlog cannot turn into an unbounded transaction. */
    private static final int SWEEP_LIMIT = 500;

    private final MaintenanceRepository maintenance;
    private final MaintenanceConfiguration configuration;
    private final FacilityFaultService faults;
    private final WorkOrderApplicationService workOrders;
    private final Clock clock;

    public MaintenanceEscalationService(MaintenanceRepository maintenance,
            MaintenanceConfiguration configuration, FacilityFaultService faults,
            WorkOrderApplicationService workOrders, Clock clock) {
        this.maintenance = maintenance;
        this.configuration = configuration;
        this.faults = faults;
        this.workOrders = workOrders;
        this.clock = clock;
    }

    /**
     * Escalates everything past its deadline.
     *
     * @param systemActor the actor the escalations are recorded against. Not a person, and the audit
     *        trail says so through {@link SourceChannel#SCHEDULER}.
     * @return what moved, so a caller — a test, or an operator triggering the sweep by hand — can see
     *         the effect rather than inferring it from the log.
     */
    @Transactional
    public EscalationSweep sweep(ActorContext systemActor) {
        Instant now = clock.instant();
        Map<String, SlaPolicy> policies = new HashMap<>();
        int faultsEscalated = 0;
        int workOrdersEscalated = 0;

        for (FacilityFault fault : maintenance.findOverdueFaults(now, SWEEP_LIMIT)) {
            // A fault with work booked against it is chased through that work order, which has its own
            // deadline and its own assignee. Escalating both would notify two people about one
            // problem, and the fastest way to make an escalation ignored is to send it twice.
            if (fault.workOrderId() != null) {
                continue;
            }
            SlaPolicy policy = policies.computeIfAbsent(fault.siteCode(), configuration::slaPolicyFor);
            int level = policy.escalationLevelFor(fault.slaDueAt(), now);
            if (level > fault.escalationLevel()) {
                faults.applyEscalation(fault, level, systemActor, SourceChannel.SCHEDULER);
                faultsEscalated++;
            }
        }

        for (WorkOrder order : maintenance.findOverdueWorkOrders(now, SWEEP_LIMIT)) {
            SlaPolicy policy = policies.computeIfAbsent(order.siteCode(), configuration::slaPolicyFor);
            int level = policy.escalationLevelFor(order.slaDueAt(), now);
            if (level > order.escalationLevel()) {
                workOrders.applyEscalation(order, level, systemActor, SourceChannel.SCHEDULER);
                workOrdersEscalated++;
            }
        }

        return new EscalationSweep(now, faultsEscalated, workOrdersEscalated);
    }

    /** What one sweep did. */
    public record EscalationSweep(Instant evaluatedAt, int faultsEscalated, int workOrdersEscalated) {

        public int total() {
            return faultsEscalated + workOrdersEscalated;
        }
    }

    /** Overdue items without escalating them — for a dashboard, or for a dry run. */
    @Transactional(readOnly = true)
    public List<WorkOrder> overdueWorkOrders() {
        return maintenance.findOverdueWorkOrders(clock.instant(), SWEEP_LIMIT);
    }
}
