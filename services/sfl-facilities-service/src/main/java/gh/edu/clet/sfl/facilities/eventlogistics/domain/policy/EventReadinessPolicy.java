package gh.edu.clet.sfl.facilities.eventlogistics.domain.policy;

import gh.edu.clet.sfl.facilities.eventlogistics.domain.EventResourceRequest;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.EventSetupTask;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.EventSetupTaskStatus;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.ReadinessStatus;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.ResourceRequestStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The roll-up, escalation and completion rules - SRS-SFL-S173-02 and -04. Pure functions of the task,
 * its requests and a clock, so each rule is a unit test rather than a scenario.
 */
public final class EventReadinessPolicy {

    private EventReadinessPolicy() {
    }

    /** Counts for the readiness view and the dashboard's "hand-off completeness". */
    public record Summary(int live, int resolved, int requested, int confirmed, int fulfilled, int conflicted,
            int manualCoordination, int manualAccepted, int cancelled) {

        /** Resolved over live, as a whole percentage; 0 for a task with nothing requested. */
        public int completenessPercent() {
            return live == 0 ? 0 : (int) Math.floor(resolved * 100.0 / live);
        }
    }

    public static Summary summarise(Collection<EventResourceRequest> requests) {
        int live = 0;
        int resolved = 0;
        int requested = 0;
        int confirmed = 0;
        int fulfilled = 0;
        int conflicted = 0;
        int manual = 0;
        int manualAccepted = 0;
        int cancelled = 0;
        for (EventResourceRequest request : requests) {
            if (!request.status().isLive()) {
                cancelled++;
                continue;
            }
            live++;
            if (!request.isUnresolved()) {
                resolved++;
            }
            switch (request.status()) {
                case REQUESTED -> requested++;
                case CONFIRMED -> confirmed++;
                case FULFILLED -> fulfilled++;
                case CONFLICTED -> conflicted++;
                case MANUAL_COORDINATION -> {
                    manual++;
                    if (request.isManualCoordinationAccepted()) {
                        manualAccepted++;
                    }
                }
                case CANCELLED -> {
                    // counted above
                }
            }
        }
        return new Summary(live, resolved, requested, confirmed, fulfilled, conflicted, manual, manualAccepted,
                cancelled);
    }

    /**
     * One status for the whole event, worst first: a conflict outranks an outstanding request, which
     * outranks readiness. A manual item nobody accepted counts as outstanding - never as ready, which
     * is what S173-02's "not falsely shown as fulfilled" requires of the roll-up too.
     */
    public static ReadinessStatus rollUp(EventSetupTaskStatus taskStatus, Collection<EventResourceRequest> requests) {
        if (taskStatus == EventSetupTaskStatus.CANCELLED) {
            return ReadinessStatus.CANCELLED;
        }
        if (taskStatus == EventSetupTaskStatus.COMPLETED) {
            return ReadinessStatus.COMPLETED;
        }
        List<EventResourceRequest> live = requests.stream().filter(request -> request.status().isLive()).toList();
        if (live.isEmpty()) {
            return ReadinessStatus.UNRESOURCED;
        }
        if (live.stream().anyMatch(request -> request.status() == ResourceRequestStatus.CONFLICTED)) {
            return ReadinessStatus.CONFLICTED;
        }
        if (live.stream().anyMatch(EventResourceRequest::isUnresolved)) {
            return ReadinessStatus.AWAITING;
        }
        return ReadinessStatus.READY;
    }

    /** Whether the event has entered its escalation window and not yet finished. */
    public static boolean inEscalationWindow(EventSetupTask task, Instant now, Duration window) {
        Instant opens = task.details().startsAt().minus(window);
        return task.status().isLive() && !now.isBefore(opens) && now.isBefore(task.details().endsAt());
    }

    /**
     * The requests the sweep must escalate now - S173-04: "any request still merely requested escalates
     * to the coordinator".
     *
     * <p>The window opens a configured interval <em>before</em> the start, which is the acceptance
     * criterion's "notified before the event, not after". It stays open until the event ends, because
     * S173-04's error state - "escalated, not silently carried forward" - still applies if the sweep
     * was down when the window opened; such a late escalation is marked as not before the start. Each
     * request escalates once: a coordinator told the same thing every five minutes stops reading.
     */
    public static List<EventResourceRequest> dueForEscalation(EventSetupTask task,
            Collection<EventResourceRequest> requests, Set<UUID> alreadyEscalated, Instant now, Duration window) {
        if (!inEscalationWindow(task, now, window)) {
            return List.of();
        }
        return requests.stream()
                .filter(EventResourceRequest::isUnresolved)
                .filter(request -> !alreadyEscalated.contains(request.id()))
                .toList();
    }

    /**
     * What stops the set-up task being marked complete - S173-04's validation: "cannot be marked complete
     * with an unresolved requested-status resource request and no escalation record".
     */
    public static List<EventResourceRequest> blockingCompletion(Collection<EventResourceRequest> requests,
            Set<UUID> escalated) {
        return requests.stream()
                .filter(EventResourceRequest::isUnresolved)
                .filter(request -> !escalated.contains(request.id()))
                .toList();
    }
}
