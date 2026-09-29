package gh.edu.clet.sfl.facilities.spaceplanning.support;

import gh.edu.clet.sfl.facilities.masterdata.application.SpaceAllocationService;
import gh.edu.clet.sfl.facilities.spaceplanning.application.OccupancyStandardService;
import gh.edu.clet.sfl.facilities.spaceplanning.application.ScenarioHandoverService;
import gh.edu.clet.sfl.facilities.spaceplanning.application.SpaceChangeRequestService;
import gh.edu.clet.sfl.facilities.spaceplanning.application.SpacePlanningConfiguration;
import gh.edu.clet.sfl.facilities.spaceplanning.application.SpacePlanningDashboardService;
import gh.edu.clet.sfl.facilities.spaceplanning.application.SpaceScenarioService;
import gh.edu.clet.sfl.facilities.spaceplanning.application.UtilisationReconciliationService;
import gh.edu.clet.sfl.facilities.support.IfimpTestHarness;
import gh.edu.clet.sfl.facilities.support.InMemorySpaceAllocationRepository;
import gh.edu.clet.sfl.facilities.support.InMemorySpacePlanningRepository;

/**
 * The S158 module wired over {@link IfimpTestHarness}'s real S152 services and in-memory adapters, plus
 * S158's own repositories and its two outbound ports as controllable fakes ({@link FakeConstructionHandoffPort}
 * for S176, {@link FakeBookingUtilisationPort} for S159).
 *
 * <p>Composed rather than extending the harness, which is {@code final} by the shared-support contract.
 * One instance per test, built fresh in {@code @BeforeEach}, matching every other Phase 2 module's pattern.
 */
public final class SpacePlanningTestFixture {

    public final IfimpTestHarness harness;
    public final InMemorySpaceAllocationRepository allocationRepository = new InMemorySpaceAllocationRepository();
    public final InMemorySpacePlanningRepository repository = new InMemorySpacePlanningRepository();
    public final FakeConstructionHandoffPort construction = new FakeConstructionHandoffPort();
    public final FakeBookingUtilisationPort bookings = new FakeBookingUtilisationPort();

    public final SpaceAllocationService register;
    public final OccupancyStandardService standards;
    public final SpaceScenarioService scenarios;
    public final ScenarioHandoverService handover;
    public final UtilisationReconciliationService utilisation;
    public final SpaceChangeRequestService requests;
    public final SpacePlanningDashboardService dashboard;

    public SpacePlanningTestFixture(IfimpTestHarness harness) {
        this.harness = harness;
        register = new SpaceAllocationService(allocationRepository, harness.facilities, harness.authorization,
                harness.audit, harness.outbox, harness.clock);
        standards = new OccupancyStandardService(repository, harness.facilities, harness.authorization, harness.audit,
                harness.outbox, harness.clock);
        scenarios = new SpaceScenarioService(repository, harness.facilities, register, standards, construction,
                harness.authorization, harness.audit, harness.outbox, harness.clock);
        handover = new ScenarioHandoverService(repository, scenarios);
        SpacePlanningConfiguration configuration = new SpacePlanningConfiguration(harness.configuration);
        utilisation = new UtilisationReconciliationService(repository, harness.facilities, register, bookings,
                configuration, harness.authorization, harness.audit, harness.outbox, harness.clock);
        requests = new SpaceChangeRequestService(repository, harness.facilities, scenarios, construction,
                harness.authorization, harness.audit, harness.outbox, harness.clock);
        dashboard = new SpacePlanningDashboardService(repository, harness.facilities, register, standards, scenarios,
                harness.authorization);
    }
}
