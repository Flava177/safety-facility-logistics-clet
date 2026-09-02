package gh.edu.clet.sfl.safetysecurity.incident.application.service;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.safetysecurity.incident.domain.exception.IncidentException;
import gh.edu.clet.sfl.safetysecurity.incident.domain.policy.IncidentPermissionMatrix;
import org.springframework.stereotype.Component;

/** Site-scoped permission enforcement for S163, delegating to {@link IncidentPermissionMatrix}. */
@Component
public class IncidentAccessPolicy {

    public void require(ActorContext actor, SflPermission permission, String site, String resource, String id) {
        if (!granted(actor, permission, site)) {
            throw IncidentException.unauthorizedScope(site, resource, id);
        }
    }

    public boolean has(ActorContext actor, SflPermission permission) {
        return IncidentPermissionMatrix.grants(actor.principal().roles(), permission);
    }

    private boolean granted(ActorContext actor, SflPermission permission, String site) {
        return IncidentPermissionMatrix.grants(actor.principal().roles(), permission)
                && actor.principal().canAccessSite(site);
    }
}
