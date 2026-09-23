package gh.edu.clet.sfl.safetysecurity.intrusion.application.port;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.IntrusionAlarm;

/**
 * Requests dispatch from the external monitoring/armed-response contract for a confirmed alarm -
 * SRS-SFL-S162-05. No contract is chosen yet; see {@code RecordedArmedResponseGateway}, the same
 * honest-stub stance every other not-yet-integrated vendor in this codebase takes.
 */
public interface ArmedResponseCoordinationPort {

    DispatchRequestResult requestDispatch(IntrusionAlarm alarm, ActorContext actor);

    record DispatchRequestResult(String provider, boolean dispatched) {
    }
}
