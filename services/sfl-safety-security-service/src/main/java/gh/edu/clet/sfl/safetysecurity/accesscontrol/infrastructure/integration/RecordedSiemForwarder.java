package gh.edu.clet.sfl.safetysecurity.accesscontrol.infrastructure.integration;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.application.port.SiemForwarderPort;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.AccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Phase-1 recorded SIEM forward. No SIEM is configured, so it honestly records the exception as not
 * forwarded rather than fabricating delivery - the same stance {@code RecordedWatchlistGateway} takes. */
@Component
public class RecordedSiemForwarder implements SiemForwarderPort {

    private static final Logger log = LoggerFactory.getLogger(RecordedSiemForwarder.class);

    @Override
    public ForwardResult forward(AccessException exception, ActorContext actor) {
        log.info("Recorded SIEM forward exception={} rule={} site={} - no SIEM provider configured",
                exception.id(), exception.ruleCode(), exception.siteCode());
        return new ForwardResult("NONE-RECORDED", false);
    }
}
