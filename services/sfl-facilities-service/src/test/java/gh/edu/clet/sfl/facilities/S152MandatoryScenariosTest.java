package gh.edu.clet.sfl.facilities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.facilities.dashboard.application.FacilityDashboardService;
import gh.edu.clet.sfl.facilities.dashboard.domain.FacilityDashboard;
import gh.edu.clet.sfl.facilities.masterdata.application.FacilitiesCommands;
import gh.edu.clet.sfl.facilities.masterdata.application.FacilitiesMasterDataService;
import gh.edu.clet.sfl.facilities.masterdata.application.FacilityAssetService;
import gh.edu.clet.sfl.facilities.masterdata.domain.AssetCategory;
import gh.edu.clet.sfl.facilities.masterdata.domain.AssetCriticality;
import gh.edu.clet.sfl.facilities.masterdata.domain.AssetOperationalStatus;
import gh.edu.clet.sfl.facilities.masterdata.domain.Building;
import gh.edu.clet.sfl.facilities.masterdata.domain.DeviceReference;
import gh.edu.clet.sfl.facilities.masterdata.domain.DeviceReferenceType;
import gh.edu.clet.sfl.facilities.masterdata.domain.FacilityAsset;
import gh.edu.clet.sfl.facilities.masterdata.domain.FacilityFloor;
import gh.edu.clet.sfl.facilities.masterdata.domain.FacilityRoom;
import gh.edu.clet.sfl.facilities.masterdata.domain.LocationReadinessStatus;
import gh.edu.clet.sfl.facilities.masterdata.domain.Site;
import gh.edu.clet.sfl.facilities.masterdata.domain.SpaceType;
import gh.edu.clet.sfl.facilities.masterdata.domain.Zone;
import gh.edu.clet.sfl.facilities.masterdata.domain.ZoneMemberType;
import gh.edu.clet.sfl.facilities.readiness.application.ReadinessApplicationService;
import gh.edu.clet.sfl.facilities.readiness.application.ReadinessCommands;
import gh.edu.clet.sfl.facilities.readiness.domain.BlockerSeverity;
import gh.edu.clet.sfl.facilities.readiness.domain.ReadinessAssessment;
import gh.edu.clet.sfl.facilities.readiness.domain.ReadinessBlocker;
import gh.edu.clet.sfl.facilities.readiness.domain.ReadinessChecklist;
import gh.edu.clet.sfl.facilities.shared.application.FacilitiesAuthorization;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditAction;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.model.OperatingMode;
import gh.edu.clet.sfl.facilities.support.InMemoryFacilitiesRepository;
import gh.edu.clet.sfl.facilities.support.InMemoryReadinessRepository;
import gh.edu.clet.sfl.facilities.support.RecordingAuditPort;
import gh.edu.clet.sfl.facilities.support.TestDoubles;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The ten mandatory S152 scenarios, end to end through the application services.
 *
 * <p>Each nested class is one scenario from the build brief. They run against in-memory adapters, so a
 * failure points at a rule rather than at a mapping; the Testcontainers test covers persistence and
 * the migrations.
 */
class S152MandatoryScenariosTest {

    private static final Instant NOW = Instant.parse("2026-07-30T09:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private InMemoryFacilitiesRepository facilities;
    private InMemoryReadinessRepository readinessStore;
    private RecordingAuditPort audit;
    private TestDoubles.RecordingOutbox outbox;
    private TestDoubles.StubMaintenance maintenance;
    private FacilitiesMasterDataService estate;
    private FacilityAssetService assets;
    private ReadinessApplicationService readiness;
    private FacilityDashboardService dashboard;

    private ActorContext manager;
    private ActorContext supervisor;
    private ActorContext otherSiteManager;

    @BeforeEach
    void setUp() {
        facilities = new InMemoryFacilitiesRepository();
        readinessStore = new InMemoryReadinessRepository();
        audit = new RecordingAuditPort(NOW);
        outbox = new TestDoubles.RecordingOutbox();
        maintenance = new TestDoubles.StubMaintenance();
        TestDoubles.InMemoryIdempotency idempotency = new TestDoubles.InMemoryIdempotency();
        FacilitiesAuthorization authorization = new FacilitiesAuthorization(audit);

        estate = new FacilitiesMasterDataService(facilities, outbox, audit, idempotency, authorization, CLOCK);
        readiness = new ReadinessApplicationService(readinessStore, facilities, outbox, audit, idempotency,
                authorization, CLOCK);
        assets = new FacilityAssetService(facilities, outbox, audit, idempotency, authorization, readiness,
                CLOCK);
        dashboard = new FacilityDashboardService(facilities, readinessStore, maintenance,
                new TestDoubles.InMemoryConfiguration(), authorization, CLOCK);

        manager = TestDoubles.actor("manager", Set.of(SflRole.FACILITIES_MANAGER), "MAIN");
        supervisor = TestDoubles.actor("supervisor", Set.of(SflRole.IFIMP_MAINTENANCE_SUPERVISOR), "MAIN");
        otherSiteManager = TestDoubles.actor("kumasi.manager", Set.of(SflRole.FACILITIES_MANAGER), "KUMASI");
    }

    // =========================================================================================

    @Nested
    @DisplayName("1. Create site, building, floor and room")
    class BuildTheHierarchy {

        @Test
        void creates_the_full_hierarchy_and_links_each_level_to_its_parent() {
            Site site = createSite();
            Building building = createBuilding(site);
            FacilityFloor floor = createFloor(building);
            FacilityRoom room = createRoom(floor, "HALL-A", SpaceType.EXAMINATION_HALL);

            assertThat(site.siteCode()).isEqualTo("MAIN");
            assertThat(building.siteId()).isEqualTo(site.id());
            assertThat(building.siteCode()).isEqualTo("MAIN");
            assertThat(floor.buildingId()).isEqualTo(building.id());
            assertThat(room.floorId()).isEqualTo(floor.id());
            assertThat(room.siteCode()).isEqualTo("MAIN");
            assertThat(room.readinessStatus()).isEqualTo(LocationReadinessStatus.UNKNOWN);
        }

        @Test
        void refuses_a_floor_whose_building_does_not_exist() {
            assertThatThrownBy(() -> estate.createFloor(new FacilitiesCommands.CreateFloor(UUID.randomUUID(),
                    "GF", "Ground floor", 0, manager, SourceChannel.WEB, null)))
                    .isInstanceOf(FacilitiesException.InvalidParentReferenceException.class)
                    .hasMessageContaining("Building referenced by this request does not exist");
        }
    }

    @Nested
    @DisplayName("2. Reject a duplicate room code in the same site")
    class DuplicateIdentifiers {

        @Test
        void refuses_a_second_space_with_the_same_code() {
            FacilityFloor floor = createFloor(createBuilding(createSite()));
            createRoom(floor, "HALL-A", SpaceType.EXAMINATION_HALL);

            assertThatThrownBy(() -> createRoom(floor, "hall-a", SpaceType.LECTURE_HALL))
                    .isInstanceOf(FacilitiesException.DuplicateIdentifierException.class)
                    .hasMessageContaining("An active space with identifier 'HALL-A' already exists for site "
                            + "MAIN");
        }

        @Test
        void refuses_a_duplicate_site_building_zone_and_asset_code_too() {
            Site site = createSite();
            createBuilding(site);

            assertThatThrownBy(() -> estate.createSite(new FacilitiesCommands.CreateSite("MAIN", "Again", null,
                    manager, SourceChannel.WEB, null)))
                    .isInstanceOf(FacilitiesException.DuplicateIdentifierException.class);
            assertThatThrownBy(() -> createBuilding(site))
                    .isInstanceOf(FacilitiesException.DuplicateIdentifierException.class);
        }

        @Test
        void an_archived_record_releases_its_identifier() {
            // Archival is retirement, not deletion — but the code becomes reusable, which is what lets a
            // demolished building's code be given to its replacement.
            Site site = createSite();
            Building building = createBuilding(site);
            estate.changeSiteLifecycle(new FacilitiesCommands.ChangeSiteLifecycle(site.id(),
                    gh.edu.clet.sfl.facilities.shared.domain.model.RecordLifecycleStatus.ARCHIVED, null,
                    manager, SourceChannel.WEB));
            assertThat(building.buildingCode()).isEqualTo("BLK-A");

            Site reused = estate.createSite(new FacilitiesCommands.CreateSite("MAIN", "Rebuilt campus", null,
                    manager, SourceChannel.WEB, null));

            assertThat(reused.siteCode()).isEqualTo("MAIN");
        }
    }

    @Nested
    @DisplayName("3. Reject a negative room capacity")
    class CapacityValidation {

        @Test
        void refuses_a_negative_capacity() {
            FacilityFloor floor = createFloor(createBuilding(createSite()));

            assertThatThrownBy(() -> estate.createRoom(new FacilitiesCommands.CreateRoom(floor.id(), "R-1",
                    "Room", SpaceType.OFFICE, -5, null, null, null, null, manager, SourceChannel.WEB, null)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("capacity cannot be negative");
        }
    }

    @Nested
    @DisplayName("4. Register room, device and zone references")
    class References {

        @Test
        void registers_a_device_against_a_space() {
            FacilityRoom room = createRoom(createFloor(createBuilding(createSite())), "HALL-A",
                    SpaceType.EXAMINATION_HALL);

            DeviceReference camera = estate.registerDeviceReference(
                    new FacilitiesCommands.RegisterDeviceReference("MAIN", "CAM-01", "Hall camera",
                            DeviceReferenceType.CCTV_CAMERA, room.id(), "HALL-A", "VMS", "EXT-1", manager,
                            SourceChannel.WEB, null));

            assertThat(camera.roomId()).isEqualTo(room.id());
            assertThat(camera.externalReference()).isEqualTo("EXT-1");
            assertThat(outbox.published("ifimp.device-reference.registered")).isTrue();
        }

        @Test
        void refuses_a_device_pointing_at_a_space_in_another_site() {
            FacilityRoom room = createRoom(createFloor(createBuilding(createSite())), "HALL-A",
                    SpaceType.EXAMINATION_HALL);
            ActorContext crossSite = TestDoubles.actor("admin", Set.of(SflRole.SFL_ADMIN), "*");
            estate.createSite(new FacilitiesCommands.CreateSite("KUMASI", "Kumasi", null, crossSite,
                    SourceChannel.WEB, null));

            assertThatThrownBy(() -> estate.registerDeviceReference(
                    new FacilitiesCommands.RegisterDeviceReference("KUMASI", "CAM-02", "Camera",
                            DeviceReferenceType.CCTV_CAMERA, room.id(), null, null, null, crossSite,
                            SourceChannel.WEB, null)))
                    .isInstanceOf(FacilitiesException.ValidationFailedException.class)
                    .hasMessageContaining("belongs to site MAIN, not KUMASI");
        }

        @Test
        void a_zone_covers_records_from_its_own_site_only() {
            FacilityRoom room = createRoom(createFloor(createBuilding(createSite())), "HALL-A",
                    SpaceType.EXAMINATION_HALL);
            Zone zone = estate.createZone(new FacilitiesCommands.CreateZone("MAIN", "EVAC-1",
                    "Evacuation zone 1", "Assembly point A", null, manager, SourceChannel.WEB, null));

            estate.addZoneMember(new FacilitiesCommands.AddZoneMember(zone.id(), ZoneMemberType.ROOM,
                    room.id(), manager, SourceChannel.WEB));

            assertThat(estate.zoneMembers(zone.id(), manager, SourceChannel.WEB)).hasSize(1);
            assertThat(facilities.findZonesContaining(ZoneMemberType.ROOM, room.id())).hasSize(1);
        }
    }

    @Nested
    @DisplayName("5-7. Assessment with blockers, READY refused, then resolved and ready")
    class ReadinessWorkflow {

        private FacilityRoom hall;
        private ReadinessChecklist checklist;

        @BeforeEach
        void seedTheHall() {
            hall = createRoom(createFloor(createBuilding(createSite())), "HALL-A",
                    SpaceType.EXAMINATION_HALL);
            checklist = readiness.createChecklist(new ReadinessCommands.CreateChecklist("MAIN", "EXAM-HALL",
                    "Examination hall readiness", null, SpaceType.EXAMINATION_HALL, null,
                    List.of(
                            new ReadinessCommands.ChecklistItem("FIRE-EGRESS", "Fire exits latch",
                                    BlockerSeverity.CRITICAL, true, 3, 10),
                            new ReadinessCommands.ChecklistItem("SEATING", "Seating to plan",
                                    BlockerSeverity.MAJOR, true, 2, 20),
                            new ReadinessCommands.ChecklistItem("SIGNAGE", "Signage displayed",
                                    BlockerSeverity.MINOR, false, 1, 30)),
                    supervisor, SourceChannel.WEB, null));
        }

        @Test
        void scenario_5_an_assessment_with_failures_raises_blockers_and_blocks_the_space() {
            ReadinessAssessment assessment = submit(false, true, true);

            assertThat(assessment.outcome()).isEqualTo(LocationReadinessStatus.BLOCKED);
            assertThat(assessment.score()).isEqualTo(50);
            assertThat(assessment.checklistCode()).isEqualTo("EXAM-HALL");
            assertThat(assessment.checklistVersion()).isEqualTo(2);

            List<ReadinessBlocker> open = readinessStore.findOpenBlockers(hall.id());
            assertThat(open).hasSize(1);
            assertThat(open.get(0).severity()).isEqualTo(BlockerSeverity.CRITICAL);
            assertThat(facilities.findRoom(hall.id()).orElseThrow().readinessStatus())
                    .isEqualTo(LocationReadinessStatus.BLOCKED);
        }

        @Test
        void an_unanswered_item_counts_as_failed() {
            ReadinessAssessment assessment = readiness.submitAssessment(
                    new ReadinessCommands.SubmitAssessment(hall.id(), checklist.id(), List.of(), null,
                            supervisor, SourceChannel.MOBILE, null));

            assertThat(assessment.score()).isZero();
            assertThat(assessment.outcome()).isEqualTo(LocationReadinessStatus.BLOCKED);
            assertThat(assessment.items()).allMatch(item -> "Not answered".equals(item.comment()));
        }

        @Test
        void scenario_6_ready_is_refused_while_a_critical_blocker_is_open() {
            submit(false, true, true);

            assertThatThrownBy(() -> readiness.setReadinessDirectly(
                    new FacilitiesCommands.UpdateRoomReadiness(hall.id(), LocationReadinessStatus.READY,
                            "Looks fine to me", supervisor, SourceChannel.WEB)))
                    .isInstanceOf(FacilitiesException.ReadinessBlockedException.class)
                    .hasMessageContaining("1 critical blocker(s) remain open");
        }

        @Test
        void scenario_7_resolving_the_blocker_returns_the_space_to_ready() {
            submit(false, true, true);
            ReadinessBlocker blocker = readinessStore.findOpenBlockers(hall.id()).get(0);

            readiness.resolveBlocker(new ReadinessCommands.ResolveBlocker(blocker.id(),
                    "Latch replaced and retested", supervisor, SourceChannel.WEB));

            assertThat(readinessStore.findOpenBlockers(hall.id())).isEmpty();
            assertThat(facilities.findRoom(hall.id()).orElseThrow().readinessStatus())
                    .isEqualTo(LocationReadinessStatus.READY);
            assertThat(audit.recorded(AuditAction.READINESS_BLOCKER_RESOLVED)).isTrue();
        }

        @Test
        void a_minor_failure_degrades_rather_than_blocks() {
            ReadinessAssessment assessment = submit(true, true, false);

            assertThat(assessment.outcome()).isEqualTo(LocationReadinessStatus.DEGRADED);
            assertThat(assessment.score()).isEqualTo(83);
        }

        @Test
        void a_clean_assessment_is_ready() {
            assertThat(submit(true, true, true).outcome()).isEqualTo(LocationReadinessStatus.READY);
        }

        @Test
        void a_second_assessment_supersedes_the_first_ones_blockers() {
            submit(false, true, true);
            assertThat(readinessStore.findOpenBlockers(hall.id())).hasSize(1);

            submit(true, true, true);

            assertThat(readinessStore.findOpenBlockers(hall.id())).isEmpty();
            assertThat(facilities.findRoom(hall.id()).orElseThrow().readinessStatus())
                    .isEqualTo(LocationReadinessStatus.READY);
        }

        @Test
        void an_unknown_item_code_is_refused_rather_than_ignored() {
            assertThatThrownBy(() -> readiness.submitAssessment(new ReadinessCommands.SubmitAssessment(
                    hall.id(), checklist.id(),
                    List.of(new ReadinessCommands.AssessmentAnswer("NOT-A-THING", true, null)), null,
                    supervisor, SourceChannel.WEB, null)))
                    .isInstanceOf(FacilitiesException.ValidationFailedException.class)
                    .hasMessageContaining("Unknown checklist item code(s)");
        }

        private ReadinessAssessment submit(boolean fire, boolean seating, boolean signage) {
            return readiness.submitAssessment(new ReadinessCommands.SubmitAssessment(hall.id(),
                    checklist.id(),
                    List.of(new ReadinessCommands.AssessmentAnswer("FIRE-EGRESS", fire, null),
                            new ReadinessCommands.AssessmentAnswer("SEATING", seating, null),
                            new ReadinessCommands.AssessmentAnswer("SIGNAGE", signage, null)),
                    null, supervisor, SourceChannel.MOBILE, "key-" + fire + seating + signage));
        }
    }

    @Nested
    @DisplayName("An asset failure blocks the space it serves")
    class AssetDrivenReadiness {

        @Test
        void a_failed_critical_asset_blocks_its_space_and_its_recovery_clears_it() {
            FacilityRoom hall = createRoom(createFloor(createBuilding(createSite())), "HALL-A",
                    SpaceType.EXAMINATION_HALL);
            FacilityAsset generator = assets.register(new FacilitiesCommands.RegisterAsset("MAIN", "GEN-01",
                    "Standby generator", AssetCategory.GENERATOR, AssetCriticality.CRITICAL, hall.id(), null,
                    null, null, null, null, null, null, null, null, null, manager, SourceChannel.WEB, null));

            FacilityAsset failed = assets.changeStatus(new FacilitiesCommands.ChangeAssetStatus(
                    generator.id(), AssetOperationalStatus.OUT_OF_SERVICE, "Will not start", null, manager,
                    SourceChannel.WEB));

            assertThat(readinessStore.findOpenBlockers(hall.id())).hasSize(1);
            assertThat(readinessStore.findOpenBlockers(hall.id()).get(0).severity())
                    .isEqualTo(BlockerSeverity.CRITICAL);
            assertThat(facilities.findRoom(hall.id()).orElseThrow().readinessStatus())
                    .isEqualTo(LocationReadinessStatus.BLOCKED);

            assets.changeStatus(new FacilitiesCommands.ChangeAssetStatus(failed.id(),
                    AssetOperationalStatus.OPERATIONAL, "Repaired", null, manager, SourceChannel.WEB));

            assertThat(readinessStore.findOpenBlockers(hall.id())).isEmpty();
        }

        @Test
        void a_low_criticality_failure_is_advisory_and_does_not_block() {
            FacilityRoom hall = createRoom(createFloor(createBuilding(createSite())), "HALL-A",
                    SpaceType.EXAMINATION_HALL);
            FacilityAsset sign = assets.register(new FacilitiesCommands.RegisterAsset("MAIN", "SIGN-01",
                    "Noticeboard light", AssetCategory.AUDIO_VISUAL, AssetCriticality.LOW, hall.id(), null,
                    null, null, null, null, null, null, null, null, null, manager, SourceChannel.WEB, null));

            assets.changeStatus(new FacilitiesCommands.ChangeAssetStatus(sign.id(),
                    AssetOperationalStatus.OUT_OF_SERVICE, "Bulb gone", null, manager, SourceChannel.WEB));

            assertThat(readinessStore.findOpenBlockers(hall.id()).get(0).severity())
                    .isEqualTo(BlockerSeverity.ADVISORY);
            assertThat(facilities.findRoom(hall.id()).orElseThrow().readinessStatus())
                    .isNotEqualTo(LocationReadinessStatus.BLOCKED);
        }

        @Test
        void a_repeated_status_change_does_not_duplicate_the_blocker() {
            FacilityRoom hall = createRoom(createFloor(createBuilding(createSite())), "HALL-A",
                    SpaceType.EXAMINATION_HALL);
            FacilityAsset generator = assets.register(new FacilitiesCommands.RegisterAsset("MAIN", "GEN-01",
                    "Generator", AssetCategory.GENERATOR, AssetCriticality.CRITICAL, hall.id(), null, null,
                    null, null, null, null, null, null, null, null, manager, SourceChannel.WEB, null));

            FacilityAsset failed = assets.changeStatus(new FacilitiesCommands.ChangeAssetStatus(
                    generator.id(), AssetOperationalStatus.OUT_OF_SERVICE, "Failed", null, manager,
                    SourceChannel.WEB));
            assets.update(new FacilitiesCommands.UpdateAsset(failed.id(), "Generator (renamed)", null, null,
                    null, null, null, null, null, null, null, manager, SourceChannel.WEB));

            assertThat(readinessStore.findOpenBlockers(hall.id())).hasSize(1);
        }
    }

    @Nested
    @DisplayName("8. Dashboard summary")
    class Dashboard {

        @Test
        void reports_readiness_blockers_assets_and_maintenance_for_the_site() {
            FacilityFloor floor = createFloor(createBuilding(createSite()));
            FacilityRoom hall = createRoom(floor, "HALL-A", SpaceType.EXAMINATION_HALL);
            createRoom(floor, "MEET-1", SpaceType.MEETING_ROOM);
            readiness.raiseBlocker(new ReadinessCommands.RaiseBlocker(hall.id(), BlockerSeverity.CRITICAL,
                    "Roof leak over the hall", supervisor, SourceChannel.WEB));
            maintenance.withOpenWork(3, 2).withOpenWorkAt("MEET-1");

            FacilityDashboard view = dashboard.dashboard("MAIN", manager, SourceChannel.WEB);

            assertThat(view.siteCode()).isEqualTo("MAIN");
            assertThat(view.operatingMode()).isEqualTo(OperatingMode.ROUTINE);
            assertThat(view.spaces().total()).isEqualTo(2);
            assertThat(view.spaces().blocked()).isEqualTo(1);
            assertThat(view.blockers().critical()).isEqualTo(1);
            assertThat(view.maintenance().openFaults()).isEqualTo(3);
            assertThat(view.maintenance().openWorkOrders()).isEqualTo(2);
            assertThat(view.examinationRisks()).hasSize(1);
            assertThat(view.examinationRisks().get(0).code()).isEqualTo("HALL-A");
            assertThat(view.unavailableSpaces()).hasSize(1);
        }

        @Test
        void warns_when_readiness_is_stale() {
            createRoom(createFloor(createBuilding(createSite())), "HALL-A", SpaceType.EXAMINATION_HALL);

            FacilityDashboard view = dashboard.dashboard("MAIN", manager, SourceChannel.WEB);

            // A space that has never been assessed is the most stale thing on the estate.
            assertThat(view.stale()).isTrue();
            assertThat(view.staleWarning()).contains("1 space(s) have readiness older than");
            assertThat(view.staleReadiness()).hasSize(1);
            assertThat(view.staleReadiness().get(0).reason()).isEqualTo("Never assessed");
        }

        @Test
        void reports_examination_mode_when_the_site_declares_it() {
            Site site = createSite();
            ActorContext centreManager = TestDoubles.actor("centre", Set.of(SflRole.CENTRE_MANAGER), "MAIN");
            estate.changeOperatingMode(new FacilitiesCommands.ChangeOperatingMode(site.id(),
                    OperatingMode.EXAMINATION, "Bar finals", centreManager, SourceChannel.WEB));

            assertThat(dashboard.dashboard("MAIN", manager, SourceChannel.WEB).operatingMode())
                    .isEqualTo(OperatingMode.EXAMINATION);
        }
    }

    @Nested
    @DisplayName("9. Site-scoped access is enforced")
    class SiteScope {

        @Test
        void refuses_a_read_of_a_site_outside_the_actors_scope() {
            Site site = createSite();

            assertThatThrownBy(() -> estate.site(site.id(), otherSiteManager, SourceChannel.WEB))
                    .isInstanceOf(FacilitiesException.UnauthorizedScopeException.class)
                    .hasMessage("You are not authorised to access this site or record.");
        }

        @Test
        void refuses_a_write_to_a_site_outside_the_actors_scope() {
            assertThatThrownBy(() -> estate.createSite(new FacilitiesCommands.CreateSite("KUMASI", "Kumasi",
                    null, manager, SourceChannel.WEB, null)))
                    .isInstanceOf(FacilitiesException.UnauthorizedScopeException.class);
        }

        @Test
        void refuses_an_actor_whose_role_grants_no_permission() {
            Site site = createSite();
            ActorContext driver = TestDoubles.actor("driver", Set.of(SflRole.FLEET_DRIVER), "MAIN");

            assertThatThrownBy(() -> estate.site(site.id(), driver, SourceChannel.WEB))
                    .isInstanceOf(FacilitiesException.UnauthorizedScopeException.class);
        }

        @Test
        void every_denial_is_audited() {
            Site site = createSite();
            audit.clear();

            assertThatThrownBy(() -> estate.site(site.id(), otherSiteManager, SourceChannel.WEB))
                    .isInstanceOf(FacilitiesException.UnauthorizedScopeException.class);

            assertThat(audit.countOf(AuditAction.AUTHORIZATION_DENIED)).isEqualTo(1);
            assertThat(audit.events().get(0).actorId()).isEqualTo("kumasi.manager");
            assertThat(audit.events().get(0).afterValue()).contains("site scopes do not include MAIN");
        }

        @Test
        void a_list_filters_to_the_actors_sites_rather_than_refusing() {
            ActorContext admin = TestDoubles.actor("admin", Set.of(SflRole.SFL_ADMIN), "*");
            estate.createSite(new FacilitiesCommands.CreateSite("MAIN", "Main", null, admin,
                    SourceChannel.WEB, null));
            estate.createSite(new FacilitiesCommands.CreateSite("KUMASI", "Kumasi", null, admin,
                    SourceChannel.WEB, null));

            assertThat(estate.sites(manager, SourceChannel.WEB)).extracting(Site::siteCode)
                    .containsExactly("MAIN");
            assertThat(estate.sites(admin, SourceChannel.WEB)).hasSize(2);
        }

        @Test
        void an_actor_with_no_site_scope_at_all_is_told_so_specifically() {
            ActorContext unscoped = TestDoubles.actor("new.starter", Set.of(SflRole.FACILITIES_MANAGER));

            assertThatThrownBy(() -> estate.rooms(null, unscoped, SourceChannel.WEB))
                    .isInstanceOf(FacilitiesException.NoScopeException.class)
                    .hasMessage("No site scope is assigned to your user profile.");
        }
    }

    @Nested
    @DisplayName("10. Audit and outbox events for every state change")
    class AuditAndEvents {

        @Test
        void every_creation_writes_an_audit_record_and_an_event() {
            createRoom(createFloor(createBuilding(createSite())), "HALL-A", SpaceType.EXAMINATION_HALL);

            assertThat(audit.actions()).containsExactly(AuditAction.SITE_CREATED, AuditAction.BUILDING_CREATED,
                    AuditAction.FLOOR_CREATED, AuditAction.ROOM_CREATED);
            assertThat(outbox.eventTypes()).containsExactly("ifimp.site.created", "ifimp.building.created",
                    "ifimp.floor.created", "ifimp.room.created");
        }

        @Test
        void the_audit_chain_stays_intact_across_a_whole_workflow() {
            FacilityRoom hall = createRoom(createFloor(createBuilding(createSite())), "HALL-A",
                    SpaceType.EXAMINATION_HALL);
            readiness.raiseBlocker(new ReadinessCommands.RaiseBlocker(hall.id(), BlockerSeverity.CRITICAL,
                    "Leak", supervisor, SourceChannel.WEB));
            ReadinessBlocker blocker = readinessStore.findOpenBlockers(hall.id()).get(0);
            readiness.resolveBlocker(new ReadinessCommands.ResolveBlocker(blocker.id(), "Fixed", supervisor,
                    SourceChannel.WEB));

            assertThat(audit.verifyChain().intact()).isTrue();
            assertThat(audit.verifyChain().recordsVerified()).isEqualTo(audit.events().size());
        }

        @Test
        void an_operating_mode_change_is_audited_as_its_own_action() {
            Site site = createSite();
            ActorContext centreManager = TestDoubles.actor("centre", Set.of(SflRole.CENTRE_MANAGER), "MAIN");

            estate.changeOperatingMode(new FacilitiesCommands.ChangeOperatingMode(site.id(),
                    OperatingMode.EXAMINATION, "Bar finals", centreManager, SourceChannel.WEB));

            assertThat(audit.recorded(AuditAction.SITE_OPERATING_MODE_CHANGED)).isTrue();
            assertThat(outbox.published("ifimp.site.operating-mode-changed")).isTrue();
        }

        @Test
        void a_facilities_manager_may_not_declare_examination_mode() {
            Site site = createSite();

            assertThatThrownBy(() -> estate.changeOperatingMode(new FacilitiesCommands.ChangeOperatingMode(
                    site.id(), OperatingMode.EXAMINATION, "Bar finals", manager, SourceChannel.WEB)))
                    .isInstanceOf(FacilitiesException.UnauthorizedScopeException.class);
        }
    }

    @Nested
    @DisplayName("Idempotency on state-creating commands")
    class Idempotency {

        @Test
        void a_replayed_key_with_the_same_payload_returns_the_original_record() {
            Site first = estate.createSite(new FacilitiesCommands.CreateSite("MAIN", "Main Campus", null,
                    manager, SourceChannel.WEB, "key-1"));

            Site replay = estate.createSite(new FacilitiesCommands.CreateSite("MAIN", "Main Campus", null,
                    manager, SourceChannel.WEB, "key-1"));

            assertThat(replay.id()).isEqualTo(first.id());
            assertThat(facilities.findSites()).hasSize(1);
            assertThat(audit.countOf(AuditAction.SITE_CREATED)).isEqualTo(1);
        }

        @Test
        void a_replayed_key_with_a_different_payload_is_a_conflict() {
            estate.createSite(new FacilitiesCommands.CreateSite("MAIN", "Main Campus", null, manager,
                    SourceChannel.WEB, "key-1"));

            assertThatThrownBy(() -> estate.createSite(new FacilitiesCommands.CreateSite("MAIN",
                    "A different name", null, manager, SourceChannel.WEB, "key-1")))
                    .isInstanceOf(FacilitiesException.IdempotencyKeyConflictException.class);
        }

        @Test
        void no_key_means_no_dedup() {
            estate.createSite(new FacilitiesCommands.CreateSite("MAIN", "Main Campus", null, manager,
                    SourceChannel.WEB, null));

            assertThatThrownBy(() -> estate.createSite(new FacilitiesCommands.CreateSite("MAIN", "Again",
                    null, manager, SourceChannel.WEB, null)))
                    .isInstanceOf(FacilitiesException.DuplicateIdentifierException.class);
        }
    }

    @Nested
    @DisplayName("The examination readiness lock")
    class ReadinessLock {

        @Test
        void a_locked_space_refuses_attribute_changes_until_it_is_released() {
            FacilityRoom hall = createRoom(createFloor(createBuilding(createSite())), "HALL-A",
                    SpaceType.EXAMINATION_HALL);

            readiness.lockReadiness(new ReadinessCommands.LockReadiness(hall.id(), "Bar finals", supervisor,
                    SourceChannel.WEB));

            assertThatThrownBy(() -> estate.updateRoom(new FacilitiesCommands.UpdateRoom(hall.id(),
                    "Renamed mid-examination", null, null, null, null, null, null, null, manager,
                    SourceChannel.WEB)))
                    .isInstanceOf(FacilitiesException.ReadinessLockedException.class);

            readiness.unlockReadiness(new ReadinessCommands.UnlockReadiness(hall.id(), "Examination closed",
                    supervisor, SourceChannel.WEB));

            assertThat(estate.updateRoom(new FacilitiesCommands.UpdateRoom(hall.id(), "Renamed", null, null,
                    null, null, null, null, null, manager, SourceChannel.WEB)).name()).isEqualTo("Renamed");
        }

        @Test
        void a_manager_without_the_override_permission_cannot_lock() {
            FacilityRoom hall = createRoom(createFloor(createBuilding(createSite())), "HALL-A",
                    SpaceType.EXAMINATION_HALL);

            assertThatThrownBy(() -> readiness.lockReadiness(new ReadinessCommands.LockReadiness(hall.id(),
                    "Bar finals", manager, SourceChannel.WEB)))
                    .isInstanceOf(FacilitiesException.UnauthorizedScopeException.class);
        }
    }

    // =========================================================================================

    private Site createSite() {
        return estate.createSite(new FacilitiesCommands.CreateSite("MAIN", "Main Campus", "Head office",
                manager, SourceChannel.WEB, null));
    }

    private Building createBuilding(Site site) {
        return estate.createBuilding(new FacilitiesCommands.CreateBuilding(site.id(), "BLK-A", "Block A",
                null, manager, SourceChannel.WEB, null));
    }

    private FacilityFloor createFloor(Building building) {
        return estate.createFloor(new FacilitiesCommands.CreateFloor(building.id(), "GF", "Ground floor", 0,
                manager, SourceChannel.WEB, null));
    }

    private FacilityRoom createRoom(FacilityFloor floor, String code, SpaceType type) {
        return estate.createRoom(new FacilitiesCommands.CreateRoom(floor.id(), code, code + " space", type,
                50, null, null, null, null, manager, SourceChannel.WEB, null));
    }
}
