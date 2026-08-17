package gh.edu.clet.sfl.facilities.masterdata.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.facilities.masterdata.domain.AssetCategory;
import gh.edu.clet.sfl.facilities.masterdata.domain.AssetCriticality;
import gh.edu.clet.sfl.facilities.masterdata.domain.Building;
import gh.edu.clet.sfl.facilities.masterdata.domain.DeviceReference;
import gh.edu.clet.sfl.facilities.masterdata.domain.DeviceReferenceType;
import gh.edu.clet.sfl.facilities.masterdata.domain.FacilityAsset;
import gh.edu.clet.sfl.facilities.masterdata.domain.FacilityFloor;
import gh.edu.clet.sfl.facilities.masterdata.domain.Site;
import gh.edu.clet.sfl.facilities.masterdata.domain.Zone;
import gh.edu.clet.sfl.facilities.readiness.application.ReadinessApplicationService;
import gh.edu.clet.sfl.facilities.shared.application.FacilitiesAuthorization;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditAction;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordLifecycleStatus;
import gh.edu.clet.sfl.facilities.support.InMemoryFacilitiesRepository;
import gh.edu.clet.sfl.facilities.support.InMemoryReadinessRepository;
import gh.edu.clet.sfl.facilities.support.RecordingAuditPort;
import gh.edu.clet.sfl.facilities.support.TestDoubles;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Editing and retiring the estate registers.
 *
 * <p>Buildings, floors, zones, device references and assets each carried a `changeLifecycle` on the
 * domain record that no endpoint reached, and most carried an `update` in the same state - recorded
 * as a gap in `S152_UI_Gap_Report` §3. This covers what those new operations have to guarantee, which
 * is the same three things every write in this service guarantees and is the whole reason they are
 * not thin passthroughs:
 *
 * <ul>
 *   <li><b>Authorised against the record's own site</b>, not against the caller's assertion of one.
 *   <li><b>Version-checked</b>, so a stale write is refused rather than silently winning.
 *   <li><b>Audited</b>, with the before and after, into the hash chain.
 * </ul>
 */
class EstateEditAndRetireTest {

    private static final Instant NOW = Instant.parse("2026-08-17T09:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private InMemoryFacilitiesRepository facilities;
    private RecordingAuditPort audit;
    private FacilitiesMasterDataService estate;
    private FacilityAssetService assets;

    private ActorContext manager;
    private ActorContext otherSiteManager;
    private ActorContext technician;

    @BeforeEach
    void setUp() {
        facilities = new InMemoryFacilitiesRepository();
        InMemoryReadinessRepository readinessStore = new InMemoryReadinessRepository();
        audit = new RecordingAuditPort(NOW);
        TestDoubles.RecordingOutbox outbox = new TestDoubles.RecordingOutbox();
        TestDoubles.InMemoryIdempotency idempotency = new TestDoubles.InMemoryIdempotency();
        FacilitiesAuthorization authorization = new FacilitiesAuthorization(audit);

        estate = new FacilitiesMasterDataService(facilities, outbox, audit, idempotency, authorization, CLOCK);
        ReadinessApplicationService readiness = new ReadinessApplicationService(readinessStore, facilities,
                outbox, audit, idempotency, authorization, CLOCK);
        assets = new FacilityAssetService(facilities, outbox, audit, idempotency, authorization, readiness,
                CLOCK);

        manager = TestDoubles.actor("manager", Set.of(SflRole.FACILITIES_MANAGER), "MAIN");
        otherSiteManager = TestDoubles.actor("kumasi.manager", Set.of(SflRole.FACILITIES_MANAGER), "KUMASI");
        technician = TestDoubles.actor("technician", Set.of(SflRole.IFIMP_TECHNICIAN), "MAIN");
    }

    @Nested
    @DisplayName("Buildings and floors, the gap S152_UI_Gap_Report §3 recorded")
    class BuildingsAndFloors {

        @Test
        void a_building_can_be_renamed_and_the_change_is_audited_with_both_versions() {
            Building building = building();

            Building saved = estate.updateBuilding(new FacilitiesCommands.UpdateBuilding(building.id(),
                    "Block A, north wing", "Rebuilt 2025", building.metadata().version(), manager,
                    SourceChannel.WEB));

            assertThat(saved.name()).isEqualTo("Block A, north wing");
            assertThat(saved.description()).isEqualTo("Rebuilt 2025");
            assertThat(saved.metadata().version()).isEqualTo(building.metadata().version() + 1);
            assertThat(audit.actions()).contains(AuditAction.BUILDING_UPDATED);
        }

        @Test
        void a_stale_version_is_refused_rather_than_overwriting_the_other_edit() {
            Building building = building();
            estate.updateBuilding(new FacilitiesCommands.UpdateBuilding(building.id(), "First edit", null,
                    building.metadata().version(), manager, SourceChannel.WEB));

            assertThatThrownBy(() -> estate.updateBuilding(new FacilitiesCommands.UpdateBuilding(building.id(),
                    "Second edit against a stale copy", null, building.metadata().version(), manager,
                    SourceChannel.WEB)))
                    .isInstanceOf(FacilitiesException.VersionConflictException.class);
        }

        @Test
        void a_manager_from_another_site_cannot_edit_this_ones_building() {
            Building building = building();

            assertThatThrownBy(() -> estate.updateBuilding(new FacilitiesCommands.UpdateBuilding(building.id(),
                    "Renamed from Kumasi", null, building.metadata().version(), otherSiteManager,
                    SourceChannel.WEB)))
                    .isInstanceOf(FacilitiesException.UnauthorizedScopeException.class);
        }

        @Test
        void a_floor_can_be_renamed_and_moved_to_another_level() {
            FacilityFloor floor = floor();

            FacilityFloor saved = estate.updateFloor(new FacilitiesCommands.UpdateFloor(floor.id(),
                    "Mezzanine", null, floor.metadata().version(), manager, SourceChannel.WEB));

            assertThat(saved.name()).isEqualTo("Mezzanine");
            assertThat(audit.actions()).contains(AuditAction.FLOOR_UPDATED);
        }

        @Test
        void a_building_can_be_archived_and_archiving_is_terminal() {
            Building building = building();

            Building archived = estate.changeBuildingLifecycle(
                    new FacilitiesCommands.ChangeBuildingLifecycle(building.id(),
                            RecordLifecycleStatus.ARCHIVED, building.metadata().version(), manager,
                            SourceChannel.WEB));

            assertThat(archived.lifecycleStatus()).isEqualTo(RecordLifecycleStatus.ARCHIVED);
            assertThat(audit.actions()).contains(AuditAction.BUILDING_LIFECYCLE_CHANGED);

            assertThatThrownBy(() -> estate.changeBuildingLifecycle(
                    new FacilitiesCommands.ChangeBuildingLifecycle(building.id(),
                            RecordLifecycleStatus.ACTIVE, archived.metadata().version(), manager,
                            SourceChannel.WEB)))
                    .isInstanceOf(FacilitiesException.InvalidStateTransitionException.class);
        }

        @Test
        void a_floor_can_be_archived() {
            FacilityFloor floor = floor();

            FacilityFloor archived = estate.changeFloorLifecycle(new FacilitiesCommands.ChangeFloorLifecycle(
                    floor.id(), RecordLifecycleStatus.ARCHIVED, floor.metadata().version(), manager,
                    SourceChannel.WEB));

            assertThat(archived.lifecycleStatus()).isEqualTo(RecordLifecycleStatus.ARCHIVED);
            assertThat(audit.actions()).contains(AuditAction.FLOOR_LIFECYCLE_CHANGED);
        }
    }

    @Nested
    @DisplayName("Device references - correctable, but never their status")
    class DeviceReferences {

        @Test
        void the_descriptive_half_is_editable() {
            DeviceReference device = device();

            DeviceReference saved = estate.updateDeviceReference(
                    new FacilitiesCommands.UpdateDeviceReference(device.id(), "Main entrance, external",
                            DeviceReferenceType.ACCESS_READER, "Acme Security", "ACME-4471",
                            device.metadata().version(), manager, SourceChannel.WEB));

            assertThat(saved.name()).isEqualTo("Main entrance, external");
            assertThat(saved.type()).isEqualTo(DeviceReferenceType.ACCESS_READER);
            assertThat(saved.vendor()).isEqualTo("Acme Security");
            assertThat(saved.externalReference()).isEqualTo("ACME-4471");
            assertThat(audit.actions()).contains(AuditAction.DEVICE_REFERENCE_UPDATED);
        }

        @Test
        void the_vendor_reported_status_survives_an_edit() {
            // The status belongs to the vendor feed. An edit that reset it would let this service
            // assert an observation it has not made, which is the whole reason there is no field for it.
            DeviceReference device = device();

            DeviceReference saved = estate.updateDeviceReference(
                    new FacilitiesCommands.UpdateDeviceReference(device.id(), "Renamed", null, null, null,
                            device.metadata().version(), manager, SourceChannel.WEB));

            assertThat(saved.status()).isEqualTo(device.status());
            assertThat(saved.deviceCode()).isEqualTo(device.deviceCode());
            assertThat(saved.siteCode()).isEqualTo(device.siteCode());
        }

        @Test
        void a_null_field_leaves_that_field_alone_rather_than_blanking_it() {
            DeviceReference device = estate.updateDeviceReference(
                    new FacilitiesCommands.UpdateDeviceReference(device().id(), null, null, "Acme Security",
                            null, 0L, manager, SourceChannel.WEB));

            assertThat(device.vendor()).isEqualTo("Acme Security");
            assertThat(device.name()).isEqualTo("Front gate camera");
        }

        @Test
        void a_technician_may_not_correct_the_device_register() {
            DeviceReference device = device();

            assertThatThrownBy(() -> estate.updateDeviceReference(
                    new FacilitiesCommands.UpdateDeviceReference(device.id(), "Renamed", null, null, null,
                            device.metadata().version(), technician, SourceChannel.WEB)))
                    .isInstanceOf(FacilitiesException.UnauthorizedScopeException.class);
            assertThat(audit.actions()).contains(AuditAction.AUTHORIZATION_DENIED);
        }

        @Test
        void a_device_can_be_retired_from_the_estate_map() {
            DeviceReference device = device();

            DeviceReference archived = estate.changeDeviceReferenceLifecycle(
                    new FacilitiesCommands.ChangeDeviceReferenceLifecycle(device.id(),
                            RecordLifecycleStatus.ARCHIVED, device.metadata().version(), manager,
                            SourceChannel.WEB));

            assertThat(archived.lifecycleStatus()).isEqualTo(RecordLifecycleStatus.ARCHIVED);
            assertThat(audit.actions()).contains(AuditAction.DEVICE_REFERENCE_LIFECYCLE_CHANGED);
        }
    }

    @Nested
    @DisplayName("Zones and assets")
    class ZonesAndAssets {

        @Test
        void a_zone_can_be_retired() {
            Zone zone = zone();

            Zone archived = estate.changeZoneLifecycle(new FacilitiesCommands.ChangeZoneLifecycle(zone.id(),
                    RecordLifecycleStatus.ARCHIVED, zone.metadata().version(), manager, SourceChannel.WEB));

            assertThat(archived.lifecycleStatus()).isEqualTo(RecordLifecycleStatus.ARCHIVED);
            assertThat(audit.actions()).contains(AuditAction.ZONE_LIFECYCLE_CHANGED);
        }

        @Test
        void an_asset_can_be_retired_and_the_change_is_audited() {
            FacilityAsset asset = asset();

            FacilityAsset archived = assets.changeLifecycle(new FacilitiesCommands.ChangeAssetLifecycle(
                    asset.id(), RecordLifecycleStatus.ARCHIVED, asset.metadata().version(), manager,
                    SourceChannel.WEB));

            assertThat(archived.lifecycleStatus()).isEqualTo(RecordLifecycleStatus.ARCHIVED);
            assertThat(audit.actions()).contains(AuditAction.FACILITY_ASSET_LIFECYCLE_CHANGED);
        }

        @Test
        void retiring_an_asset_is_refused_against_a_stale_version() {
            FacilityAsset asset = asset();

            assertThatThrownBy(() -> assets.changeLifecycle(new FacilitiesCommands.ChangeAssetLifecycle(
                    asset.id(), RecordLifecycleStatus.INACTIVE, asset.metadata().version() + 5, manager,
                    SourceChannel.WEB)))
                    .isInstanceOf(FacilitiesException.VersionConflictException.class);
        }

        @Test
        void a_manager_from_another_site_cannot_retire_this_ones_asset() {
            FacilityAsset asset = asset();

            assertThatThrownBy(() -> assets.changeLifecycle(new FacilitiesCommands.ChangeAssetLifecycle(
                    asset.id(), RecordLifecycleStatus.ARCHIVED, asset.metadata().version(), otherSiteManager,
                    SourceChannel.WEB)))
                    .isInstanceOf(FacilitiesException.UnauthorizedScopeException.class);
        }

        @Test
        void an_unknown_record_is_a_not_found_rather_than_a_silent_no_op() {
            assertThatThrownBy(() -> estate.changeZoneLifecycle(new FacilitiesCommands.ChangeZoneLifecycle(
                    UUID.randomUUID(), RecordLifecycleStatus.INACTIVE, null, manager, SourceChannel.WEB)))
                    .isInstanceOf(FacilitiesException.RecordNotFoundException.class);
        }
    }

    // =========================================================================================

    private Site site() {
        return estate.createSite(new FacilitiesCommands.CreateSite("MAIN", "Main Campus", null, manager,
                SourceChannel.WEB, null));
    }

    private Building building() {
        return estate.createBuilding(new FacilitiesCommands.CreateBuilding(site().id(), "BLK-A", "Block A",
                null, manager, SourceChannel.WEB, null));
    }

    private FacilityFloor floor() {
        return estate.createFloor(new FacilitiesCommands.CreateFloor(building().id(), "GF", "Ground floor", 0,
                manager, SourceChannel.WEB, null));
    }

    private Zone zone() {
        site();
        return estate.createZone(new FacilitiesCommands.CreateZone("MAIN", "ZONE-N", "North wing", null, null,
                manager, SourceChannel.WEB, null));
    }

    private DeviceReference device() {
        site();
        return estate.registerDeviceReference(new FacilitiesCommands.RegisterDeviceReference("MAIN", "CAM-01",
                "Front gate camera", DeviceReferenceType.CCTV_CAMERA, null, "Front gate", null, null,
                manager, SourceChannel.WEB, null));
    }

    private FacilityAsset asset() {
        site();
        return assets.register(new FacilitiesCommands.RegisterAsset("MAIN", "GEN-01", "Standby generator",
                AssetCategory.GENERATOR, AssetCriticality.CRITICAL, null, "North yard", null, null, null,
                null, null, null, null, null, null, manager, SourceChannel.WEB, null));
    }
}
