package gh.edu.clet.sfl.safetysecurity.cctv.application.service;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.exception.CctvException;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.policy.CctvPermissionMatrix;
import org.springframework.stereotype.Component;

/** Site-scoped permission enforcement for S161, delegating to {@link CctvPermissionMatrix}. */
@Component
public class CctvAccessPolicy {

    public void require(ActorContext actor, SflPermission permission, String site, String resource, String id) {
        if (!granted(actor, permission, site)) {
            throw CctvException.unauthorizedScope(site, resource, id);
        }
    }

    public boolean has(ActorContext actor, SflPermission permission) {
        return CctvPermissionMatrix.grants(actor.principal().roles(), permission);
    }

    private boolean granted(ActorContext actor, SflPermission permission, String site) {
        return CctvPermissionMatrix.grants(actor.principal().roles(), permission)
                && actor.principal().canAccessSite(site);
    }
}
