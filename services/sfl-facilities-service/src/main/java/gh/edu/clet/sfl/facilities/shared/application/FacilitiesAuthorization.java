package gh.edu.clet.sfl.facilities.shared.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.facilities.shared.application.port.AuditPort;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.policy.FacilitiesPermissionMatrix;
import java.util.Collection;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * The single authorisation gate for every S152 command and query.
 *
 * <p>Two questions, both required by SRS-SFL-S152-01 — "Users shall only see and update records
 * inside their assigned site scopes and roles":
 * <ol>
 *   <li><strong>Permission</strong>, derived from roles through {@link FacilitiesPermissionMatrix}.</li>
 *   <li><strong>Site scope</strong>, checked against the record's own site.</li>
 * </ol>
 *
 * <p>Both are needed and neither substitutes for the other: a facilities manager for Accra holds
 * {@code FACILITIES_SPACE_MANAGE} and still may not edit a Kumasi room.
 *
 * <p><strong>Every denial is audited.</strong> {@code AUTHORIZATION_DENIED} goes onto the hash chain
 * before the exception is thrown, because a refused attempt to read another site's estate is exactly
 * what a compliance review is looking for, and an unaudited refusal is invisible. The audit write
 * happens first so a caller cannot learn from timing whether the record existed.
 */
@Component
public class FacilitiesAuthorization {

    private final AuditPort audit;

    public FacilitiesAuthorization(AuditPort audit) {
        this.audit = audit;
    }

    /** The permissions this actor holds, for the actor-permissions endpoint the dashboard reads. */
    public Set<SflPermission> permissionsOf(ActorContext actor) {
        return FacilitiesPermissionMatrix.permissionsFor(actor.principal().roles());
    }

    public boolean has(ActorContext actor, SflPermission permission) {
        return FacilitiesPermissionMatrix.grants(actor.principal().roles(), permission);
    }

    /** Refuses, and audits the refusal, when the actor lacks the permission. */
    public void require(ActorContext actor, SflPermission permission, SourceChannel channel,
            String resourceType, String resourceId, String siteScope) {
        if (has(actor, permission)) {
            return;
        }
        deny(actor, channel, resourceType, resourceId, siteScope,
                "Actor does not hold " + permission.name());
    }

    /** Refuses, and audits the refusal, when the record's site is outside the actor's scope. */
    public void requireSite(ActorContext actor, String siteCode, SourceChannel channel,
            String resourceType, String resourceId) {
        if (siteCode == null || siteCode.isBlank()) {
            throw new FacilitiesException.MissingSiteScopeException();
        }
        if (actor.principal().canAccessSite(siteCode)) {
            return;
        }
        deny(actor, channel, resourceType, resourceId, siteCode,
                "Actor site scopes do not include " + siteCode);
    }

    /** The common case: a permission and a site, checked together. */
    public void require(ActorContext actor, SflPermission permission, String siteCode, SourceChannel channel,
            String resourceType, String resourceId) {
        require(actor, permission, channel, resourceType, resourceId, siteCode);
        requireSite(actor, siteCode, channel, resourceType, resourceId);
    }

    /**
     * Refuses an actor holding no site scope at all.
     *
     * <p>Its own error state in SRS-SFL-S152-05 ("No site scope is assigned to your user profile"),
     * distinct from being refused a particular site — one is a provisioning problem, the other is a
     * permission boundary, and telling a user the wrong one wastes a support call.
     */
    public void requireAnySiteScope(ActorContext actor) {
        if (actor.principal().siteScopes().isEmpty()) {
            throw new FacilitiesException.NoScopeException();
        }
    }

    /** {@code true} when the actor may see this site — for filtering lists rather than refusing them. */
    public boolean canAccessSite(ActorContext actor, String siteCode) {
        return siteCode != null && actor.principal().canAccessSite(siteCode);
    }

    /**
     * Narrows a requested site filter to what the actor may see.
     *
     * <p>A list endpoint filters rather than refuses: asking for "all sites" is a legitimate request
     * that should answer with the actor's own, not a 403. Asking for a *specific* site outside scope
     * is refused, because silently returning an empty list would misrepresent the estate.
     */
    public void requireRequestedSite(ActorContext actor, String requestedSiteCode, SourceChannel channel,
            String resourceType) {
        if (requestedSiteCode != null && !requestedSiteCode.isBlank()) {
            requireSite(actor, requestedSiteCode, channel, resourceType, "list");
        } else {
            requireAnySiteScope(actor);
        }
    }

    /** Filters a collection down to the sites the actor may see. */
    public <T> java.util.List<T> filterBySite(ActorContext actor, Collection<T> records,
            java.util.function.Function<T, String> siteOf) {
        return records.stream().filter(record -> canAccessSite(actor, siteOf.apply(record))).toList();
    }

    private void deny(ActorContext actor, SourceChannel channel, String resourceType, String resourceId,
            String siteScope, String reason) {
        audit.recordDenial(actor, channel, resourceType, resourceId,
                siteScope == null || siteScope.isBlank() ? "*" : siteScope, reason);
        throw new FacilitiesException.UnauthorizedScopeException(
                gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesErrorCode.UNAUTHORIZED_SCOPE
                        .defaultMessage());
    }
}
