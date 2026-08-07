package gh.edu.clet.sfl.common.security;

import java.util.Set;

/**
 * What a set of roles is permitted to do, as far as one service is concerned.
 *
 * <h2>Why an interface here rather than a matrix here</h2>
 *
 * <p>Each service owns its own mapping and must keep owning it: {@code FacilitiesPermissionMatrix}
 * knows that a technician updates a work order and a supervisor closes it, and that is an IFIMP
 * business rule with no business in a shared library. What the shared library can own is the
 * <em>question</em> - "may this actor do this" - so that {@link AuthorizationPolicy} can ask it
 * without knowing which platform it is standing in.
 *
 * <p>The existing matrices are static and not uniform: fleet, asset-visibility and facilities expose
 * {@code permissionsFor(roles)}, fuel and dispatch expose only {@code grants(roles, permission)}.
 * Both shapes satisfy this interface through a one-line adapter, which is the point - nothing about
 * them had to change to be enforced.
 *
 * <h2>What this is for, stated plainly</h2>
 *
 * <p>Until now the only authorisation primitives in common were role-based: {@code requireRole},
 * {@code requireAnyRole}, {@code requireSiteAccess}. So the matrices existed, the dashboard asked
 * them what to show, and no endpoint asked them anything - the permission a screen was hidden on was
 * not the permission the service checked, because the service checked no permission at all. This is
 * the missing half.
 */
@FunctionalInterface
public interface PermissionMatrix {

    /** Whether these roles, together, carry this permission. */
    boolean grants(Set<SflRole> roles, SflPermission permission);
}
