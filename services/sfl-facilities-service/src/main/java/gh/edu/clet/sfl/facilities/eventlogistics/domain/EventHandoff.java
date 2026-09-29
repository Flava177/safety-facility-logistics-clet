package gh.edu.clet.sfl.facilities.eventlogistics.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * One hand-off received from CCP Events (S078), accepted or rejected - the "hand-off record" key object
 * of S173.
 *
 * <p>This register is also what {@code RecordedCcpEventsDirectory} resolves S078 references against.
 * S078 has no query API integrated with SFL, so "does S078 recognise this event?" can only be answered
 * from what S078 has itself told SFL through the authenticated channel - see that adapter.
 *
 * @param action what the hand-off did to the set-up task; {@code null} for a rejection
 * @param rejectionDetail why it was refused, in the words the rejection used
 */
public record EventHandoff(
        UUID id,
        String siteCode,
        String s078EventReference,
        String s078Status,
        Outcome outcome,
        Action action,
        UUID setupTaskId,
        UUID inboxId,
        String sourceSystem,
        String idempotencyKey,
        String rejectionDetail,
        Instant receivedAt,
        String recordedBy,
        String correlationId) {

    public enum Outcome {
        ACCEPTED,
        REJECTED
    }

    public enum Action {
        /** A confirmed event S173 had not seen: the set-up task was created. */
        CREATED,
        /** S078 changed the event: the task's details were updated. */
        UPDATED,
        /** S078 re-sent the event with nothing S173 holds changed. */
        UNCHANGED,
        /** S078 cancelled the event: the task and its live requests were cancelled. */
        CANCELLED,
        /** The task was already completed or cancelled; recorded, nothing changed. */
        IGNORED_TASK_CLOSED
    }
}
