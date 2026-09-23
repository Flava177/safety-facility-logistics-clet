package gh.edu.clet.sfl.safetysecurity.intrusion.infrastructure.integration;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.safetysecurity.intrusion.application.port.ArmedResponseCoordinationPort;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.IntrusionAlarm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Phase-1 recorded armed-response dispatch. No monitoring/armed-response contract is configured, so
 * it honestly records the request as not dispatched rather than fabricating a response - the same
 * stance {@code RecordedSiemForwarder} takes for S160a. */
@Component
public class RecordedArmedResponseGateway implements ArmedResponseCoordinationPort {

    private static final Logger log = LoggerFactory.getLogger(RecordedArmedResponseGateway.class);

    @Override
    public DispatchRequestResult requestDispatch(IntrusionAlarm alarm, ActorContext actor) {
        log.info("Recorded armed-response dispatch request alarm={} zone={} site={} - no monitoring contract "
                + "configured", alarm.id(), alarm.zoneCode(), alarm.siteCode());
        return new DispatchRequestResult("NONE-RECORDED", false);
    }
}
