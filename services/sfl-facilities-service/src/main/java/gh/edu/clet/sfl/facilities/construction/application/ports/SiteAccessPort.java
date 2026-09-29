package gh.edu.clet.sfl.facilities.construction.application.ports;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.construction.domain.Contractor;
import gh.edu.clet.sfl.facilities.construction.domain.SiteAccessGrant;

/**
 * Where contractor site access is resolved - SRS-SFL-S176-02 names Access Control (S160a) and Visitor
 * Management (S160).
 *
 * <p>Both are SSEMP systems in another deployable, so the only adapter publishes an event and reports
 * truthfully that nothing enforced it: {@link Dispatch#enforced()} is {@code false} until an SSEMP
 * consumer exists and confirms. The port exists so that day changes one adapter, not the service.
 */
public interface SiteAccessPort {

    Dispatch requestAccess(SiteAccessGrant grant, Contractor contractor, ActorContext actor);

    Dispatch suspendAccess(SiteAccessGrant grant, Contractor contractor, String reason, ActorContext actor);

    /**
     * @param provider who the request went to, e.g. {@code SSEMP-S160A-OUTBOX}
     * @param enforced whether an access-control system has confirmed it is enforcing the decision
     */
    record Dispatch(String provider, boolean enforced) {
    }
}
