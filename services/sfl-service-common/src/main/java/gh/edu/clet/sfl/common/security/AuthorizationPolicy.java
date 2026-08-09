package gh.edu.clet.sfl.common.security;

import java.util.Set;

/**
 * The authorisation primitives every service shares.
 *
 * <p>Role checks were the whole of this class, and role is the wrong grain for most decisions: a
 * technician and a maintenance supervisor hold different work-order permissions and neither is
 * expressible as "has role X" without restating the matrix at the call site. {@link
 * #requirePermission} asks the service's own {@link PermissionMatrix} instead, so the check at the
 * endpoint and the gate on the screen are the same named permission.
 */
public class AuthorizationPolicy {

    private final PermissionMatrix matrix;

    /**
     * For services that have not yet supplied their matrix.
     *
     * <p>Deliberately denies rather than permits. A no-argument constructor that granted everything
     * would make an unwired service look enforced while enforcing nothing, which is the same failure
     * the dashboard's permission fail-open produced and took a fortnight to see.
     */
    public AuthorizationPolicy() {
        this((roles, permission) -> false);
    }

    public AuthorizationPolicy(PermissionMatrix matrix) {
        this.matrix = matrix == null ? (roles, permission) -> false : matrix;
    }

    public boolean hasPermission(ActorContext actor, SflPermission permission) {
        return matrix.grants(actor.principal().roles(), permission);
    }

    /**
     * Refuses unless the actor's roles carry this permission.
     *
     * <p>Independent of {@code sfl.security.enabled}. With security off the actor arrives on the
     * {@code X-SFL-*} headers rather than a token, but it is still an actor with roles, and
     * authorisation applies to it identically. The flag decides how identity is established, not
     * whether it is honoured - and a local run that authorised nothing would mean every screen was
     * tested against a service that would refuse it in production.
     */
    public void requirePermission(ActorContext actor, SflPermission permission) {
        if (!hasPermission(actor, permission)) {
            throw new AuthorizationException("Actor requires permission: " + permission);
        }
    }

    public boolean hasRole(ActorContext actor, SflRole role) {
        return actor.principal().hasRole(role);
    }

    public boolean hasAnyRole(ActorContext actor, Set<SflRole> roles) {
        return actor.principal().hasAnyRole(roles);
    }

    public boolean canAccessSite(ActorContext actor, String siteCode) {
        return actor.principal().canAccessSite(siteCode);
    }

    public void requireRole(ActorContext actor, SflRole role) {
        if (!hasRole(actor, role)) {
            throw new AuthorizationException("Actor requires role: " + role);
        }
    }

    public void requireAnyRole(ActorContext actor, Set<SflRole> roles) {
        if (!hasAnyRole(actor, roles)) {
            throw new AuthorizationException("Actor does not have a required role");
        }
    }

    public void requireSiteAccess(ActorContext actor, String siteCode) {
        if (!canAccessSite(actor, siteCode)) {
            throw new AuthorizationException("Actor cannot access site: " + SiteScopedPrincipal.normalizeSite(siteCode));
        }
    }
}