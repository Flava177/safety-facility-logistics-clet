package gh.edu.clet.sfl.facilities.support;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.facilities.booking.application.BookingApplicationService;
import gh.edu.clet.sfl.facilities.booking.application.BookingConfiguration;
import gh.edu.clet.sfl.facilities.booking.application.BookingUtilisationReader;
import gh.edu.clet.sfl.facilities.booking.application.ports.BookingLifecycleObserver;
import gh.edu.clet.sfl.facilities.maintenance.application.AutomatedWorkOrderIntake;
import gh.edu.clet.sfl.facilities.maintenance.application.FacilityFaultService;
import gh.edu.clet.sfl.facilities.maintenance.application.MaintenanceConfiguration;
import gh.edu.clet.sfl.facilities.maintenance.application.WorkOrderApplicationService;
import gh.edu.clet.sfl.facilities.masterdata.application.FacilitiesCommands;
import gh.edu.clet.sfl.facilities.masterdata.application.FacilitiesMasterDataService;
import gh.edu.clet.sfl.facilities.masterdata.application.FacilityAssetService;
import gh.edu.clet.sfl.facilities.masterdata.domain.Building;
import gh.edu.clet.sfl.facilities.masterdata.domain.FacilityFloor;
import gh.edu.clet.sfl.facilities.masterdata.domain.FacilityRoom;
import gh.edu.clet.sfl.facilities.masterdata.domain.Site;
import gh.edu.clet.sfl.facilities.masterdata.domain.SpaceType;
import gh.edu.clet.sfl.facilities.readiness.application.ReadinessApplicationService;
import gh.edu.clet.sfl.facilities.shared.application.FacilitiesAuthorization;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The Phase 2 IFIMP test harness: the real S152, S153 and S159 application services over in-memory
 * adapters, with a seeded estate, for every Phase 2 module's acceptance tests to build on.
 *
 * <p>Real services, not mocks, because most Phase 2 acceptance criteria are statements about what
 * happens in S152 or S153 - "a work order is raised in S153 carrying the telemetry evidence reference",
 * "S152's current-state register is unaffected" - and a mock of S153 would only prove the call was made.
 *
 * <p>Seeded: site {@code MAIN} (building {@code LAW}, floor {@code GF}, rooms {@code HALL-A} examination
 * hall capacity 200, {@code MEET-1} meeting room capacity 12, {@code OFF-101} office capacity 4) and site
 * {@code KSI} (building {@code KSB}, floor {@code G}, room {@code KSI-HALL}) for cross-site refusal tests.
 */
public final class IfimpTestHarness {

    public static final Instant NOW = Instant.parse("2026-09-28T08:00:00Z");

    public final MutableClock clock = new MutableClock(NOW);
    public final InMemoryFacilitiesRepository facilities = new InMemoryFacilitiesRepository();
    public final InMemoryMaintenanceRepository maintenance = new InMemoryMaintenanceRepository();
    public final InMemoryReadinessRepository readinessStore = new InMemoryReadinessRepository();
    public final InMemoryBookingRepository bookingStore = new InMemoryBookingRepository();
    public final RecordingAuditPort audit = new RecordingAuditPort(NOW);
    public final TestDoubles.RecordingOutbox outbox = new TestDoubles.RecordingOutbox();
    public final TestDoubles.InMemoryConfiguration configuration = new TestDoubles.InMemoryConfiguration();
    public final TestDoubles.InMemoryIdempotency idempotency = new TestDoubles.InMemoryIdempotency();
    public final FacilitiesAuthorization authorization = new FacilitiesAuthorization(audit);
    /** Add observers before calling {@link #bookings()} for the first time. */
    public final List<BookingLifecycleObserver> bookingObservers = new ArrayList<>();

    public final FacilitiesMasterDataService estate;
    public final ReadinessApplicationService readiness;
    public final FacilityAssetService assets;
    public final FacilityFaultService faults;
    public final WorkOrderApplicationService workOrders;
    public final AutomatedWorkOrderIntake intake;
    public final BookingUtilisationReader utilisation;
    private BookingApplicationService bookings;

    /** Everyone scoped to MAIN unless the name says otherwise. */
    public final ActorContext director = TestDoubles.actor("director", Set.of(SflRole.FACILITIES_DIRECTOR), "MAIN");
    public final ActorContext manager = TestDoubles.actor("manager", Set.of(SflRole.FACILITIES_MANAGER), "MAIN");
    public final ActorContext supervisor = TestDoubles.actor("supervisor",
            Set.of(SflRole.IFIMP_MAINTENANCE_SUPERVISOR), "MAIN");
    public final ActorContext technician = TestDoubles.actor("technician", Set.of(SflRole.IFIMP_TECHNICIAN), "MAIN");
    public final ActorContext vendorTechnician = TestDoubles.actor("vendor.tech", Set.of(SflRole.VENDOR_TECHNICIAN),
            "MAIN");
    public final ActorContext requester = TestDoubles.actor("requester", Set.of(SflRole.IFIMP_REQUESTER), "MAIN");
    public final ActorContext engineer = TestDoubles.actor("engineer", Set.of(SflRole.FACILITIES_ENGINEER), "MAIN");
    public final ActorContext energyOfficer = TestDoubles.actor("energy.officer",
            Set.of(SflRole.ENERGY_SUSTAINABILITY_OFFICER), "MAIN");
    public final ActorContext spacePlanner = TestDoubles.actor("space.planner", Set.of(SflRole.SPACE_PLANNING_OFFICER),
            "MAIN");
    public final ActorContext projectManager = TestDoubles.actor("project.manager",
            Set.of(SflRole.CONSTRUCTION_PROJECT_MANAGER), "MAIN");
    public final ActorContext eventCoordinator = TestDoubles.actor("event.coordinator",
            Set.of(SflRole.EVENT_LOGISTICS_COORDINATOR), "MAIN");
    public final ActorContext hseManager = TestDoubles.actor("hse.manager", Set.of(SflRole.HSE_MANAGER), "MAIN");
    public final ActorContext auditor = TestDoubles.actor("auditor", Set.of(SflRole.COMPLIANCE_OFFICER), "MAIN");
    public final ActorContext integration = TestDoubles.actor("integration.bms", Set.of(SflRole.SERVICE_INTEGRATION),
            "*");
    public final ActorContext system = TestDoubles.actor("system", Set.of(SflRole.SFL_ADMIN), "*");
    /** A facilities manager for the other site - for cross-site refusal tests. */
    public final ActorContext kumasiManager = TestDoubles.actor("ksi.manager", Set.of(SflRole.FACILITIES_MANAGER),
            "KSI");

    public final Site main;
    public final Building lawBlock;
    public final FacilityFloor groundFloor;
    public final FacilityRoom hall;
    public final FacilityRoom meetingRoom;
    public final FacilityRoom office;
    public final Site kumasi;
    public final FacilityRoom kumasiHall;

    public IfimpTestHarness() {
        MaintenanceConfiguration maintenanceConfiguration = new MaintenanceConfiguration(configuration);
        estate = new FacilitiesMasterDataService(facilities, outbox, audit, idempotency, authorization, clock);
        readiness = new ReadinessApplicationService(readinessStore, facilities, outbox, audit, idempotency,
                authorization, clock);
        assets = new FacilityAssetService(facilities, outbox, audit, idempotency, authorization, readiness, clock);
        faults = new FacilityFaultService(maintenance, facilities, readiness, maintenanceConfiguration,
                authorization, audit, idempotency, outbox, clock);
        workOrders = new WorkOrderApplicationService(maintenance, facilities, faults, maintenanceConfiguration,
                authorization, audit, idempotency, outbox, clock);
        intake = new AutomatedWorkOrderIntake(faults, workOrders, maintenance);
        utilisation = new BookingUtilisationReader(bookingStore);

        ActorContext seed = TestDoubles.actor("seed", Set.of(SflRole.SFL_ADMIN), "*");
        main = estate.createSite(new FacilitiesCommands.CreateSite("MAIN", "CLET Headquarters", null, seed,
                SourceChannel.WEB, null));
        lawBlock = estate.createBuilding(new FacilitiesCommands.CreateBuilding(main.id(), "LAW", "Law Block", null,
                seed, SourceChannel.WEB, null));
        groundFloor = estate.createFloor(new FacilitiesCommands.CreateFloor(lawBlock.id(), "GF", "Ground floor", 0,
                seed, SourceChannel.WEB, null));
        hall = room(groundFloor, "HALL-A", "Examination Hall A", SpaceType.EXAMINATION_HALL, 200, seed);
        meetingRoom = room(groundFloor, "MEET-1", "Meeting Room 1", SpaceType.MEETING_ROOM, 12, seed);
        office = room(groundFloor, "OFF-101", "Office 101", SpaceType.OFFICE, 4, seed);

        kumasi = estate.createSite(new FacilitiesCommands.CreateSite("KSI", "CLET Kumasi", null, seed,
                SourceChannel.WEB, null));
        Building kumasiBlock = estate.createBuilding(new FacilitiesCommands.CreateBuilding(kumasi.id(), "KSB",
                "Kumasi Block", null, seed, SourceChannel.WEB, null));
        FacilityFloor kumasiFloor = estate.createFloor(new FacilitiesCommands.CreateFloor(kumasiBlock.id(), "G",
                "Ground", 0, seed, SourceChannel.WEB, null));
        kumasiHall = room(kumasiFloor, "KSI-HALL", "Kumasi Hall", SpaceType.LECTURE_HALL, 150, seed);
        audit.clear();
        outbox.clear();
    }

    /** The S159 booking service, built on first use so observers added in a test's setup are wired in. */
    public BookingApplicationService bookings() {
        if (bookings == null) {
            bookings = new BookingApplicationService(bookingStore, facilities, new BookingConfiguration(configuration),
                    authorization, audit, idempotency, outbox, clock, bookingObservers);
        }
        return bookings;
    }

    private FacilityRoom room(FacilityFloor floor, String code, String name, SpaceType type, int capacity,
            ActorContext seed) {
        return estate.createRoom(new FacilitiesCommands.CreateRoom(floor.id(), code, name, type, capacity, null, null,
                type.isBookableByDefault(), type.isExaminationCapableByDefault(), seed, SourceChannel.WEB, null));
    }
}
