package gh.edu.clet.sfl.safetysecurity.intrusion.application.service;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.exception.IntrusionErrorCode;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.exception.IntrusionException;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.policy.IntrusionPermissionMatrix;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Site-scoped permission enforcement for S162, delegating to {@link IntrusionPermissionMatrix}. */
@Component
public class IntrusionAccessPolicy {

    public void require(ActorContext actor, SflPermission permission, String site, String resource, String id) {
        if (!granted(actor, permission, site)) {
            throw IntrusionException.unauthorizedScope(site, resource, id);
        }
    }

    /** Disarm-flavoured guard: a permission failure surfaces the "Not Authorised" disarm message. */
    public void requireDisarmAuthority(ActorContext actor, SflPermission permission, String site, String id) {
        if (!granted(actor, permission, site)) {
            throw new IntrusionException(IntrusionErrorCode.INTRUSION_ZONE_DISARM_NOT_AUTHORISED,
                    Map.of("requiredPermission", permission.name(), "siteCode", site == null ? "" : site,
                            "resourceId", id == null ? "" : id));
        }
    }

    public boolean has(ActorContext actor, SflPermission permission) {
        return IntrusionPermissionMatrix.grants(actor.principal().roles(), permission);
    }

    private boolean granted(ActorContext actor, SflPermission permission, String site) {
        return IntrusionPermissionMatrix.grants(actor.principal().roles(), permission)
                && actor.principal().canAccessSite(site);
    }
}
