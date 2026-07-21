package gh.edu.clet.sfl.assetvisibility.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class AssetReferenceTest {

    @Test
    void registers_asset_with_normalized_codes() {
        AssetReference asset = AssetReference.register(UUID.randomUUID(), " cam-001 ", "Main Gate Camera",
                AssetCategory.CCTV_CAMERA, " main ", LocationType.ROOM, " room-a ", "security", "vms-1001",
                Instant.parse("2026-07-13T08:00:00Z"));

        assertThat(asset.assetCode()).isEqualTo("CAM-001");
        assertThat(asset.siteCode()).isEqualTo("MAIN");
        assertThat(asset.locationReference()).isEqualTo("ROOM-A");
        assertThat(asset.status()).isEqualTo(AssetStatus.ACTIVE);
    }

    @Test
    void moves_asset_without_losing_external_or_custody_reference() {
        Instant createdAt = Instant.parse("2026-07-13T08:00:00Z");
        AssetReference asset = AssetReference.register(UUID.randomUUID(), "CAM-001", "Main Gate Camera",
                AssetCategory.CCTV_CAMERA, "MAIN", LocationType.ROOM, "ROOM-A", "security", "vms-1001",
                createdAt);

        AssetReference moved = asset.moveTo(LocationType.ZONE, "main-gate", createdAt.plusSeconds(60));

        assertThat(moved.locationType()).isEqualTo(LocationType.ZONE);
        assertThat(moved.locationReference()).isEqualTo("MAIN-GATE");
        assertThat(moved.custodianReference()).isEqualTo("security");
        assertThat(moved.externalReference()).isEqualTo("vms-1001");
        assertThat(moved.updatedAt()).isAfter(moved.createdAt());
    }

    @Test
    void rejects_asset_without_location_reference() {
        assertThatThrownBy(() -> AssetReference.register(UUID.randomUUID(), "CAM-001", "Main Gate Camera",
                AssetCategory.CCTV_CAMERA, "MAIN", LocationType.ROOM, " ", null, null, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("locationReference is required");
    }
}