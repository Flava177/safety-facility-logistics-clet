package gh.edu.clet.sfl.facilities.masterdata.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.model.OperatingMode;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordLifecycleStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The estate's invariants (SRS-SFL-S152-01).
 *
 * <p>Exercised directly on the aggregates. Every rule here is one the application layer relies on
 * rather than re-checks, so a failure means a service is enforcing nothing.
 */
class FacilitiesMasterDataTest {

    private static final Instant NOW = Instant.parse("2026-07-30T09:00:00Z");
    private static final String ACTOR = "facilities.officer";

    @Test
    void creates_site_with_normalized_site_code() {
        Site site = Site.create(UUID.randomUUID(), " main ", "Main Campus", "Head office", ACTOR, NOW,
                SourceChannel.WEB, "corr-1");

        assertThat(site.siteCode()).isEqualTo("MAIN");
        assertThat(site.active()).isTrue();
        assertThat(site.lifecycleStatus()).isEqualTo(RecordLifecycleStatus.ACTIVE);
        assertThat(site.operatingMode()).isEqualTo(OperatingMode.ROUTINE);
    }

    @Test
    void records_the_system_managed_fields_on_creation() {
        Site site = Site.create(UUID.randomUUID(), "MAIN", "Main Campus", null, ACTOR, NOW,
                SourceChannel.MOBILE, "corr-2");

        assertThat(site.metadata().createdBy()).isEqualTo(ACTOR);
        assertThat(site.metadata().createdAt()).isEqualTo(NOW);
        assertThat(site.metadata().lastModifiedBy()).isEqualTo(ACTOR);
        assertThat(site.metadata().version()).isZero();
        assertThat(site.metadata().sourceChannel()).isEqualTo(SourceChannel.MOBILE);
        assertThat(site.metadata().correlationId()).isEqualTo("corr-2");
    }

    @Test
    void increments_the_version_on_every_change() {
        Site site = Site.create(UUID.randomUUID(), "MAIN", "Main Campus", null, ACTOR, NOW,
                SourceChannel.WEB, "corr-3");

        Site updated = site.update("Main Campus (North)", null, "another.officer",
                NOW.plusSeconds(60), SourceChannel.WEB, "corr-4");

        assertThat(updated.metadata().version()).isEqualTo(1L);
        assertThat(updated.metadata().lastModifiedBy()).isEqualTo("another.officer");
        assertThat(updated.metadata().createdBy()).isEqualTo(ACTOR);
    }

    @Test
    void rejects_a_write_built_on_a_stale_read() {
        Site site = Site.create(UUID.randomUUID(), "MAIN", "Main Campus", null, ACTOR, NOW,
                SourceChannel.WEB, null);
        Site updated = site.update("Renamed", null, ACTOR, NOW, SourceChannel.WEB, null);

        assertThatThrownBy(() -> updated.metadata().requireVersion(0L, "Site", updated.id()))
                .isInstanceOf(FacilitiesException.VersionConflictException.class)
                .hasMessageContaining("expected version 0 but found 1");
    }

    @Test
    void a_null_expected_version_accepts_last_write_wins() {
        Site site = Site.create(UUID.randomUUID(), "MAIN", "Main Campus", null, ACTOR, NOW,
                SourceChannel.WEB, null);

        site.metadata().requireVersion(null, "Site", site.id());
    }

    @Test
    void archived_is_terminal() {
        Site site = Site.create(UUID.randomUUID(), "MAIN", "Main Campus", null, ACTOR, NOW,
                SourceChannel.WEB, null);
        Site archived = site.changeLifecycle(RecordLifecycleStatus.ARCHIVED, ACTOR, NOW, SourceChannel.WEB,
                null);

        assertThatThrownBy(() -> archived.changeLifecycle(RecordLifecycleStatus.ACTIVE, ACTOR, NOW,
                SourceChannel.WEB, null))
                .isInstanceOf(FacilitiesException.InvalidStateTransitionException.class)
                .hasMessageContaining("cannot move from ARCHIVED to ACTIVE");
    }

    @Test
    void declares_and_stands_down_examination_mode() {
        Site site = Site.create(UUID.randomUUID(), "MAIN", "Main Campus", null, ACTOR, NOW,
                SourceChannel.WEB, null);

        Site inExam = site.changeOperatingMode(OperatingMode.EXAMINATION, "registrar", NOW,
                SourceChannel.WEB, null);

        assertThat(inExam.inExaminationMode()).isTrue();
        assertThat(inExam.operatingModeChangedBy()).isEqualTo("registrar");
        assertThat(inExam.operatingModeChangedAt()).isEqualTo(NOW);
        assertThat(inExam.changeOperatingMode(OperatingMode.ROUTINE, "registrar", NOW, SourceChannel.WEB, null)
                .inExaminationMode()).isFalse();
    }

    @Test
    void refuses_a_no_op_operating_mode_change() {
        Site site = Site.create(UUID.randomUUID(), "MAIN", "Main Campus", null, ACTOR, NOW,
                SourceChannel.WEB, null);

        assertThatThrownBy(() -> site.changeOperatingMode(OperatingMode.ROUTINE, ACTOR, NOW,
                SourceChannel.WEB, null))
                .isInstanceOf(FacilitiesException.OperatingModeTransitionException.class)
                .hasMessageContaining("already in ROUTINE");
    }

    @Test
    void creates_room_with_unknown_readiness_then_updates_status() {
        FacilityRoom room = examinationHall();

        FacilityRoom ready = room.applyReadiness(LocationReadinessStatus.READY, "Pre-exam checks passed",
                ACTOR, NOW, SourceChannel.WEB, null);

        assertThat(room.readinessStatus()).isEqualTo(LocationReadinessStatus.UNKNOWN);
        assertThat(ready.roomCode()).isEqualTo("HALL-A");
        assertThat(ready.readinessStatus()).isEqualTo(LocationReadinessStatus.READY);
        assertThat(ready.readinessNotes()).isEqualTo("Pre-exam checks passed");
        assertThat(ready.readinessUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void rejects_room_with_negative_capacity() {
        assertThatThrownBy(() -> FacilityRoom.create(UUID.randomUUID(), UUID.randomUUID(), "MAIN", "HALL-A",
                "Examination Hall A", SpaceType.EXAMINATION_HALL, -1, null, null, null, null, ACTOR, NOW,
                SourceChannel.WEB, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("capacity cannot be negative");
    }

    @Test
    void defaults_bookable_and_examination_capable_from_the_space_type() {
        FacilityRoom hall = examinationHall();
        FacilityRoom plantRoom = FacilityRoom.create(UUID.randomUUID(), UUID.randomUUID(), "MAIN", "PLANT-1",
                "Plant room", SpaceType.PLANT_ROOM, null, null, null, null, null, ACTOR, NOW,
                SourceChannel.WEB, null);

        assertThat(hall.bookable()).isTrue();
        assertThat(hall.examinationCapable()).isTrue();
        assertThat(plantRoom.bookable()).isFalse();
        assertThat(plantRoom.examinationCapable()).isFalse();
    }

    @Test
    void an_explicit_flag_overrides_the_space_type_default() {
        FacilityRoom hall = FacilityRoom.create(UUID.randomUUID(), UUID.randomUUID(), "MAIN", "HALL-B",
                "Hall under refurbishment", SpaceType.LECTURE_HALL, 100, null, null, false, false, ACTOR, NOW,
                SourceChannel.WEB, null);

        assertThat(hall.bookable()).isFalse();
        assertThat(hall.examinationCapable()).isFalse();
    }

    @Test
    void a_blocked_space_is_available_for_neither_booking_nor_examination() {
        FacilityRoom blocked = examinationHall().applyReadiness(LocationReadinessStatus.BLOCKED,
                "Fire door will not latch", ACTOR, NOW, SourceChannel.WEB, null);

        assertThat(blocked.availableForBooking()).isFalse();
        assertThat(blocked.availableForExamination()).isFalse();
    }

    @Test
    void a_degraded_space_still_books_but_cannot_host_an_examination() {
        FacilityRoom degraded = examinationHall().applyReadiness(LocationReadinessStatus.DEGRADED,
                "One projector out", ACTOR, NOW, SourceChannel.WEB, null);

        assertThat(degraded.availableForBooking()).isTrue();
        assertThat(degraded.availableForExamination()).isFalse();
    }

    @Test
    void a_locked_space_refuses_attribute_changes() {
        FacilityRoom locked = examinationHall().lockReadiness("supervisor", NOW, SourceChannel.WEB, null);

        assertThat(locked.readinessLocked()).isTrue();
        assertThat(locked.readinessLockedBy()).isEqualTo("supervisor");
        assertThatThrownBy(() -> locked.update("Renamed", null, null, null, null, null, null, ACTOR, NOW,
                SourceChannel.WEB, null))
                .isInstanceOf(FacilitiesException.ReadinessLockedException.class);
    }

    @Test
    void a_locked_space_still_accepts_a_readiness_outcome() {
        // The lock protects the space's attributes, not the assessment of it: an examination hall being
        // reassessed mid-lock is exactly what should happen when something fails.
        FacilityRoom locked = examinationHall().lockReadiness("supervisor", NOW, SourceChannel.WEB, null);

        FacilityRoom assessed = locked.applyReadiness(LocationReadinessStatus.BLOCKED, "Power failure", ACTOR,
                NOW, SourceChannel.WEB, null);

        assertThat(assessed.readinessStatus()).isEqualTo(LocationReadinessStatus.BLOCKED);
        assertThat(assessed.readinessLocked()).isTrue();
    }

    @Test
    void registers_device_reference_for_security_and_readiness_integrations() {
        DeviceReference camera = DeviceReference.register(UUID.randomUUID(), "main", "cam-001",
                "Main Gate Camera", DeviceReferenceType.CCTV_CAMERA, UUID.randomUUID(), "MAIN-GATE",
                "VMS Vendor", "VMS-77", ACTOR, NOW, SourceChannel.INTEGRATION, null);

        assertThat(camera.siteCode()).isEqualTo("MAIN");
        assertThat(camera.deviceCode()).isEqualTo("CAM-001");
        assertThat(camera.status()).isEqualTo(DeviceOperationalStatus.UNKNOWN);
        assertThat(camera.type()).isEqualTo(DeviceReferenceType.CCTV_CAMERA);
        assertThat(camera.externalReference()).isEqualTo("VMS-77");
    }

    @Test
    void a_device_status_report_keeps_the_vendors_observation_time() {
        Instant observedAt = NOW.minusSeconds(3600);
        DeviceReference camera = DeviceReference.register(UUID.randomUUID(), "MAIN", "CAM-001", "Camera",
                DeviceReferenceType.CCTV_CAMERA, null, null, "Vendor", null, ACTOR, NOW,
                SourceChannel.INTEGRATION, null);

        DeviceReference reported = camera.reportStatus(DeviceOperationalStatus.OFFLINE, observedAt, "feed",
                NOW, SourceChannel.INTEGRATION, null);

        assertThat(reported.status()).isEqualTo(DeviceOperationalStatus.OFFLINE);
        assertThat(reported.statusReportedAt()).isEqualTo(observedAt);
    }

    private static FacilityRoom examinationHall() {
        return FacilityRoom.create(UUID.randomUUID(), UUID.randomUUID(), "main", "hall-a",
                "Examination Hall A", SpaceType.EXAMINATION_HALL, 50, null, null, null, null, ACTOR, NOW,
                SourceChannel.WEB, null);
    }
}
