package gh.edu.clet.sfl.facilities.shared.application.port;

import gh.edu.clet.sfl.facilities.shared.domain.security.SecurityEvent;

/**
 * Forwards a security-relevant IFIMP event to the SIEM (S208).
 *
 * <p>One port for the service, not one per module: facilities is the host platform and its modules
 * already share one audit chain and one permission matrix. No SIEM is chosen yet, so the only adapter
 * is {@code RecordedSecurityEventForwarder}, which records the event as <em>not</em> forwarded rather than
 * pretending - the same stance SSEMP's S160a and S161 forwarders take.
 */
public interface SecurityEventForwarderPort {

    ForwardResult forward(SecurityEvent event);

    record ForwardResult(String provider, boolean forwarded) {
    }
}
