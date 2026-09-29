package gh.edu.clet.sfl.facilities.energy.support;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.energy.application.EnergyConfiguration;
import gh.edu.clet.sfl.facilities.energy.application.EnergyHealthService;
import gh.edu.clet.sfl.facilities.energy.application.EnergyMeterService;
import gh.edu.clet.sfl.facilities.energy.application.EnergyReadingService;
import gh.edu.clet.sfl.facilities.energy.application.EnergyVarianceService;
import gh.edu.clet.sfl.facilities.energy.application.SustainabilityKpiService;
import gh.edu.clet.sfl.facilities.shared.application.vendor.VendorInboxPort;
import gh.edu.clet.sfl.facilities.shared.application.vendor.VendorMessageVerifier;
import gh.edu.clet.sfl.facilities.shared.application.vendor.VendorRejectionRecorder;
import gh.edu.clet.sfl.facilities.shared.application.vendor.VendorSourcePort;
import gh.edu.clet.sfl.facilities.shared.domain.security.VendorChannel;
import gh.edu.clet.sfl.facilities.shared.domain.security.VendorRejectionReason;
import gh.edu.clet.sfl.facilities.shared.application.port.SecurityEventForwarderPort;
import gh.edu.clet.sfl.facilities.support.IfimpTestHarness;
import gh.edu.clet.sfl.facilities.support.InMemoryEnergyRepository;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The S157 module wired over {@link IfimpTestHarness}: real S152 for location resolution, an in-memory
 * energy repository, a real {@link VendorMessageVerifier} over a fake inbox/source registry (so the
 * forged-message test exercises the same class production does), and fakes for the two Phase 2 contracts
 * S157 consumes (S156's device directory, the AMI vendor adapter).
 */
public final class EnergyTestHarness {

    public static final String AMI_SOURCE = "AMI-TEST";
    public static final String AMI_SECRET = "test-ami-secret";

    public final IfimpTestHarness base = new IfimpTestHarness();
    public final InMemoryEnergyRepository repository = new InMemoryEnergyRepository();
    public final FakeEnergyDeviceDirectory devices = new FakeEnergyDeviceDirectory();
    public final FakeMeteringVendorAdapter vendorAdapter = new FakeMeteringVendorAdapter();
    public final FakeInbox inbox = new FakeInbox();

    public final EnergyConfiguration configuration = new EnergyConfiguration(base.configuration);
    public final VendorMessageVerifier verifier;
    public final EnergyMeterService meters;
    public final EnergyReadingService readings;
    public final EnergyVarianceService variance;
    public final SustainabilityKpiService kpis;
    public final EnergyHealthService health;

    public EnergyTestHarness() {
        VendorSourcePort sources = sourceId -> AMI_SOURCE.equals(sourceId)
                ? Optional.of(new VendorSourcePort.VendorSource(AMI_SOURCE, VendorChannel.ENERGY_METERING,
                        AMI_SECRET, Set.of("*")))
                : Optional.empty();
        SecurityEventForwarderPort forwarder = event -> new SecurityEventForwarderPort.ForwardResult("TEST", false);
        verifier = new VendorMessageVerifier(sources, inbox,
                new VendorRejectionRecorder(inbox, base.audit, forwarder, base.outbox, base.clock), base.clock);
        meters = new EnergyMeterService(repository, base.facilities, devices, configuration, base.authorization,
                base.audit, base.idempotency, base.outbox, base.clock);
        readings = new EnergyReadingService(repository, meters, vendorAdapter, devices, verifier, configuration,
                base.authorization, base.audit, base.idempotency, base.outbox, base.clock);
        variance = new EnergyVarianceService(repository, base.facilities, configuration, base.authorization,
                base.audit, base.outbox, base.clock);
        kpis = new SustainabilityKpiService(repository, base.facilities, configuration, base.authorization,
                base.audit, base.outbox, base.clock);
        health = new EnergyHealthService(repository, devices, vendorAdapter,
                new gh.edu.clet.sfl.facilities.shared.application.vendor.VendorIntegrationRegistry(
                        () -> java.util.List.of(
                                new gh.edu.clet.sfl.facilities.shared.application.vendor.VendorIntegrationCatalogPort
                                        .VendorIntegration("energy-metering", "S157",
                                                "Utility metering / AMI gateway", "Hybrid", vendorAdapter.adapterName(),
                                                null)),
                        base.authorization),
                base.authorization);
    }

    /** Registers the S156 telemetry observer's translation, for a test that exercises the shared-stream path. */
    public ActorContext energyOfficer() {
        return base.energyOfficer;
    }

    public ActorContext director() {
        return base.director;
    }

    public ActorContext integration() {
        return base.integration;
    }

    public static final class FakeInbox implements VendorInboxPort {
        private final Set<String> accepted = new HashSet<>();
        public final java.util.List<VendorRejectionReason> rejections = new java.util.ArrayList<>();

        @Override
        public boolean alreadyAccepted(String sourceId, String idempotencyKey) {
            return accepted.contains(sourceId + "|" + idempotencyKey);
        }

        @Override
        public UUID recordAccepted(InboxEntry entry) {
            accepted.add(entry.sourceId() + "|" + entry.idempotencyKey());
            return UUID.randomUUID();
        }

        @Override
        public UUID recordDuplicate(InboxEntry entry) {
            return UUID.randomUUID();
        }

        @Override
        public UUID recordRejected(InboxEntry entry, VendorRejectionReason reason, String detail) {
            rejections.add(reason);
            return UUID.randomUUID();
        }
    }
}
