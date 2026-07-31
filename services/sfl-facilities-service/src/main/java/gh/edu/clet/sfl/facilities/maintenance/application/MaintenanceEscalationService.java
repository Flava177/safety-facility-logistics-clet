package gh.edu.clet.sfl.facilities.maintenance.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.facilities.maintenance.application.ports.MaintenanceRepository;
import gh.edu.clet.sfl.facilities.maintenance.application.ports.NotificationPort;
import gh.edu.clet.sfl.facilities.maintenance.application.ports.NotificationPort.NotificationKind;
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
 * <p><strong>It notifies through a port, and the port records rather than pretends.</strong> This class
 * used to publish {@code sfl.ifimp.work-order-escalated.v1} to the outbox and stop, on the reasoning
 * that delivery belongs to a notification service and a second notifier here would be a second place
 * for CLET's escalation contact list to be wrong. The reasoning holds — the contact list still lives in
 * one place, behind {@link NotificationPort}, and a real provider replaces the adapter by
 * configuration. The consequence did not: nothing consumed those events, so for three passes the
 * requirement's own words — "notifies the configured role" — were simply not true, and the gap report
 * said so. An escalation nobody is told about is a database row, not an escalation.
 *
 * <p>An escalated work order tells its assignee; one with no assignee tells the supervisor's desk,
 * because unassigned overdue work is exactly the case where nobody is already watching.
 */
@Service
public class MaintenanceEscalationService {

    /** A bound on one sweep, so a backlog cannot turn into an unbounded transaction. */
    private static final int SWEEP_LIMIT = 500;

    private final MaintenanceRepository maintenance;
    private final MaintenanceConfiguration configuration;
    private final FacilityFaultService faults;
    private final WorkOrderApplicationService workOrders;
    private final NotificationPort notifications;
    private final Clock clock;

    public MaintenanceEscalationService(MaintenanceRepository maintenance,
            MaintenanceConfiguration configuration, FacilityFaultService faults,
            WorkOrderApplicationService workOrders, NotificationPort notifications, Clock clock) {
        this.maintenance = maintenance;
        this.configuration = configuration;
        this.faults = faults;
        this.workOrders = workOrders;
        this.notifications = notifications;
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
                notifications.notifyRole(fault.siteCode(), SflRole.IFIMP_MAINTENANCE_SUPERVISOR,
                        NotificationKind.FAULT_ESCALATED, fault.faultNumber(),
                        Map.of("faultNumber", fault.faultNumber(),
                                "escalationLevel", Integer.toString(level),
                                "priority", String.valueOf(fault.priority())));
                faultsEscalated++;
            }
        }

        for (WorkOrder order : maintenance.findOverdueWorkOrders(now, SWEEP_LIMIT)) {
            SlaPolicy policy = policies.computeIfAbsent(order.siteCode(), configuration::slaPolicyFor);
            int level = policy.escalationLevelFor(order.slaDueAt(), now);
            if (level > order.escalationLevel()) {
                workOrders.applyEscalation(order, level, systemActor, SourceChannel.SCHEDULER);
                notifyEscalatedWorkOrder(order, level);
                workOrdersEscalated++;
            }
        }

        int responseBreaches = sweepResponseBreaches(systemActor, now);
        return new EscalationSweep(now, faultsEscalated, workOrdersEscalated, responseBreaches);
    }

    /**
     * The second track: work nobody has picked up.
     *
     * <p>{@code maintenance.sla.response.*} has been read, stored and exposed since S153 shipped, and
     * nothing used it — only the resolution deadline escalated. So "nobody has started this" and
     * "nobody has finished this" produced the same event, to the same person, which is precisely the
     * distinction an SLA ladder exists to draw. A job untouched for three hours needs the supervisor
     * who can reassign it; a job being worked on that is running late needs a different conversation.
     *
     * <p>A separate track rather than a second condition inside the resolution loop, and the gap report
     * asked for it that way for a reason: the two have different deadlines, different recipients and
     * different idempotence markers, and folding them together would make one of the two impossible to
     * tune without disturbing the other.
     */
    private int sweepResponseBreaches(ActorContext systemActor, Instant now) {
        int raised = 0;
        for (WorkOrder order : maintenance.findResponseBreaches(now, SWEEP_LIMIT)) {
            WorkOrder marked = maintenance.saveWorkOrder(order.withResponseEscalated(systemActor.actorId(), now,
                    SourceChannel.SCHEDULER, systemActor.correlationId()));
            Map<String, String> context = Map.of(
                    "workOrderNumber", marked.workOrderNumber(),
                    "priority", String.valueOf(marked.priority()),
                    "assigned", Boolean.toString(marked.assignedTo() != null));
            // Always the supervisor's desk, never the assignee. The assignee is by definition the person
            // who has not acted; telling them again is what they have already not responded to.
            notifications.notifyRole(marked.siteCode(), SflRole.IFIMP_MAINTENANCE_SUPERVISOR,
                    NotificationKind.RESPONSE_OVERDUE, marked.workOrderNumber(), context);
            raised++;
        }
        return raised;
    }

    /**
     * Tells the assignee, and the supervisor's desk when there is no assignee.
     *
     * <p>An escalation on unassigned work is precisely the case where nobody is watching, so it must
     * not be the case that goes unsent. Routing it to the role rather than dropping it is what makes
     * "notify when work is overdue" true for the work most likely to be overdue.
     */
    private void notifyEscalatedWorkOrder(WorkOrder order, int level) {
        Map<String, String> context = Map.of(
                "workOrderNumber", order.workOrderNumber(),
                "escalationLevel", Integer.toString(level),
                "priority", String.valueOf(order.priority()));
        if (order.assignedTo() != null && !order.assignedTo().isBlank()) {
            notifications.notifyRecipient(order.siteCode(), order.assignedTo(), NotificationKind.WORK_ESCALATED,
                    order.workOrderNumber(), context);
        } else {
            notifications.notifyRole(order.siteCode(), SflRole.IFIMP_MAINTENANCE_SUPERVISOR,
                    NotificationKind.WORK_ESCALATED, order.workOrderNumber(), context);
        }
    }

    /** What one sweep did. */
    public record EscalationSweep(Instant evaluatedAt, int faultsEscalated, int workOrdersEscalated,
            int responseBreaches) {

        public int total() {
            return faultsEscalated + workOrdersEscalated + responseBreaches;
        }
    }

    /** Overdue items without escalating them — for a dashboard, or for a dry run. */
    @Transactional(readOnly = true)
    public List<WorkOrder> overdueWorkOrders() {
        return maintenance.findOverdueWorkOrders(clock.instant(), SWEEP_LIMIT);
    }
}
