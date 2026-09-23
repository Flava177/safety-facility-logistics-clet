package gh.edu.clet.sfl.safetysecurity.accesscontrol.application.port;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.AccessException;

/**
 * Forwards a security-relevant access exception to the SIEM/SOC tooling - SRS-SFL-S160a-04. No SIEM
 * is chosen yet; see {@code RecordedSiemForwarder}, the same honest-stub stance every other
 * not-yet-integrated vendor in this codebase takes.
 */
public interface SiemForwarderPort {

    ForwardResult forward(AccessException exception, ActorContext actor);

    record ForwardResult(String provider, boolean forwarded) {
    }
}
