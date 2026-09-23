package gh.edu.clet.sfl.safetysecurity.cctv.infrastructure.integration;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.safetysecurity.cctv.application.port.SiemForwarderPort;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.AnalyticsAlert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Phase-1 recorded SIEM forward. No SIEM is configured, so it honestly records the alert as not
 * forwarded rather than fabricating delivery - the same stance {@code RecordedSiemForwarder} in
 * {@code accesscontrol} takes for its own exceptions. */
@Component
public class RecordedSiemForwarder implements SiemForwarderPort {

    private static final Logger log = LoggerFactory.getLogger(RecordedSiemForwarder.class);

    @Override
    public ForwardResult forward(AnalyticsAlert alert, ActorContext actor) {
        log.info("Recorded SIEM forward alert={} type={} site={} - no SIEM provider configured", alert.id(),
                alert.type(), alert.siteCode());
        return new ForwardResult("NONE-RECORDED", false);
    }
}
