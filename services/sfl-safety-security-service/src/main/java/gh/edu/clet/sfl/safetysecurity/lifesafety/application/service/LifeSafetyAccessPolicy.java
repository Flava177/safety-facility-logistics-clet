package gh.edu.clet.sfl.safetysecurity.lifesafety.application.service;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.exception.LifeSafetyException;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.policy.LifeSafetyPermissionMatrix;
import org.springframework.stereotype.Component;

/** Site-scoped permission enforcement for S162a, delegating to {@link LifeSafetyPermissionMatrix}. */
@Component
public class LifeSafetyAccessPolicy {

    public void require(ActorContext actor, SflPermission permission, String site, String resource, String id) {
        if (!granted(actor, permission, site)) {
            throw LifeSafetyException.unauthorizedScope(site, resource, id);
        }
    }

    public boolean has(ActorContext actor, SflPermission permission) {
        return LifeSafetyPermissionMatrix.grants(actor.principal().roles(), permission);
    }

    private boolean granted(ActorContext actor, SflPermission permission, String site) {
        return LifeSafetyPermissionMatrix.grants(actor.principal().roles(), permission)
                && actor.principal().canAccessSite(site);
    }
}
