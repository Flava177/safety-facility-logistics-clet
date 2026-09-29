package gh.edu.clet.sfl.facilities.buildingsystems.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.facilities.buildingsystems.application.ports.BuildingSystemsRepository;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.BmsAlert;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.BmsDevice;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.BuildingSystemType;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.ChannelState;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.DeviceStatus;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.HealthState;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.policy.DeviceHealthPolicy;
import gh.edu.clet.sfl.facilities.shared.application.FacilitiesAuthorization;
import gh.edu.clet.sfl.facilities.shared.application.vendor.VendorIntegrationRegistry;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.model.EstateCodes;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The building-system health dashboard - SRS-SFL-S156-03.
 *
 * <p>"Dashboard rolls up per-building/system health (normal, degraded, fault, offline)." Computed on read
 * from the live channel states and active alerts rather than stored, so the answer is never older than the
 * request: staleness is a function of <em>now</em>, and a stored "NORMAL" would go on saying so after the
 * sensor died. Each device's state comes from {@link DeviceHealthPolicy}; systems, buildings and the site
 * take the worst of their members ({@link HealthState#worstOf}), so one silent sensor keeps a building off
 * green.
 *
 * <p>The response carries the S156 procurement-gate status (CORR-07): while no §5.2 evidence is on file it
 * reads {@code SIMULATED_ADAPTER_ONLY}, and the dashboard must not describe this feed as integrated.
 */
@Service
public class BuildingHealthService {

    static final String PROCUREMENT_KEY = "bms-iot";

    private final BuildingSystemsRepository repository;
    private final BuildingSystemsConfiguration configuration;
    private final VendorIntegrationRegistry vendorIntegrations;
    private final FacilitiesAuthorization authorization;
    private final Clock clock;

    public BuildingHealthService(BuildingSystemsRepository repository, BuildingSystemsConfiguration configuration,
            VendorIntegrationRegistry vendorIntegrations, FacilitiesAuthorization authorization, Clock clock) {
        this.repository = repository;
        this.configuration = configuration;
        this.vendorIntegrations = vendorIntegrations;
        this.authorization = authorization;
        this.clock = clock;
    }

    public record DeviceHealthView(UUID deviceId, String deviceCode, String avampAssetId, String locationCode,
            HealthState state, String reason, Instant lastObservedAt, List<UUID> activeAlertIds,
            List<String> workOrderNumbers) {
    }

    public record SystemHealth(BuildingSystemType systemType, HealthState state, List<DeviceHealthView> devices) {
    }

    public record BuildingHealth(String buildingCode, HealthState state, List<SystemHealth> systems) {
    }

    public record SiteHealth(String siteCode, HealthState state, Instant generatedAt, int activeAlerts,
            int linkedWorkOrders, VendorIntegrationRegistry.GateStatus procurementGate, List<BuildingHealth> buildings) {
    }

    @Transactional(readOnly = true)
    public SiteHealth health(String siteCode, ActorContext actor, SourceChannel channel) {
        String site = EstateCodes.normalize(siteCode);
        authorization.require(actor, SflPermission.FACILITIES_BMS_READ, site, channel, "BmsHealth", site);
        Instant now = clock.instant();
        int staleAfter = configuration.staleAfterIntervals(site);

        List<BmsDevice> devices = repository.findDevices(site, DeviceStatus.ACTIVE);
        List<UUID> ids = devices.stream().map(BmsDevice::id).toList();
        Map<UUID, List<ChannelState>> channels = repository.findChannelStates(ids).stream()
                .collect(Collectors.groupingBy(ChannelState::deviceId));
        Map<UUID, List<BmsAlert>> alerts = repository.findActiveAlerts(ids).stream()
                .collect(Collectors.groupingBy(BmsAlert::deviceId));

        Map<String, Map<BuildingSystemType, List<DeviceHealthView>>> tree = new TreeMap<>();
        for (BmsDevice device : devices) {
            List<BmsAlert> active = alerts.getOrDefault(device.id(), List.of());
            DeviceHealthPolicy.DeviceHealth health = DeviceHealthPolicy.assess(device,
                    channels.getOrDefault(device.id(), List.of()), active, now, staleAfter);
            tree.computeIfAbsent(device.buildingCode(), ignored -> new TreeMap<>())
                    .computeIfAbsent(device.systemType(), ignored -> new java.util.ArrayList<>())
                    .add(new DeviceHealthView(device.id(), device.deviceCode(), device.avampAssetId(),
                            device.locationCode(), health.state(), health.reason(), health.lastObservedAt(),
                            active.stream().map(BmsAlert::id).toList(),
                            active.stream().map(BmsAlert::workOrderNumber).filter(java.util.Objects::nonNull)
                                    .toList()));
        }

        List<BuildingHealth> buildings = tree.entrySet().stream().map(building -> {
            List<SystemHealth> systems = building.getValue().entrySet().stream()
                    .map(system -> new SystemHealth(system.getKey(),
                            HealthState.worstOf(system.getValue().stream().map(DeviceHealthView::state).toList()),
                            List.copyOf(system.getValue())))
                    .toList();
            return new BuildingHealth(building.getKey(),
                    HealthState.worstOf(systems.stream().map(SystemHealth::state).toList()), systems);
        }).toList();

        List<BmsAlert> allActive = alerts.values().stream().flatMap(List::stream).toList();
        return new SiteHealth(site, HealthState.worstOf(buildings.stream().map(BuildingHealth::state).toList()), now,
                allActive.size(), (int) allActive.stream().filter(BmsAlert::hasWorkOrder).count(),
                vendorIntegrations.gateStatus(PROCUREMENT_KEY), buildings);
    }
}
