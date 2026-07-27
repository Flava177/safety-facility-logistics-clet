package gh.edu.clet.sfl.fleetlogistics.dispatch.application.service;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.policy.DispatchPermissionMatrix;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.FleetAuthorizationException;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Site-scoped permission enforcement for S171, delegating to {@link DispatchPermissionMatrix}. */
@Component
public class DispatchAccessPolicy {

    public void require(ActorContext actor, SflPermission permission, String site, String resource, String id) {
        if (!DispatchPermissionMatrix.grants(actor.principal().roles(), permission)
                || !actor.principal().canAccessSite(site)) {
            throw new FleetAuthorizationException(Map.of("requiredPermission", permission.name(), "siteCode", site,
                    "resourceType", resource, "resourceId", id == null ? "" : id));
        }
    }

    public boolean has(ActorContext actor, SflPermission permission) {
        return DispatchPermissionMatrix.grants(actor.principal().roles(), permission);
    }

    /** Permission-only guard for cross-site administration (outbox health/replay, audit integrity). */
    public void requirePermission(ActorContext actor, SflPermission permission, String resource) {
        if (!DispatchPermissionMatrix.grants(actor.principal().roles(), permission)) {
            throw new FleetAuthorizationException(Map.of("requiredPermission", permission.name(),
                    "resourceType", resource));
        }
    }
}
