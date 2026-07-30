package gh.edu.clet.sfl.fleetlogistics.dispatch.application.service;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.policy.DispatchPermissionMatrix;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.FleetAuthorizationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Site-scoped permission enforcement for S171, delegating to {@link DispatchPermissionMatrix}. */
@Component
public class DispatchAccessPolicy {

    public void require(ActorContext actor, SflPermission permission, String site, String resource, String id) {
        if (!DispatchPermissionMatrix.grants(actor.principal().roles(), permission)
                || !actor.principal().canAccessSite(site)) {
            // Omit an absent id rather than blanking it. `Map.of` rejects nulls, and the `""` this
            // used to substitute is a claim that there was an id and it was empty. It also used to
            // slip past the audit writer's null guard and lose the denial record entirely.
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("requiredPermission", permission.name());
            details.put("siteCode", site);
            details.put("resourceType", resource);
            if (id != null && !id.isBlank()) {
                details.put("resourceId", id);
            }
            throw new FleetAuthorizationException(details);
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
