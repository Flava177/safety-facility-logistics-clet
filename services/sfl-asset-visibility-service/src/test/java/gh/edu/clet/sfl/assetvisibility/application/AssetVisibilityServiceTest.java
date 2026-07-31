package gh.edu.clet.sfl.assetvisibility.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import gh.edu.clet.sfl.assetvisibility.application.ports.AssetReferenceRepository;
import gh.edu.clet.sfl.assetvisibility.domain.AssetCategory;
import gh.edu.clet.sfl.assetvisibility.domain.AssetReference;
import gh.edu.clet.sfl.assetvisibility.domain.LocationType;
import org.junit.jupiter.api.Test;

class AssetVisibilityServiceTest {

    @Test
    void registers_asset_and_records_outbox_event() {
        InMemoryAssetReferenceRepository repository = new InMemoryAssetReferenceRepository();
        RecordingOutbox outbox = new RecordingOutbox();
        AssetVisibilityService service = new AssetVisibilityService(repository, outbox,
                Clock.fixed(Instant.parse("2026-07-13T08:00:00Z"), ZoneOffset.UTC));

        AssetReference asset = service.register(new RegisterAssetCommand("cam-001", "Main Gate Camera",
                AssetCategory.CCTV_CAMERA, "main", LocationType.ROOM, "room-a", "security", "vms-1001",
                "operator@sfl.local", "corr-1"));

        assertThat(asset.assetCode()).isEqualTo("CAM-001");
        assertThat(repository.findByAssetCode("CAM-001")).isPresent();
        assertThat(outbox.events).hasSize(1);
        assertThat(outbox.events.get(0).eventType).isEqualTo("sfl.avamp.asset-registered.v1");
        assertThat(outbox.events.get(0).siteScope).isEqualTo("MAIN");
    }

    @Test
    void rejects_duplicate_asset_code() {
        InMemoryAssetReferenceRepository repository = new InMemoryAssetReferenceRepository();
        AssetVisibilityService service = new AssetVisibilityService(repository, new RecordingOutbox(),
                Clock.fixed(Instant.parse("2026-07-13T08:00:00Z"), ZoneOffset.UTC));
        service.register(new RegisterAssetCommand("cam-001", "Main Gate Camera", AssetCategory.CCTV_CAMERA,
                "main", LocationType.ROOM, "room-a", null, null, null, null));

        assertThatThrownBy(() -> service.register(new RegisterAssetCommand("CAM-001", "Duplicate Camera",
                AssetCategory.CCTV_CAMERA, "main", LocationType.ROOM, "room-b", null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Asset already exists: CAM-001");
    }

    private static class InMemoryAssetReferenceRepository implements AssetReferenceRepository {
        private final Map<UUID, AssetReference> assets = new LinkedHashMap<>();

        @Override
        public AssetReference save(AssetReference assetReference) {
            assets.put(assetReference.id(), assetReference);
            return assetReference;
        }

        @Override
        public Optional<AssetReference> findById(UUID id) {
            return Optional.ofNullable(assets.get(id));
        }

        @Override
        public Optional<AssetReference> findByAssetCode(String assetCode) {
            return assets.values().stream().filter(asset -> asset.assetCode().equals(assetCode)).findFirst();
        }

        @Override
        public List<AssetReference> findAll(String siteCode) {
            return new ArrayList<>(assets.values());
        }

        @Override
        public List<AssetReference> findByLocation(String siteCode, LocationType locationType, String locationReference) {
            return assets.values().stream()
                    .filter(asset -> asset.siteCode().equals(siteCode))
                    .filter(asset -> asset.locationType() == locationType)
                    .filter(asset -> asset.locationReference().equals(locationReference))
                    .toList();
        }
    }

    private static class RecordingOutbox implements ServiceOutbox {
        private final List<RecordedEvent> events = new ArrayList<>();

        @Override
        public void record(String eventType, int eventVersion, String aggregateType, UUID aggregateId,
                String siteScope, String correlationId, String causationId, Object payload) {
            events.add(new RecordedEvent(eventType, eventVersion, aggregateType, aggregateId, siteScope,
                    correlationId, causationId, payload));
        }
    }

    private record RecordedEvent(String eventType, int eventVersion, String aggregateType, UUID aggregateId,
            String siteScope, String correlationId, String causationId, Object payload) {
    }
}