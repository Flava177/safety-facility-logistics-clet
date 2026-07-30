package gh.edu.clet.sfl.facilities.masterdata.application.ports;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.masterdata.domain.FacilityAsset;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;

/**
 * How the estate tells readiness that something it depends on has changed.
 *
 * <p>An inversion, and a deliberate one. Readiness reads spaces, so {@code readiness} depends on
 * {@code masterdata}; an asset going out of service must recompute the readiness of the space it sits
 * in, which would need the dependency to run the other way too. Declaring the port <em>here</em> and
 * implementing it <em>there</em> keeps the compile-time arrow pointing one way while the runtime
 * behaviour flows both.
 *
 * <p>The alternative — publishing a domain event and consuming it asynchronously — is what this will
 * become when the outbox has a drainer. Until then a synchronous port is honest: the recompute really
 * does happen inside the same transaction, and pretending otherwise would put an eventual-consistency
 * story in the documentation that the code does not implement.
 */
public interface SpaceReadinessPort {

    /**
     * Reconciles the readiness blockers derived from one asset, then re-derives its space's readiness.
     *
     * <p>Called after an asset's operational status changes. An asset that has become impaired raises
     * a blocker; one that has recovered resolves the blocker it raised. Does nothing when the asset is
     * not attached to a space — an asset in a yard blocks nothing.
     */
    void reconcileAssetBlockers(FacilityAsset asset, ActorContext actor, SourceChannel channel);
}
