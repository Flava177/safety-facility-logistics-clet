package gh.edu.clet.sfl.safetysecurity.accesscontrol.application.service;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.exception.AccessControlErrorCode;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.exception.AccessControlException;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.policy.AccessControlPermissionMatrix;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Site-scoped permission enforcement for S160a, delegating to {@link AccessControlPermissionMatrix}. */
@Component
public class AccessControlAccessPolicy {

    public void require(ActorContext actor, SflPermission permission, String site, String resource, String id) {
        if (!granted(actor, permission, site)) {
            throw AccessControlException.unauthorizedScope(site, resource, id);
        }
    }

    /** Override-flavoured guard: a permission failure surfaces the "Not Authorised" override message. */
    public void requireOverrideAuthority(ActorContext actor, SflPermission permission, String site, String id) {
        if (!granted(actor, permission, site)) {
            throw new AccessControlException(AccessControlErrorCode.ACCESS_OVERRIDE_NOT_AUTHORISED,
                    Map.of("requiredPermission", permission.name(), "siteCode", site == null ? "" : site,
                            "resourceId", id == null ? "" : id));
        }
    }

    public boolean has(ActorContext actor, SflPermission permission) {
        return AccessControlPermissionMatrix.grants(actor.principal().roles(), permission);
    }

    private boolean granted(ActorContext actor, SflPermission permission, String site) {
        return AccessControlPermissionMatrix.grants(actor.principal().roles(), permission)
                && actor.principal().canAccessSite(site);
    }
}
