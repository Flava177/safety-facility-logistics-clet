package gh.edu.clet.sfl.facilities.readiness.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.facilities.masterdata.application.ports.FacilitiesRepository;
import gh.edu.clet.sfl.facilities.masterdata.domain.FacilityAsset;
import gh.edu.clet.sfl.facilities.masterdata.domain.FacilityRoom;
import gh.edu.clet.sfl.facilities.readiness.application.ports.ReadinessRepository;
import gh.edu.clet.sfl.facilities.readiness.domain.BlockerSeverity;
import gh.edu.clet.sfl.facilities.readiness.domain.BlockerSource;
import gh.edu.clet.sfl.facilities.readiness.domain.ReadinessBlocker;
import gh.edu.clet.sfl.facilities.shared.application.FacilitiesAuthorization;
import gh.edu.clet.sfl.facilities.shared.application.port.AuditPort;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditAction;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Manual blocker commands plus the {@code SpaceReadinessPort}/{@code ExternalBlockerPort} bodies -
 * split out of {@link ReadinessApplicationService} for the same reason the rest of that class's
 * Javadoc gives. Genuinely one concern: every method here is "reconcile this space's blockers from
 * one source against what should be open", whether the source is a human, an asset, or another
 * module's {@code raiseExternalBlocker}/{@code resolveExternalBlockers} call.
 *
 * <p>The two port interfaces themselves stay implemented on {@link ReadinessApplicationService} -
 * {@code FacilityAssetService} and {@code FacilityFaultService} depend on those interface types, not
 * on this class, and Spring still wires the same bean either way - but each override there is a
 * one-line delegation to a method here.
 */
final class ReadinessBlockerOperations {

    private final ReadinessApplicationService service;
    private final ReadinessRepository readiness;
    private final FacilitiesRepository facilities;
    private final FacilitiesAuthorization authorization;
    private final AuditPort audit;

    ReadinessBlockerOperations(ReadinessApplicationService service, ReadinessRepository readiness,
            FacilitiesRepository facilities, FacilitiesAuthorization authorization, AuditPort audit) {
        this.service = service;
        this.readiness = readiness;
        this.facilities = facilities;
        this.authorization = authorization;
        this.audit = audit;
    }

    ReadinessBlocker raise(ReadinessCommands.RaiseBlocker command) {
        ActorContext actor = command.actor();
        FacilityRoom room = service.requireRoom(command.roomId());
        authorization.require(actor, SflPermission.FACILITIES_READINESS_ASSESS, room.siteCode(),
                command.channel(), "ReadinessBlocker", room.id().toString());

        Instant at = service.now();
        ReadinessBlocker blocker = readiness.saveBlocker(ReadinessBlocker.raise(room.id(), room.siteCode(), null,
                BlockerSource.MANUAL, null, command.severity(), command.description(), actor.actorId(), at));

        service.applyOutcome(room, service.evaluate(room.id()), actor, command.channel(), at);

        audit.record(actor, command.channel(), AuditAction.READINESS_BLOCKER_RAISED, "ReadinessBlocker",
                blocker.id().toString(), room.siteCode(), null, blocker);
        service.publish("sfl.ifimp.readiness-blocker-created.v1", "ReadinessBlocker", blocker.id(), room.siteCode(),
                actor, blocker);
        return blocker;
    }

    /**
     * Closes a blocker and re-derives the space's readiness.
     *
     * <p>Resolving the last open critical blocker is what lets a space become READY again, so the
     * recompute is not an optimisation - it is the second half of the operation.
     */
    ReadinessBlocker resolve(ReadinessCommands.ResolveBlocker command) {
        ActorContext actor = command.actor();
        ReadinessBlocker blocker = readiness.findBlocker(command.blockerId())
                .orElseThrow(() -> new FacilitiesException.RecordNotFoundException("Readiness blocker",
                        command.blockerId()));
        authorization.require(actor, SflPermission.FACILITIES_READINESS_ASSESS, blocker.siteCode(),
                command.channel(), "ReadinessBlocker", blocker.id().toString());

        Instant at = service.now();
        ReadinessBlocker resolved = readiness.saveBlocker(
                blocker.resolve(command.resolutionNotes(), actor.actorId(), at));

        FacilityRoom room = service.requireRoom(resolved.roomId());
        service.applyOutcome(room, service.evaluate(room.id()), actor, command.channel(), at);

        audit.record(actor, command.channel(), AuditAction.READINESS_BLOCKER_RESOLVED, "ReadinessBlocker",
                resolved.id().toString(), resolved.siteCode(), blocker, resolved);
        service.publish("sfl.ifimp.readiness-blocker-resolved.v1", "ReadinessBlocker", resolved.id(),
                resolved.siteCode(), actor, resolved);
        return resolved;
    }

    /**
     * Reconciles the blockers derived from one asset.
     *
     * <p>Keyed on the asset's id as the blocker's {@code sourceReference}, so an asset that recovers
     * closes exactly the blockers it opened and nothing else. An asset that is impaired but already has
     * an open blocker at the right severity is left alone - re-raising on every save would fill the
     * queue with duplicates of one fault.
     */
    void reconcileAssetBlockers(FacilityAsset asset, ActorContext actor, SourceChannel channel) {
        if (asset == null || asset.roomId() == null) {
            return;
        }
        Optional<FacilityRoom> maybeRoom = facilities.findRoom(asset.roomId());
        if (maybeRoom.isEmpty()) {
            return;
        }
        FacilityRoom room = maybeRoom.get();
        Instant at = service.now();
        String reference = asset.id().toString();
        List<ReadinessBlocker> existing = readiness.findOpenBlockersBySource(BlockerSource.ASSET, reference);

        if (asset.impairsReadiness()) {
            BlockerSeverity severity = severityFor(asset);
            boolean alreadyRaised = existing.stream().anyMatch(blocker -> blocker.severity() == severity);
            // Severity may have changed with the asset's criticality or status; close what no longer fits.
            existing.stream()
                    .filter(blocker -> blocker.severity() != severity)
                    .forEach(blocker -> readiness.saveBlocker(blocker.resolve(
                            "Superseded: asset severity is now " + severity, actor.actorId(), at)));
            if (!alreadyRaised) {
                ReadinessBlocker blocker = readiness.saveBlocker(ReadinessBlocker.raise(room.id(),
                        room.siteCode(), null, BlockerSource.ASSET, reference, severity,
                        asset.assetCode() + " (" + asset.category() + ") is " + asset.operationalStatus(),
                        actor.actorId(), at));
                audit.record(actor, channel, AuditAction.READINESS_BLOCKER_RAISED, "ReadinessBlocker",
                        blocker.id().toString(), room.siteCode(), null, blocker);
                service.publish("sfl.ifimp.readiness-blocker-created.v1", "ReadinessBlocker", blocker.id(),
                        room.siteCode(), actor, blocker);
            }
        } else {
            existing.forEach(blocker -> {
                ReadinessBlocker resolved = readiness.saveBlocker(blocker.resolve(
                        "Asset " + asset.assetCode() + " returned to " + asset.operationalStatus(),
                        actor.actorId(), at));
                audit.record(actor, channel, AuditAction.READINESS_BLOCKER_RESOLVED, "ReadinessBlocker",
                        resolved.id().toString(), room.siteCode(), blocker, resolved);
                service.publish("sfl.ifimp.readiness-blocker-resolved.v1", "ReadinessBlocker", resolved.id(),
                        room.siteCode(), actor, resolved);
            });
        }

        service.applyOutcome(room, service.evaluate(room.id()), actor, channel, at);
    }

    /**
     * The body of {@code ExternalBlockerPort#raiseExternalBlocker}: find what this source already has
     * open, leave it alone if it is already at the right severity, close it if the severity has moved,
     * raise one if there is none. Written out rather than shared with {@link #reconcileAssetBlockers}
     * because that method also decides *whether* an asset is impaired, which is a judgement only it
     * can make; this one is told the severity and applies it.
     */
    UUID raiseExternal(UUID roomId, BlockerSource source, String sourceReference, BlockerSeverity severity,
            String description, ActorContext actor, SourceChannel channel) {
        if (roomId == null || severity == null) {
            return null;
        }
        Optional<FacilityRoom> maybeRoom = facilities.findRoom(roomId);
        if (maybeRoom.isEmpty()) {
            // A caller may legitimately reference a location the estate has no room for - a corridor,
            // a car park. Silently doing nothing is correct: there is no space whose readiness could
            // change, and refusing would make the caller's own write fail for a reason it cannot fix.
            return null;
        }
        FacilityRoom room = maybeRoom.get();
        Instant at = service.now();
        List<ReadinessBlocker> existing = readiness.findOpenBlockersBySource(source, sourceReference);

        Optional<ReadinessBlocker> alreadyRight = existing.stream()
                .filter(blocker -> blocker.severity() == severity)
                .findFirst();
        existing.stream()
                .filter(blocker -> blocker.severity() != severity)
                .forEach(blocker -> readiness.saveBlocker(blocker.resolve(
                        "Superseded: severity is now " + severity, actor.actorId(), at)));

        UUID blockerId;
        if (alreadyRight.isPresent()) {
            blockerId = alreadyRight.get().id();
        } else {
            ReadinessBlocker raised = readiness.saveBlocker(ReadinessBlocker.raise(room.id(), room.siteCode(),
                    null, source, sourceReference, severity, description, actor.actorId(), at));
            audit.record(actor, channel, AuditAction.READINESS_BLOCKER_RAISED, "ReadinessBlocker",
                    raised.id().toString(), room.siteCode(), null, raised);
            service.publish("sfl.ifimp.readiness-blocker-created.v1", "ReadinessBlocker", raised.id(),
                    room.siteCode(), actor, raised);
            blockerId = raised.id();
        }

        service.applyOutcome(room, service.evaluate(room.id()), actor, channel, at);
        return blockerId;
    }

    int resolveExternal(BlockerSource source, String sourceReference, String resolutionNotes, ActorContext actor,
            SourceChannel channel) {
        List<ReadinessBlocker> open = readiness.findOpenBlockersBySource(source, sourceReference);
        if (open.isEmpty()) {
            return 0;
        }
        Instant at = service.now();
        // One source can hold blockers on more than one space only if the caller reuses a reference
        // across rooms, which nothing does today - but re-deriving per distinct room rather than per
        // blocker costs nothing and does not assume it.
        Set<UUID> touched = new LinkedHashSet<>();
        for (ReadinessBlocker blocker : open) {
            ReadinessBlocker resolved = readiness.saveBlocker(
                    blocker.resolve(resolutionNotes, actor.actorId(), at));
            audit.record(actor, channel, AuditAction.READINESS_BLOCKER_RESOLVED, "ReadinessBlocker",
                    resolved.id().toString(), resolved.siteCode(), blocker, resolved);
            service.publish("sfl.ifimp.readiness-blocker-resolved.v1", "ReadinessBlocker", resolved.id(),
                    resolved.siteCode(), actor, resolved);
            touched.add(blocker.roomId());
        }
        touched.forEach(roomId -> facilities.findRoom(roomId)
                .ifPresent(room -> service.applyOutcome(room, service.evaluate(room.id()), actor, channel, at)));
        return open.size();
    }

    /**
     * The blocker severity an impaired asset earns.
     *
     * <p>Criticality sets the ceiling and status sets how much of it applies: a critical asset that is
     * out of service blocks the space, the same asset merely degraded impairs it. A low-criticality
     * asset never rises above advisory however broken it is - a failed noticeboard light does not stop
     * an examination.
     */
    private static BlockerSeverity severityFor(FacilityAsset asset) {
        return switch (asset.criticality()) {
            case CRITICAL -> asset.operationalStatus().isTotalFailure()
                    ? BlockerSeverity.CRITICAL
                    : BlockerSeverity.MAJOR;
            case HIGH -> asset.operationalStatus().isTotalFailure()
                    ? BlockerSeverity.MAJOR
                    : BlockerSeverity.MINOR;
            case MEDIUM -> BlockerSeverity.MINOR;
            case LOW -> BlockerSeverity.ADVISORY;
        };
    }
}
