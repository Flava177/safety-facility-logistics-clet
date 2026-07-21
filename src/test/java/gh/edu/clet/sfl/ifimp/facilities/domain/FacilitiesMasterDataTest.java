package gh.edu.clet.sfl.ifimp.facilities.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class FacilitiesMasterDataTest {

    @Test
    void creates_site_with_normalized_site_code() {
        Site site = Site.create(UUID.randomUUID(), " main ", "Main Campus", "Head office", Instant.now());

        assertThat(site.siteCode()).isEqualTo("MAIN");
        assertThat(site.active()).isTrue();
    }

    @Test
    void creates_room_with_unknown_readiness_then_updates_status() {
        FacilityRoom room = FacilityRoom.create(
                UUID.randomUUID(), UUID.randomUUID(), "main", "hall-a", "Examination Hall A",
                "EXAM_HALL", 50, Instant.now());

        FacilityRoom ready = room.updateReadiness(LocationReadinessStatus.READY, "Pre-exam checks passed",
                Instant.now());

        assertThat(room.readinessStatus()).isEqualTo(LocationReadinessStatus.UNKNOWN);
        assertThat(ready.roomCode()).isEqualTo("HALL-A");
        assertThat(ready.readinessStatus()).isEqualTo(LocationReadinessStatus.READY);
        assertThat(ready.readinessNotes()).isEqualTo("Pre-exam checks passed");
    }

    @Test
    void rejects_room_with_negative_capacity() {
        assertThatThrownBy(() -> FacilityRoom.create(
                UUID.randomUUID(), UUID.randomUUID(), "MAIN", "HALL-A", "Examination Hall A",
                "EXAM_HALL", -1, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("capacity cannot be negative");
    }

    @Test
    void registers_device_reference_for_security_and_readiness_integrations() {
        DeviceReference camera = DeviceReference.register(
                UUID.randomUUID(), "main", "cam-001", "Main Gate Camera",
                DeviceReferenceType.CCTV_CAMERA, UUID.randomUUID(), "MAIN-GATE", "VMS Vendor", Instant.now());

        assertThat(camera.siteCode()).isEqualTo("MAIN");
        assertThat(camera.deviceCode()).isEqualTo("CAM-001");
        assertThat(camera.status()).isEqualTo(DeviceOperationalStatus.UNKNOWN);
        assertThat(camera.type()).isEqualTo(DeviceReferenceType.CCTV_CAMERA);
    }
}
