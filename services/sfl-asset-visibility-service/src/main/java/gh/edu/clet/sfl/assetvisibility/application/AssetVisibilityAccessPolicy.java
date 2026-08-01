package gh.edu.clet.sfl.assetvisibility.application;

import gh.edu.clet.sfl.assetvisibility.domain.policy.AssetVisibilityPermissionMatrix;
import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * The gate every AVAMP operation passes through.
 *
 * <h2>Two checks, and the second is the one that was most missing</h2>
 *
 * <ol>
 *   <li><strong>Permission</strong> — does any of the actor's roles grant this?</li>
 *   <li><strong>Site scope</strong> — is this asset at a site the actor holds?</li>
 * </ol>
 *
 * The register is site-scoped in the domain — every asset carries a {@code siteCode} — and that was
 * enforced nowhere. A permission check alone would have left an integration engineer scoped to one
 * centre able to move an asset at another, which is the failure site scope exists to prevent and the
 * one a role-only check never catches.
 *
 * <h2>Reads are checked too</h2>
 *
 * Three of the eight endpoints are reads, and the previous controller did not even resolve an actor
 * for them. "It is only a read" is how a register of every tracked device in the estate, with its
 * current location and custodian, becomes readable by anyone who can obtain a token.
 *
 * <h2>The error is the platform's, not a new one</h2>
 *
 * {@link AssetVisibilityAuthorizationException} carries {@code ASSETVIS_UNAUTHORIZED_SCOPE} and the
 * details a caller needs — the permission required, the resource, the site. It maps to **403**, not
 * 401: "who are you" and "you may not" are different answers, and A1 established that distinction
 * across the platform.
 */
@Component
public class AssetVisibilityAccessPolicy {

    /** Permission alone, for operations that are not site-bound at the point of the check. */
    public void require(ActorContext actor, SflPermission permission, String resource) {
        if (AssetVisibilityPermissionMatrix.grants(actor.principal().roles(), permission)) {
            return;
        }
        throw refusal(permission, resource, null, "Actor does not hold the required permission");
    }

    /** Permission and site scope, which is the ordinary case for a record that names its site. */
    public void require(ActorContext actor, SflPermission permission, String siteCode, String resource) {
        require(actor, permission, resource);
        if (siteCode == null || siteCode.isBlank()) {
            // A missing site on a scoped operation is a caller error rather than an open door: the
            // alternative — treating "no site" as "every site" — is how a scoped register leaks.
            throw refusal(permission, resource, siteCode, "A site code is required for this operation");
        }
        if (!actor.principal().canAccessSite(siteCode)) {
            throw refusal(permission, resource, siteCode, "Actor is not scoped to this site");
        }
    }

    /** True when the actor may read, used to decide whether a list is worth fetching at all. */
    public boolean canRead(ActorContext actor) {
        return AssetVisibilityPermissionMatrix.grants(actor.principal().roles(),
                SflPermission.ASSET_REFERENCE_READ);
    }

    private AssetVisibilityAuthorizationException refusal(SflPermission permission, String resource,
            String siteCode, String reason) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("reason", reason);
        details.put("requiredPermission", permission.name());
        details.put("resourceType", resource);
        if (siteCode != null && !siteCode.isBlank()) {
            details.put("siteCode", siteCode);
        }
        return new AssetVisibilityAuthorizationException(details);
    }
}
