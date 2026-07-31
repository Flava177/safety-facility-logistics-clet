package gh.edu.clet.sfl.facilities.readiness.application.ports;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.readiness.domain.BlockerSeverity;
import gh.edu.clet.sfl.facilities.readiness.domain.BlockerSource;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import java.util.UUID;

/**
 * How another module tells readiness that something is wrong with a space.
 *
 * <h2>Why this port is declared here and not by the caller</h2>
 *
 * S152's {@code SpaceReadinessPort} is declared by {@code masterdata} and implemented by readiness —
 * consumer-declared, which is the usual inversion. This one is deliberately the other way round, and
 * the reason is a constraint rather than a preference: <strong>readiness must not learn about work
 * orders.</strong>
 *
 * <p>If maintenance declared the port, readiness would have to import
 * {@code maintenance.application.ports} to implement it, and the dependency arrow would point from
 * the module that decides whether a hall is usable to the module that tracks who is fixing it. That
 * is backwards. Readiness is the deeper of the two: a space's readiness is a fact about the estate,
 * true whether or not anybody has raised a work order about it. Maintenance is one of several things
 * that can make that fact change, alongside assessments and asset failures.
 *
 * <p>So readiness declares what it will accept from outside, and maintenance depends on it. Nothing
 * in {@code readiness} names a fault, a work order or a schedule, and the ArchUnit rule
 * {@code readiness_does_not_depend_on_maintenance} holds that line.
 *
 * <h2>Why the signature carries no domain type</h2>
 *
 * Everything crossing this boundary is a primitive, a UUID or a readiness type. A method taking a
 * {@code FacilityFault} would put the same import back through the front door.
 */
public interface ExternalBlockerPort {

    /**
     * Raises or updates a blocker on a space from an outside source, and re-derives its readiness.
     *
     * <p>Idempotent by {@code (source, sourceReference)}: called twice with the same severity it does
     * nothing the second time, and called with a different severity it closes the old blocker and
     * raises one at the new level. That is what lets a caller reconcile on every save without
     * filling the queue with duplicates of one problem — the same contract
     * {@code reconcileAssetBlockers} already offers for assets.
     *
     * @param roomId the space affected. Nothing happens if it is unknown to the estate.
     * @param sourceReference the caller's stable identifier for the thing that is wrong, so it can
     *        find and close its own blocker later without touching anybody else's.
     * @return the blocker's id, or {@code null} when nothing was raised.
     */
    UUID raiseExternalBlocker(UUID roomId, BlockerSource source, String sourceReference,
            BlockerSeverity severity, String description, ActorContext actor, SourceChannel channel);

    /**
     * Closes every open blocker matching {@code (source, sourceReference)} and re-derives readiness.
     *
     * <p>Safe to call when there is nothing to close, which is the ordinary case: a caller resolving
     * something that never met the threshold should not have to remember whether it raised anything.
     *
     * @return how many blockers were closed.
     */
    int resolveExternalBlockers(BlockerSource source, String sourceReference, String resolutionNotes,
            ActorContext actor, SourceChannel channel);
}
