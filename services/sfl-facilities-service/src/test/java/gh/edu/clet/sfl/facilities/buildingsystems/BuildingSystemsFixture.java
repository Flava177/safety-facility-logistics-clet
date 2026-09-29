package gh.edu.clet.sfl.facilities.buildingsystems;

import gh.edu.clet.sfl.facilities.buildingsystems.application.AvampAssetProjectionService;
import gh.edu.clet.sfl.facilities.buildingsystems.application.BmsDeviceService;
import gh.edu.clet.sfl.facilities.buildingsystems.application.BuildingAlertService;
import gh.edu.clet.sfl.facilities.buildingsystems.application.BuildingDeviceDirectoryService;
import gh.edu.clet.sfl.facilities.buildingsystems.application.BuildingHealthService;
import gh.edu.clet.sfl.facilities.buildingsystems.application.BuildingSystemsConfiguration;
import gh.edu.clet.sfl.facilities.buildingsystems.application.S152LocationResolver;
import gh.edu.clet.sfl.facilities.buildingsystems.application.TelemetryIngestionService;
import gh.edu.clet.sfl.facilities.buildingsystems.application.ThresholdRuleService;
import gh.edu.clet.sfl.facilities.buildingsystems.application.contract.BuildingTelemetryObserver;
import gh.edu.clet.sfl.facilities.buildingsystems.application.ports.BmsTelemetryTranslatorPort;
import gh.edu.clet.sfl.facilities.buildingsystems.application.ports.BuildingWorkOrderPort;
import gh.edu.clet.sfl.facilities.buildingsystems.infrastructure.integration.BacnetBridgeTelemetryAdapter;
import gh.edu.clet.sfl.facilities.buildingsystems.infrastructure.integration.MaintenanceWorkOrderAdapter;
import gh.edu.clet.sfl.facilities.buildingsystems.infrastructure.integration.SimulatedBmsTelemetryAdapter;
import gh.edu.clet.sfl.facilities.shared.application.FacilitiesAuthorization;
import gh.edu.clet.sfl.facilities.shared.application.port.SecurityEventForwarderPort;
import gh.edu.clet.sfl.facilities.shared.application.vendor.VendorIntegrationCatalogPort;
import gh.edu.clet.sfl.facilities.shared.application.vendor.VendorIntegrationRegistry;
import gh.edu.clet.sfl.facilities.shared.application.vendor.VendorMessageVerifier;
import gh.edu.clet.sfl.facilities.shared.application.vendor.VendorRejectionRecorder;
import gh.edu.clet.sfl.facilities.shared.domain.security.SecurityEvent;
import gh.edu.clet.sfl.facilities.support.IfimpTestHarness;
import gh.edu.clet.sfl.facilities.support.InMemoryBuildingSystemsRepository;
import gh.edu.clet.sfl.facilities.support.TestDoubles;
import java.util.ArrayList;
import java.util.List;

/**
 * Wires the whole S156 module over an {@link IfimpTestHarness} - real S152 and S153, an in-memory S156
 * repository, and the real {@link VendorMessageVerifier} over an in-memory inbox/source pair, so a
 * scenario test exercises the actual pipeline rather than a stand-in for it.
 */
public final class BuildingSystemsFixture {

    public final IfimpTestHarness harness;
    public final InMemoryBuildingSystemsRepository repository = new InMemoryBuildingSystemsRepository();
    public final TestDoubles.InMemoryConfiguration configuration;
    public final BuildingSystemsConfiguration bmsConfiguration;
    public final S152LocationResolver locations;
    public final InMemoryVendorSupport.InMemoryVendorInbox inbox = new InMemoryVendorSupport.InMemoryVendorInbox();
    public final List<SecurityEvent> siemEvents = new ArrayList<>();
    public final VendorMessageVerifier verifier;
    public final BuildingWorkOrderPort workOrders;
    public final BuildingAlertService alertService;
    public final TelemetryIngestionService ingestion;
    public final BmsDeviceService devices;
    public final ThresholdRuleService rules;
    public final BuildingHealthService health;
    public final AvampAssetProjectionService avampProjection;
    public final BuildingDeviceDirectoryService directory;
    public final List<BuildingTelemetryObserver> observers = new ArrayList<>();

    public BuildingSystemsFixture(IfimpTestHarness harness) {
        this.harness = harness;
        this.configuration = harness.configuration;
        this.bmsConfiguration = new BuildingSystemsConfiguration(configuration);
        this.locations = new S152LocationResolver(harness.facilities);
        SecurityEventForwarderPort siem = event -> {
            siemEvents.add(event);
            return new SecurityEventForwarderPort.ForwardResult("TEST", false);
        };
        VendorRejectionRecorder rejections = new VendorRejectionRecorder(inbox, harness.audit, siem, harness.outbox,
                harness.clock);
        this.verifier = new VendorMessageVerifier(InMemoryVendorSupport.sources(), inbox, rejections, harness.clock);
        this.workOrders = new MaintenanceWorkOrderAdapter(harness.intake);
        this.alertService = new BuildingAlertService(repository, bmsConfiguration, workOrders, harness.authorization,
                harness.audit, harness.outbox, siem, harness.clock);
        List<BmsTelemetryTranslatorPort> translators = new ArrayList<>(
                List.of(new SimulatedBmsTelemetryAdapter(), new BacnetBridgeTelemetryAdapter()));
        this.ingestion = new TelemetryIngestionService(repository, bmsConfiguration, translators, verifier, locations,
                alertService, observers, harness.authorization, harness.audit, harness.outbox, harness.clock);
        this.devices = new BmsDeviceService(repository, locations, bmsConfiguration, harness.authorization,
                harness.audit, harness.outbox, harness.clock);
        this.rules = new ThresholdRuleService(repository, bmsConfiguration, harness.authorization, harness.audit,
                harness.outbox, harness.clock);
        VendorIntegrationCatalogPort catalog = () -> List.of(new VendorIntegrationCatalogPort.VendorIntegration(
                "bms-iot", "S156", "Building Management System / IoT platform", "Buy and Integrate",
                "SimulatedBmsTelemetryAdapter", null));
        this.health = new BuildingHealthService(repository, bmsConfiguration,
                new VendorIntegrationRegistry(catalog, harness.authorization), harness.authorization, harness.clock);
        this.avampProjection = new AvampAssetProjectionService(repository, harness.audit, harness.clock);
        this.directory = new BuildingDeviceDirectoryService(repository);
    }

    /** A fresh {@link TelemetryIngestionService} with one extra translator - the S156-05 replaceability test. */
    public TelemetryIngestionService ingestionWith(BmsTelemetryTranslatorPort extraTranslator) {
        List<BmsTelemetryTranslatorPort> translators = List.of(new SimulatedBmsTelemetryAdapter(),
                new BacnetBridgeTelemetryAdapter(), extraTranslator);
        return new TelemetryIngestionService(repository, bmsConfiguration, translators, verifier, locations,
                alertService, observers, harness.authorization, harness.audit, harness.outbox, harness.clock);
    }
}
