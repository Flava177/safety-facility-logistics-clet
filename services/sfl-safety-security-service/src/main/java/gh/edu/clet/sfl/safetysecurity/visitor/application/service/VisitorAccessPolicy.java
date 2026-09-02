package gh.edu.clet.sfl.safetysecurity.visitor.application.service;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.safetysecurity.visitor.domain.exception.VisitorErrorCode;
import gh.edu.clet.sfl.safetysecurity.visitor.domain.exception.VisitorException;
import gh.edu.clet.sfl.safetysecurity.visitor.domain.policy.VisitorPermissionMatrix;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Site-scoped permission enforcement for S160, delegating to {@link VisitorPermissionMatrix}. */
@Component
public class VisitorAccessPolicy {

    public void require(ActorContext actor, SflPermission permission, String site, String resource, String id) {
        if (!granted(actor, permission, site)) {
            throw VisitorException.unauthorizedScope(site, resource, id);
        }
    }

    /** Approval-flavoured guard: a permission failure surfaces the "Unauthorized Approval" message. */
    public void requireApproval(ActorContext actor, SflPermission permission, String site, String resource,
            String id) {
        if (!granted(actor, permission, site)) {
            throw new VisitorException(VisitorErrorCode.VISITOR_UNAUTHORIZED_APPROVAL,
                    Map.of("requiredPermission", permission.name(), "siteCode", site == null ? "" : site,
                            "resourceType", resource, "resourceId", id == null ? "" : id));
        }
    }

    public boolean has(ActorContext actor, SflPermission permission) {
        return VisitorPermissionMatrix.grants(actor.principal().roles(), permission);
    }

    private boolean granted(ActorContext actor, SflPermission permission, String site) {
        return VisitorPermissionMatrix.grants(actor.principal().roles(), permission)
                && actor.principal().canAccessSite(site);
    }
}
