package gh.edu.clet.sfl.assetvisibility.application;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import gh.edu.clet.sfl.assetvisibility.application.ports.AssetReferenceRepository;
import gh.edu.clet.sfl.assetvisibility.domain.AssetReference;
import gh.edu.clet.sfl.assetvisibility.domain.LocationType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssetVisibilityService {

    private final AssetReferenceRepository assets;
    private final ServiceOutbox outbox;
    private final Clock clock;

    public AssetVisibilityService(AssetReferenceRepository assets, ServiceOutbox outbox, Clock clock) {
        this.assets = assets;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public AssetReference register(RegisterAssetCommand command) {
        String assetCode = normalize(command.assetCode(), "assetCode");
        assets.findByAssetCode(assetCode).ifPresent(existing -> {
            throw new IllegalArgumentException("Asset already exists: " + assetCode);
        });
        AssetReference asset = AssetReference.register(UUID.randomUUID(), assetCode, command.name(),
                command.category(), command.siteCode(), command.locationType(), command.locationReference(),
                command.custodianReference(), command.externalReference(), clock.instant());
        AssetReference saved = assets.save(asset);
        record("sfl.avamp.asset-registered.v1", "AssetReference", saved, command.actor(), command.correlationId());
        return saved;
    }

    @Transactional
    public AssetReference move(MoveAssetCommand command) {
        AssetReference asset = requireAsset(command.assetId());
        AssetReference saved = assets.save(asset.moveTo(command.locationType(), command.locationReference(), clock.instant()));
        record("sfl.avamp.asset-location-changed.v1", "AssetReference", saved, command.actor(), command.correlationId());
        return saved;
    }

    @Transactional
    public AssetReference assignCustody(AssignCustodyCommand command) {
        AssetReference asset = requireAsset(command.assetId());
        AssetReference saved = assets.save(asset.assignCustodian(command.custodianReference(), clock.instant()));
        record("sfl.avamp.asset-custody-changed.v1", "AssetReference", saved, command.actor(), command.correlationId());
        return saved;
    }

    @Transactional
    public AssetReference linkEvidence(LinkEvidenceCommand command) {
        AssetReference asset = requireAsset(command.assetId());
        AssetReference saved = assets.save(asset.linkEvidence(command.evidenceReference(), clock.instant()));
        record("sfl.avamp.asset-evidence-linked.v1", "AssetReference", saved, command.actor(), command.correlationId());
        return saved;
    }

    @Transactional(readOnly = true)
    public AssetReference findById(UUID id) {
        return requireAsset(id);
    }

    @Transactional(readOnly = true)
    public List<AssetReference> findAll(String siteCode) {
        return assets.findAll(siteCode);
    }

    @Transactional(readOnly = true)
    public List<AssetReference> findByLocation(String siteCode, LocationType locationType, String locationReference) {
        return assets.findByLocation(siteCode, locationType, locationReference);
    }

    private AssetReference requireAsset(UUID id) {
        return assets.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Asset was not found: " + id));
    }

    private void record(String eventType, String aggregateType, AssetReference asset, String actor, String correlationId) {
        outbox.record(eventType, 1, aggregateType, asset.id(), asset.siteCode(), correlationId,
                actorOrDevelopment(actor), asset);
    }

    private String actorOrDevelopment(String actor) {
        return actor == null || actor.isBlank() ? "development-user" : actor;
    }

    private String normalize(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.strip().toUpperCase(Locale.ROOT);
    }
}