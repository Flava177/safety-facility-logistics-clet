package gh.edu.clet.sfl.facilities.eventlogistics.infrastructure.integration;

import gh.edu.clet.sfl.facilities.eventlogistics.application.ports.CcpEventsDirectoryPort;
import gh.edu.clet.sfl.facilities.eventlogistics.application.ports.EventLogisticsRepository;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.S078EventStatus;
import org.springframework.stereotype.Component;

/**
 * Resolves S078 references against S173's own register of accepted hand-offs - SRS-SFL-S173-01.
 *
 * <h2>What this can and cannot know</h2>
 *
 * <p>CCP Events (S078) is an external CLET system with no query API integrated with SFL. The only
 * evidence S173 has that S078 recognises an event is what S078 has said through the authenticated
 * {@code CCP_EVENTS} channel. So:
 * <ul>
 *   <li>a reference S173 already holds a set-up task for is <strong>resolvable</strong> - S078 confirmed it
 *       in a signed hand-off before;</li>
 *   <li>a new reference arriving as a signed <strong>CONFIRMED</strong> hand-off is resolvable - the signed
 *       confirmation is S078 recognising it, and it is the one fact S173 can check;</li>
 *   <li>anything else - an update or cancellation for a reference S173 has never seen, or a draft or
 *       tentative event - is <strong>unresolvable</strong> and rejected.</li>
 * </ul>
 *
 * <p>This is weaker than asking S078, and the gap report says so: a correctly signed confirmation for
 * an event S078 later disowns cannot be caught here. When S078 exposes a lookup, a real adapter
 * replaces this one behind the same port.
 */
@Component
public class RecordedCcpEventsDirectory implements CcpEventsDirectoryPort {

    private final EventLogisticsRepository repository;

    public RecordedCcpEventsDirectory(EventLogisticsRepository repository) {
        this.repository = repository;
    }

    @Override
    public Resolution resolve(String s078EventReference, S078EventStatus statedStatus) {
        if (repository.findTaskByS078Reference(s078EventReference).isPresent()) {
            return new Resolution(true, true, null);
        }
        if (statedStatus == S078EventStatus.CONFIRMED) {
            return new Resolution(true, false, null);
        }
        return new Resolution(false, false, statedStatus == S078EventStatus.CANCELLED
                ? "S078 event " + s078EventReference + " is not in the accepted hand-off register, so there is"
                        + " nothing S173 recognises to cancel."
                : "S078 event " + s078EventReference + " is " + statedStatus
                        + ", not confirmed; a set-up task is created only for a confirmed event.");
    }

    @Override
    public String describe() {
        return "CCP Events (S078) is external and has no query API integrated with SFL. References resolve"
                + " against S173's register of signed CCP_EVENTS hand-offs; in development the source is the"
                + " simulator CCP-EVENTS-SIM.";
    }
}
