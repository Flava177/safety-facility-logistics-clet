package gh.edu.clet.sfl.facilities.shared.infrastructure.integration;

import gh.edu.clet.sfl.facilities.shared.application.port.SecurityEventForwarderPort;
import gh.edu.clet.sfl.facilities.shared.domain.security.SecurityEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Recorded SIEM forward. No SIEM (S208) is connected, so this logs at WARN on a dedicated logger a log
 * shipper can route, and reports {@code forwarded=false} - it never fabricates delivery.
 */
@Component
public class RecordedSecurityEventForwarder implements SecurityEventForwarderPort {

    /** A named logger, so a log pipeline can route it to the SIEM before a real adapter exists. */
    private static final Logger SIEM = LoggerFactory.getLogger("sfl.facilities.siem");

    @Override
    public ForwardResult forward(SecurityEvent event) {
        SIEM.warn("siem module={} category={} severity={} site={} reference={} summary={}", event.module(),
                event.category(), event.severity(), event.siteCode(), event.reference(), event.summary());
        return new ForwardResult("NONE-RECORDED", false);
    }
}
