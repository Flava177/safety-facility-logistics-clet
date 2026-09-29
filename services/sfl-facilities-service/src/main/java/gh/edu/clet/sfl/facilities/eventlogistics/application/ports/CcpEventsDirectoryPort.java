package gh.edu.clet.sfl.facilities.eventlogistics.application.ports;

import gh.edu.clet.sfl.facilities.eventlogistics.domain.S078EventStatus;

/**
 * Whether CCP Events (S078) recognises an event reference - SRS-SFL-S173-01 validation: "A set-up task
 * cannot exist without a resolvable S078 event reference; a hand-off for an unrecognised event is
 * rejected, not created speculatively."
 *
 * <p>A port because the honest answer today and the right answer later are different adapters. S078 is
 * an external CLET system with no query API integrated with SFL, so the shipped adapter
 * ({@code RecordedCcpEventsDirectory}) can only resolve against what S078 has itself sent through the
 * authenticated {@code CCP_EVENTS} channel. The day S078 exposes a lookup, a real adapter replaces it
 * and nothing in the intake changes.
 */
public interface CcpEventsDirectoryPort {

    /**
     * @param statedStatus what the hand-off being resolved says the event's status is
     */
    Resolution resolve(String s078EventReference, S078EventStatus statedStatus);

    /** A plain statement of how resolution works in this deployment, for the integration view. */
    String describe();

    /**
     * @param known S173 already holds a set-up task for this reference
     * @param explanation why an unresolvable reference was refused - carried onto the rejection record
     */
    record Resolution(boolean resolvable, boolean known, String explanation) {
    }
}
