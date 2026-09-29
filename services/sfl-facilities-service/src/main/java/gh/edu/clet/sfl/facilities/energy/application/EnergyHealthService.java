package gh.edu.clet.sfl.facilities.energy.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.facilities.energy.application.ports.EnergyDeviceDirectoryPort;
import gh.edu.clet.sfl.facilities.energy.application.ports.EnergyRepository;
import gh.edu.clet.sfl.facilities.energy.application.ports.MeteringVendorPort;
import gh.edu.clet.sfl.facilities.energy.domain.ConsumptionReading;
import gh.edu.clet.sfl.facilities.energy.domain.EnergyMeter;
import gh.edu.clet.sfl.facilities.energy.domain.MeterSource;
import gh.edu.clet.sfl.facilities.energy.domain.ReadingStatus;
import gh.edu.clet.sfl.facilities.shared.application.FacilitiesAuthorization;
import gh.edu.clet.sfl.facilities.shared.application.vendor.VendorIntegrationRegistry;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.model.EstateCodes;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * S157's integration and health view - what the runbook's first check reads.
 *
 * <p>Carries the procurement-gate status from {@link VendorIntegrationRegistry} rather than any claim of its
 * own (SRS CORR-07, §5.2): until evidence is on file the AMI feed reports {@code SIMULATED_ADAPTER_ONLY}, and
 * nothing here can say "integrated". Also lists the S157-04 conflicts - meters still fed by AMI or by hand
 * whose AVAMP identity S156 now ingests - because those are exactly the meters whose gateway readings are
 * being refused.
 */
@Service
public class EnergyHealthService {

    static final String INTEGRATION_KEY = "energy-metering";

    private final EnergyRepository repository;
    private final EnergyDeviceDirectoryPort devices;
    private final MeteringVendorPort vendor;
    private final VendorIntegrationRegistry integrations;
    private final FacilitiesAuthorization authorization;

    public EnergyHealthService(EnergyRepository repository, EnergyDeviceDirectoryPort devices,
            MeteringVendorPort vendor, VendorIntegrationRegistry integrations, FacilitiesAuthorization authorization) {
        this.repository = repository;
        this.devices = devices;
        this.vendor = vendor;
        this.integrations = integrations;
        this.authorization = authorization;
    }

    public record DeviceConflict(String meterId, String meterCode, String siteCode, String source,
            String avampAssetId, String s156DeviceCode) {
    }

    public record EnergyHealth(String integrationKey, VendorIntegrationRegistry.GateStatus gateStatus, String adapter,
            String statement, Map<MeterSource, Long> activeMetersBySource, long heldReadings,
            Instant latestReadingAt, List<DeviceConflict> deviceConflicts) {
    }

    @Transactional(readOnly = true)
    public EnergyHealth health(String siteCode, ActorContext actor, SourceChannel channel) {
        String site = siteCode == null || siteCode.isBlank() ? null : EstateCodes.normalize(siteCode);
        authorization.require(actor, SflPermission.FACILITIES_ENERGY_READ, channel, "EnergyHealth", "view", site);
        authorization.requireRequestedSite(actor, site, channel, "EnergyHealth");
        List<EnergyMeter> meters = authorization.filterBySite(actor, repository.findMeters(site, null, true),
                EnergyMeter::siteCode);
        Map<MeterSource, Long> bySource = new EnumMap<>(MeterSource.class);
        for (MeterSource source : MeterSource.values()) {
            bySource.put(source, meters.stream().filter(meter -> meter.source() == source).count());
        }
        List<ConsumptionReading> held = authorization.filterBySite(actor, repository.findReadings(
                new EnergyRepository.ReadingQuery(site, null, ReadingStatus.HELD, null, null, 1000)),
                ConsumptionReading::siteCode);
        Instant latest = authorization.filterBySite(actor, repository.findReadings(
                new EnergyRepository.ReadingQuery(site, null, ReadingStatus.POSTED, null, null, 1)),
                ConsumptionReading::siteCode).stream().map(ConsumptionReading::observedAt).findFirst().orElse(null);
        List<DeviceConflict> conflicts = meters.stream()
                .filter(meter -> meter.source() != MeterSource.BMS_STREAM && meter.avampAssetId() != null)
                .flatMap(meter -> devices.findByAvampAssetId(meter.avampAssetId()).stream()
                        .map(device -> new DeviceConflict(meter.id().toString(), meter.meterCode(), meter.siteCode(),
                                meter.source().name(), meter.avampAssetId(), device.deviceCode())))
                .toList();
        VendorIntegrationRegistry.GateStatus gate = integrations.gateStatus(INTEGRATION_KEY);
        String statement = gate == VendorIntegrationRegistry.GateStatus.GATE_EVIDENCE_ON_FILE
                ? "Procurement-gate evidence is on file for the metering integration."
                : "Recorded/simulated adapter only (" + vendor.adapterName() + "). No SRS §5.2 procurement-gate "
                        + "evidence is on file, so the metering integration must not be reported as integrated.";
        return new EnergyHealth(INTEGRATION_KEY, gate, vendor.adapterName(), statement, bySource, held.size(), latest,
                conflicts);
    }
}
