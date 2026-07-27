package gh.edu.clet.sfl.emergencynotification.application.service;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.emergencynotification.domain.exception.EmergencyErrorCode;
import gh.edu.clet.sfl.emergencynotification.domain.exception.EmergencyException;
import gh.edu.clet.sfl.emergencynotification.domain.policy.EmergencyPermissionMatrix;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Site-scoped permission enforcement for S174, delegating to {@link EmergencyPermissionMatrix}. */
@Component
public class EmergencyAccessPolicy {

    public void require(ActorContext actor, SflPermission permission, String site, String resource, String id) {
        if (!granted(actor, permission, site)) {
            throw EmergencyException.unauthorizedScope(site, resource, id);
        }
    }

    /** Approval-flavoured guard: a permission failure surfaces the SRS "Unauthorized Approval" message. */
    public void requireApproval(ActorContext actor, SflPermission permission, String site, String resource, String id) {
        if (!granted(actor, permission, site)) {
            throw new EmergencyException(EmergencyErrorCode.EMERGENCY_UNAUTHORIZED_APPROVAL,
                    Map.of("requiredPermission", permission.name(), "siteCode", site == null ? "" : site,
                            "resourceType", resource, "resourceId", id == null ? "" : id));
        }
    }

    /** Permission-only guard for cross-site administration (integration health/replay). */
    public void requirePermission(ActorContext actor, SflPermission permission, String resource) {
        if (!EmergencyPermissionMatrix.grants(actor.principal().roles(), permission)) {
            throw new EmergencyException(EmergencyErrorCode.EMERGENCY_UNAUTHORIZED_SCOPE,
                    Map.of("requiredPermission", permission.name(), "resourceType", resource));
        }
    }

    public boolean has(ActorContext actor, SflPermission permission) {
        return EmergencyPermissionMatrix.grants(actor.principal().roles(), permission);
    }

    private boolean granted(ActorContext actor, SflPermission permission, String site) {
        return EmergencyPermissionMatrix.grants(actor.principal().roles(), permission)
                && actor.principal().canAccessSite(site);
    }
}
