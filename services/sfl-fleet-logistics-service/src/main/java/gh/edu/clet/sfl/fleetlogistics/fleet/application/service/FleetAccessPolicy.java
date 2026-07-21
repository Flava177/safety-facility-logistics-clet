package gh.edu.clet.sfl.fleetlogistics.fleet.application.service;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.AuthorizationPolicy;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.DashboardScopeMissingException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.FleetAuthorizationException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.RestrictedDrilldownException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.UnauthorizedApprovalException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.policy.FleetPermissionMatrix;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * The single place every fleet command and query goes through for authorisation.
 *
 * <p>Enforces the five checks S166 requires: required permission, site scope, record scope,
 * sensitive-field permission and privileged-transition permission.
 *
 * <p>Denials carry machine-readable context in the exception details. Auditing of denials happens in
 * {@code FleetAuditService.recordAuthorizationDenial}, invoked from the API exception handler once the
 * failed request's transaction has rolled back — writing the denial inside the doomed transaction would
 * lose it, and writing it in a nested transaction would contend with the audit chain lock the outer
 * transaction may already hold.
 */
@Component
public class FleetAccessPolicy {

    private final AuthorizationPolicy authorizationPolicy;

    public FleetAccessPolicy() {
        this(new AuthorizationPolicy());
    }

    FleetAccessPolicy(AuthorizationPolicy authorizationPolicy) {
        this.authorizationPolicy = Objects.requireNonNull(authorizationPolicy);
    }

    /** True when any of the actor's roles grants {@code permission}. */
    public boolean has(ActorContext actor, SflPermission permission) {
        return actor != null && FleetPermissionMatrix.grants(actor.principal().roles(), permission);
    }

    /** Requires a permission, independent of any particular record. */
    public void requirePermission(ActorContext actor, SflPermission permission, String resourceType) {
        if (!has(actor, permission)) {
            throw new FleetAuthorizationException(details(permission, null, resourceType, null,
                    "Missing required fleet permission"));
        }
    }

    /** Requires access to a site, independent of any particular permission. */
    public void requireSiteAccess(ActorContext actor, SiteCode site, String resourceType, String resourceId) {
        if (!authorizationPolicy.canAccessSite(actor, site.value())) {
            throw new FleetAuthorizationException(details(null, site.value(), resourceType, resourceId,
                    "Actor site scope does not include this record"));
        }
    }

    /** The usual check: the actor needs the permission <em>and</em> access to the record's site. */
    public void require(ActorContext actor, SflPermission permission, SiteCode site, String resourceType,
            String resourceId) {
        requirePermission(actor, permission, resourceType);
        requireSiteAccess(actor, site, resourceType, resourceId);
    }

    /**
     * Privileged workflow transitions — approve, override, cancel, reopen — report the SRS
     * "Unauthorized Approval" wording rather than the generic scope error.
     */
    public void requirePrivilegedTransition(ActorContext actor, SflPermission permission, SiteCode site,
            String resourceType, String resourceId) {
        if (!has(actor, permission)) {
            throw new UnauthorizedApprovalException(details(permission, site.value(), resourceType, resourceId,
                    "Missing privileged workflow permission"));
        }
        requireSiteAccess(actor, site, resourceType, resourceId);
    }

    /**
     * Record-level scope: a record with an owner reference may only be acted on by that owner unless the
     * actor holds a supervising permission. This is what keeps the limited driver/mobile user class to
     * their own trips and inspections.
     */
    public void requireRecordScope(ActorContext actor, String ownerReference, SflPermission supervisingPermission,
            String resourceType, String resourceId) {
        if (ownerReference == null || ownerReference.isBlank()) {
            return;
        }
        if (ownerReference.equalsIgnoreCase(actor.actorId()) || has(actor, supervisingPermission)) {
            return;
        }
        throw new FleetAuthorizationException(details(supervisingPermission, null, resourceType, resourceId,
                "Record belongs to another actor"));
    }

    /** Whether the actor may see a sensitive field such as VIN or licence number. */
    public boolean canReadSensitive(ActorContext actor, SflPermission sensitivePermission) {
        return has(actor, sensitivePermission);
    }

    /**
     * The site filter a query must push into SQL. Raises the SRS "No Scope" error when the actor's
     * profile carries no site scope at all.
     */
    public SiteScopeFilter requireSiteScopeFilter(ActorContext actor) {
        var scopes = actor.principal().siteScopes();
        if (scopes.contains("*")) {
            return SiteScopeFilter.all();
        }
        if (scopes.isEmpty()) {
            throw new DashboardScopeMissingException();
        }
        return SiteScopeFilter.of(scopes);
    }

    /** Dashboard drilldown into a source record reports the SRS "Restricted Drilldown" wording. */
    public void requireDrilldown(ActorContext actor, SflPermission permission, SiteCode site, String resourceType,
            String resourceId) {
        if (!has(actor, permission) || !authorizationPolicy.canAccessSite(actor, site.value())) {
            throw new RestrictedDrilldownException(details(permission, site.value(), resourceType, resourceId,
                    "Drilldown into an unauthorised record"));
        }
    }

    private static Map<String, Object> details(SflPermission permission, String siteCode, String resourceType,
            String resourceId, String reason) {
        Map<String, Object> details = new LinkedHashMap<>();
        if (permission != null) {
            details.put("requiredPermission", permission.name());
        }
        if (siteCode != null) {
            details.put("siteCode", siteCode);
        }
        if (resourceType != null) {
            details.put("resourceType", resourceType);
        }
        if (resourceId != null) {
            details.put("resourceId", resourceId);
        }
        details.put("reason", reason);
        return details;
    }
}
