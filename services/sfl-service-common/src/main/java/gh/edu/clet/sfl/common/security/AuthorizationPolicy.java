package gh.edu.clet.sfl.common.security;

import java.util.Set;

public class AuthorizationPolicy {

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