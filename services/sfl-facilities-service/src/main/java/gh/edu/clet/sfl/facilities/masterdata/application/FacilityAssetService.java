package gh.edu.clet.sfl.facilities.masterdata.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.facilities.masterdata.application.ports.FacilitiesRepository;
import gh.edu.clet.sfl.facilities.masterdata.application.ports.SpaceReadinessPort;
import gh.edu.clet.sfl.facilities.masterdata.domain.AssetOperationalStatus;
import gh.edu.clet.sfl.facilities.masterdata.domain.FacilityAsset;
import gh.edu.clet.sfl.facilities.masterdata.domain.FacilityRoom;
import gh.edu.clet.sfl.facilities.shared.application.FacilitiesAuthorization;
import gh.edu.clet.sfl.facilities.shared.application.ServiceOutbox;
import gh.edu.clet.sfl.facilities.shared.application.port.AuditPort;
import gh.edu.clet.sfl.facilities.shared.application.port.IdempotencyPort;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditAction;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The facility asset register (SRS-SFL-S152-01, §21.1).
 *
 * <p>Separate from {@link FacilitiesMasterDataService} because the asset is what S153 will attach to,
 * and keeping it its own service means the maintenance module can depend on this without pulling in
 * the whole estate. It also has a behaviour the estate does not: an asset status change
 * <strong>recomputes readiness</strong> for the space it sits in, through {@link SpaceReadinessPort}.
 */
@Service
public class FacilityAssetService {

    private final FacilitiesRepository facilities;
    private final ServiceOutbox outbox;
    private final AuditPort audit;
    private final IdempotencyPort idempotency;
    private final FacilitiesAuthorization authorization;
    private final SpaceReadinessPort spaceReadiness;
    private final Clock clock;

    public FacilityAssetService(FacilitiesRepository facilities, ServiceOutbox outbox, AuditPort audit,
            IdempotencyPort idempotency, FacilitiesAuthorization authorization, SpaceReadinessPort spaceReadiness,
            Clock clock) {
        this.facilities = facilities;
        this.outbox = outbox;
        this.audit = audit;
        this.idempotency = idempotency;
        this.authorization = authorization;
        this.spaceReadiness = spaceReadiness;
        this.clock = clock;
    }

    @Transactional
    public FacilityAsset register(FacilitiesCommands.RegisterAsset command) {
        ActorContext actor = command.actor();
        String siteCode = normalize(command.siteCode());
        authorization.require(actor, SflPermission.FACILITIES_ASSET_MANAGE, siteCode, command.channel(),
                "FacilityAsset", normalize(command.assetCode()));

        if (command.idempotencyKey() != null && !command.idempotencyKey().isBlank()) {
            Optional<FacilityAsset> replayed = idempotency
                    .findExistingResult("register-asset", command.idempotencyKey(),
                            idempotency.fingerprint(command.idempotencyPayload()))
                    .flatMap(facilities::findAsset);
            if (replayed.isPresent()) {
                return replayed.get();
            }
        }

        String assetCode = normalize(command.assetCode());
        facilities.findAssetByCode(siteCode, assetCode).ifPresent(existing -> {
            if (existing.lifecycleStatus().occupiesIdentifier()) {
                throw new FacilitiesException.DuplicateIdentifierException("asset", assetCode, siteCode);
            }
        });
        if (command.roomId() != null) {
            requireRoomInSite(command.roomId(), siteCode);
        }

        FacilityAsset saved = facilities.saveAsset(FacilityAsset.register(UUID.randomUUID(), siteCode, assetCode,
                command.name(), command.category(), command.criticality(), command.roomId(),
                command.locationCode(), command.manufacturer(), command.modelNumber(), command.serialNumber(),
                command.installedOn(), command.warrantyExpiresOn(), command.serviceIntervalDays(),
                command.custodian(), command.deviceReferenceId(), command.assetReferenceId(), actor.actorId(),
                now(), command.channel(), actor.correlationId()));

        audit.record(actor, command.channel(), AuditAction.FACILITY_ASSET_REGISTERED, "FacilityAsset",
                saved.id().toString(), saved.siteCode(), null, saved);
        publish("sfl.ifimp.facility-asset-registered.v1", saved, actor);
        idempotency.recordResult("register-asset", command.idempotencyKey(),
                idempotency.fingerprint(command.idempotencyPayload()), saved.id(), saved.siteCode(),
                actor.actorId());
        return saved;
    }

    @Transactional
    public FacilityAsset update(FacilitiesCommands.UpdateAsset command) {
        ActorContext actor = command.actor();
        FacilityAsset asset = requireAsset(command.assetId());
        authorization.require(actor, SflPermission.FACILITIES_ASSET_MANAGE, asset.siteCode(), command.channel(),
                "FacilityAsset", asset.id().toString());
        asset.metadata().requireVersion(command.expectedVersion(), "Asset", asset.id());

        FacilityAsset saved = facilities.saveAsset(asset.update(command.name(), command.category(),
                command.criticality(), command.manufacturer(), command.modelNumber(), command.serialNumber(),
                command.warrantyExpiresOn(), command.serviceIntervalDays(), command.custodian(), actor.actorId(),
                now(), command.channel(), actor.correlationId()));

        audit.record(actor, command.channel(), AuditAction.FACILITY_ASSET_UPDATED, "FacilityAsset",
                saved.id().toString(), saved.siteCode(), asset, saved);
        publish("sfl.ifimp.facility-asset-updated.v1", saved, actor);

        // Criticality may have changed, which changes the severity of any blocker this asset raises.
        spaceReadiness.reconcileAssetBlockers(saved, actor, command.channel());
        return saved;
    }

    /**
     * Records that an asset's condition changed, and reconciles the readiness that depends on it.
     *
     * <p>The recompute is the point of the method. A generator going out of service is only useful
     * information if the examination hall it powers stops reporting itself ready — otherwise the fault
     * is recorded in one screen and contradicted in another.
     */
    @Transactional
    public FacilityAsset changeStatus(FacilitiesCommands.ChangeAssetStatus command) {
        ActorContext actor = command.actor();
        FacilityAsset asset = requireAsset(command.assetId());
        authorization.require(actor, SflPermission.FACILITIES_ASSET_MANAGE, asset.siteCode(), command.channel(),
                "FacilityAsset", asset.id().toString());
        asset.metadata().requireVersion(command.expectedVersion(), "Asset", asset.id());

        AssetOperationalStatus previous = asset.operationalStatus();
        FacilityAsset saved = facilities.saveAsset(asset.changeOperationalStatus(command.operationalStatus(),
                command.notes(), actor.actorId(), now(), command.channel(), actor.correlationId()));

        audit.record(actor, command.channel(), AuditAction.FACILITY_ASSET_STATUS_CHANGED, "FacilityAsset",
                saved.id().toString(), saved.siteCode(), previous, saved.operationalStatus());
        publish("sfl.ifimp.facility-asset-status-changed.v1", saved, actor);

        spaceReadiness.reconcileAssetBlockers(saved, actor, command.channel());
        return saved;
    }

    @Transactional
    public FacilityAsset relocate(FacilitiesCommands.RelocateAsset command) {
        ActorContext actor = command.actor();
        FacilityAsset asset = requireAsset(command.assetId());
        authorization.require(actor, SflPermission.FACILITIES_ASSET_MANAGE, asset.siteCode(), command.channel(),
                "FacilityAsset", asset.id().toString());
        asset.metadata().requireVersion(command.expectedVersion(), "Asset", asset.id());

        FacilityRoom previousRoom = asset.roomId() == null ? null : facilities.findRoom(asset.roomId()).orElse(null);
        if (command.roomId() != null) {
            requireRoomInSite(command.roomId(), asset.siteCode());
        }

        FacilityAsset saved = facilities.saveAsset(asset.relocate(command.roomId(), command.locationCode(),
                actor.actorId(), now(), command.channel(), actor.correlationId()));

        audit.record(actor, command.channel(), AuditAction.FACILITY_ASSET_RELOCATED, "FacilityAsset",
                saved.id().toString(), saved.siteCode(), asset.roomId(), saved.roomId());
        publish("sfl.ifimp.facility-asset-relocated.v1", saved, actor);

        // Both ends move: the space it left may recover, the space it joined may now be impaired.
        if (previousRoom != null) {
            spaceReadiness.reconcileAssetBlockers(asset, actor, command.channel());
        }
        spaceReadiness.reconcileAssetBlockers(saved, actor, command.channel());
        return saved;
    }

    @Transactional(readOnly = true)
    public FacilitiesRepository.Page<FacilityAsset> search(FacilitiesRepository.AssetQuery query,
            ActorContext actor, SourceChannel channel) {
        authorization.require(actor, SflPermission.FACILITIES_ASSET_READ, channel, "FacilityAsset", "search",
                query.siteCode());
        authorization.requireRequestedSite(actor, query.siteCode(), channel, "FacilityAsset");
        FacilitiesRepository.Page<FacilityAsset> page = facilities.searchAssets(query);
        var visible = authorization.filterBySite(actor, page.items(), FacilityAsset::siteCode);
        return visible.size() == page.items().size()
                ? page
                : FacilitiesRepository.Page.of(visible, visible.size(), page.page(), page.size());
    }

    @Transactional(readOnly = true)
    public FacilityAsset find(UUID id, ActorContext actor, SourceChannel channel) {
        FacilityAsset asset = requireAsset(id);
        authorization.require(actor, SflPermission.FACILITIES_ASSET_READ, asset.siteCode(), channel,
                "FacilityAsset", id.toString());
        return asset;
    }

    private FacilityAsset requireAsset(UUID id) {
        return facilities.findAsset(id)
                .orElseThrow(() -> new FacilitiesException.RecordNotFoundException("Asset", id));
    }

    private void requireRoomInSite(UUID roomId, String siteCode) {
        FacilityRoom room = facilities.findRoom(roomId)
                .orElseThrow(() -> new FacilitiesException.InvalidParentReferenceException("Space", roomId));
        if (!room.siteCode().equals(siteCode)) {
            throw new FacilitiesException.ValidationFailedException(
                    "Space " + room.roomCode() + " belongs to site " + room.siteCode() + ", not " + siteCode
                            + ".");
        }
    }

    private void publish(String eventType, FacilityAsset asset, ActorContext actor) {
        outbox.record(eventType, 1, "FacilityAsset", asset.id(), asset.siteCode(), actor.correlationId(),
                actor.actorId(), asset);
    }

    private Instant now() {
        return clock.instant();
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new FacilitiesException.MissingSiteScopeException();
        }
        return value.strip().toUpperCase(Locale.ROOT);
    }
}
