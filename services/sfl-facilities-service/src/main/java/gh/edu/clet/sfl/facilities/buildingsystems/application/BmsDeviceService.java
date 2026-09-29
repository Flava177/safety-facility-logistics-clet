package gh.edu.clet.sfl.facilities.buildingsystems.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.facilities.buildingsystems.application.ports.BuildingSystemsRepository;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.AvampAssetProjection;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.BmsAlert;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.BmsDevice;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.DeviceStatus;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.policy.LifecycleReminderPolicy;
import gh.edu.clet.sfl.facilities.shared.application.FacilitiesAuthorization;
import gh.edu.clet.sfl.facilities.shared.application.ServiceOutbox;
import gh.edu.clet.sfl.facilities.shared.application.port.AuditPort;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditAction;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.model.EstateCodes;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The IoT device inventory and lifecycle - SRS-SFL-S156-04.
 *
 * <h2>What registration refuses</h2>
 *
 * <ul>
 *   <li>An AVAMP asset id S156 has not seen, or that AVAMP reports at another site or not active - "every
 *       BMS/IoT device is registered as an AVAMP asset with a stable identifier". Checked against the
 *       local projection fed by AVAMP's own events, so it holds while FTLMP is down.</li>
 *   <li>A second device on the same AVAMP asset - one physical box, one identity, which is also the rule
 *       S157 relies on to avoid double-registering a meter (S157-04).</li>
 *   <li>An active device already using the gateway's device code at the site.</li>
 *   <li>A location the S152 register does not resolve - "device-to-location mapping is resolved through
 *       S152".</li>
 * </ul>
 *
 * <h2>Retirement</h2>
 *
 * <p>"Decommissioning a device retires rather than deletes its record and history." Retiring stamps who,
 * when and why, clears the device's live alerts so the dashboard stops reporting a box that has been taken
 * off the wall, and leaves every reading, alert and work-order link in place. Telemetry that later arrives
 * under the retired device's code is quarantined as unregistered - until a replacement is registered under
 * that code.
 */
@Service
public class BmsDeviceService {

    private final BuildingSystemsRepository repository;
    private final S152LocationResolver locations;
    private final BuildingSystemsConfiguration configuration;
    private final FacilitiesAuthorization authorization;
    private final AuditPort audit;
    private final ServiceOutbox outbox;
    private final Clock clock;

    public BmsDeviceService(BuildingSystemsRepository repository, S152LocationResolver locations,
            BuildingSystemsConfiguration configuration, FacilitiesAuthorization authorization, AuditPort audit,
            ServiceOutbox outbox, Clock clock) {
        this.repository = repository;
        this.locations = locations;
        this.configuration = configuration;
        this.authorization = authorization;
        this.audit = audit;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public BmsDevice register(BuildingSystemsCommands.RegisterDevice command) {
        ActorContext actor = command.actor();
        String site = EstateCodes.normalize(command.siteCode());
        authorization.require(actor, SflPermission.FACILITIES_BMS_DEVICE_MANAGE, site, command.channel(), "BmsDevice",
                "new");
        EstateCodes.require(command.avampAssetId(), "avampAssetId");
        String avampAssetId = command.avampAssetId().strip();
        AvampAssetProjection asset = repository.findAvampAsset(avampAssetId)
                .filter(found -> found.siteCode().equals(site))
                .filter(AvampAssetProjection::isActive)
                .orElseThrow(() -> new FacilitiesException.InvalidParentReferenceException(
                        "active AVAMP asset at " + site, avampAssetId));
        repository.findDeviceByAvampAssetId(asset.avampAssetId()).ifPresent(existing -> {
            throw new FacilitiesException.DuplicateIdentifierException("BmsDevice (AVAMP asset)",
                    asset.avampAssetId(), existing.siteCode());
        });
        repository.findActiveDeviceByCode(site, command.deviceCode()).ifPresent(existing -> {
            throw new FacilitiesException.DuplicateIdentifierException("BmsDevice", existing.deviceCode(), site);
        });
        S152LocationResolver.ResolvedLocation location = resolveOrRefuse(site, command.buildingCode(),
                command.roomId());

        Instant now = clock.instant();
        BmsDevice device = repository.saveDevice(BmsDevice.register(UUID.randomUUID(), site, command.deviceCode(),
                asset.avampAssetId(), command.name(), command.systemType(), command.kind(), location.buildingCode(),
                location.roomId(), location.roomCode(), command.expectedIntervalSeconds(), command.installedOn(),
                command.firmwareVersion(), command.firmwareReviewDueOn(), command.warrantyExpiresOn(),
                command.calibrationDueOn(), actor.actorId(), now, command.channel(), actor.correlationId()));
        audit.record(actor, command.channel(), AuditAction.BMS_DEVICE_REGISTERED, "BmsDevice", device.id().toString(),
                device.siteCode(), null, device);
        BuildingSystemsEvents.publish(outbox, BuildingSystemsEvents.DEVICE_REGISTERED, "BmsDevice", device.id(),
                device.siteCode(), actor, devicePayload(device));
        return device;
    }

    @Transactional
    public BmsDevice revise(BuildingSystemsCommands.ReviseDevice command) {
        ActorContext actor = command.actor();
        BmsDevice device = requireDevice(command.deviceId());
        authorization.require(actor, SflPermission.FACILITIES_BMS_DEVICE_MANAGE, device.siteCode(), command.channel(),
                "BmsDevice", device.id().toString());
        device.metadata().requireVersion(command.expectedVersion(), "BmsDevice", device.id());
        S152LocationResolver.ResolvedLocation location = resolveOrRefuse(device.siteCode(),
                command.buildingCode() == null ? device.buildingCode() : command.buildingCode(), command.roomId());
        BmsDevice revised = repository.saveDevice(device.revise(
                command.name() == null ? device.name() : command.name(), location.buildingCode(), location.roomId(),
                location.roomCode(),
                command.expectedIntervalSeconds() > 0 ? command.expectedIntervalSeconds()
                        : device.expectedIntervalSeconds(),
                command.installedOn(), command.firmwareVersion(), command.firmwareReviewDueOn(),
                command.warrantyExpiresOn(), command.calibrationDueOn(), actor.actorId(), clock.instant(),
                command.channel(), actor.correlationId()));
        audit.record(actor, command.channel(), AuditAction.BMS_DEVICE_UPDATED, "BmsDevice", revised.id().toString(),
                revised.siteCode(), device, revised);
        return revised;
    }

    @Transactional
    public BmsDevice retire(BuildingSystemsCommands.RetireDevice command) {
        ActorContext actor = command.actor();
        BmsDevice device = requireDevice(command.deviceId());
        authorization.require(actor, SflPermission.FACILITIES_BMS_DEVICE_MANAGE, device.siteCode(), command.channel(),
                "BmsDevice", device.id().toString());
        device.metadata().requireVersion(command.expectedVersion(), "BmsDevice", device.id());
        Instant now = clock.instant();
        BmsDevice retired = repository.saveDevice(device.retire(command.reason(), actor.actorId(), now,
                command.channel(), actor.correlationId()));
        for (BmsAlert alert : repository.findActiveAlerts(List.of(device.id()))) {
            BmsAlert cleared = repository.saveAlert(alert.clear(actor.actorId(), now, command.channel(),
                    actor.correlationId()));
            audit.record(actor, command.channel(), AuditAction.BMS_ALERT_CLEARED, "BmsAlert", cleared.id().toString(),
                    cleared.siteCode(), alert, cleared);
        }
        audit.record(actor, command.channel(), AuditAction.BMS_DEVICE_RETIRED, "BmsDevice", retired.id().toString(),
                retired.siteCode(), device, retired);
        Map<String, Object> payload = devicePayload(retired);
        payload.put("retiredAt", now.toString());
        BuildingSystemsEvents.publish(outbox, BuildingSystemsEvents.DEVICE_RETIRED, "BmsDevice", retired.id(),
                retired.siteCode(), actor, payload);
        return retired;
    }

    @Transactional(readOnly = true)
    public List<BmsDevice> devices(String siteCode, DeviceStatus status, ActorContext actor, SourceChannel channel) {
        authorization.require(actor, SflPermission.FACILITIES_BMS_READ, channel, "BmsDevice", "list", siteCode);
        authorization.requireRequestedSite(actor, siteCode, channel, "BmsDevice");
        return authorization.filterBySite(actor, repository.findDevices(siteCode, status), BmsDevice::siteCode);
    }

    @Transactional(readOnly = true)
    public BmsDevice device(UUID deviceId, ActorContext actor, SourceChannel channel) {
        BmsDevice device = requireDevice(deviceId);
        authorization.require(actor, SflPermission.FACILITIES_BMS_READ, device.siteCode(), channel, "BmsDevice",
                deviceId.toString());
        return device;
    }

    /** What AVAMP currently says about a device's asset - for comparing AVAMP's location with S152's. */
    @Transactional(readOnly = true)
    public java.util.Optional<AvampAssetProjection> avampView(BmsDevice device) {
        return repository.findAvampAsset(device.avampAssetId());
    }

    /**
     * Raises the calibration, firmware-review and warranty reminders that have come due - S156-04.
     *
     * <p>One reminder per item per due date (see {@link LifecycleReminderPolicy}), each audited and published
     * as {@code bms-device-lifecycle-due} so a notification consumer can route it. Dates are compared in UTC,
     * which is Ghana's civil time.
     */
    @Transactional
    public int sweepLifecycleReminders(ActorContext actor) {
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        int raised = 0;
        for (BmsDevice device : repository.findDevices(null, DeviceStatus.ACTIVE)) {
            BmsDevice current = device;
            for (LifecycleReminderPolicy.Due due : LifecycleReminderPolicy.due(device, today,
                    configuration.reminderLeadDays(device.siteCode()))) {
                BmsDevice before = current;
                current = repository.saveDevice(current.withReminderRaised(due.item(), due.dueOn(), actor.actorId(),
                        now, SourceChannel.SCHEDULER, actor.correlationId()));
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("deviceId", current.id().toString());
                payload.put("avampAssetId", current.avampAssetId());
                payload.put("deviceCode", current.deviceCode());
                payload.put("siteCode", current.siteCode());
                payload.put("item", due.item().name());
                payload.put("dueOn", due.dueOn().toString());
                payload.put("overdue", due.dueOn().isBefore(today));
                audit.record(actor, SourceChannel.SCHEDULER, AuditAction.BMS_DEVICE_LIFECYCLE_REMINDER_RAISED,
                        "BmsDevice", current.id().toString(), current.siteCode(), before, payload);
                BuildingSystemsEvents.publish(outbox, BuildingSystemsEvents.DEVICE_LIFECYCLE_DUE, "BmsDevice",
                        current.id(), current.siteCode(), actor, payload);
                raised++;
            }
        }
        return raised;
    }

    private S152LocationResolver.ResolvedLocation resolveOrRefuse(String site, String buildingCode, UUID roomId) {
        EstateCodes.require(buildingCode, "buildingCode");
        return locations.resolve(site, EstateCodes.normalize(buildingCode), roomId)
                .orElseThrow(() -> new FacilitiesException.InvalidParentReferenceException("S152 location",
                        buildingCode + (roomId == null ? "" : "/" + roomId)));
    }

    private BmsDevice requireDevice(UUID id) {
        return repository.findDevice(id)
                .orElseThrow(() -> new FacilitiesException.RecordNotFoundException("BmsDevice", id));
    }

    private static Map<String, Object> devicePayload(BmsDevice device) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("deviceId", device.id().toString());
        payload.put("avampAssetId", device.avampAssetId());
        payload.put("deviceCode", device.deviceCode());
        payload.put("siteCode", device.siteCode());
        payload.put("buildingCode", device.buildingCode());
        payload.put("roomId", device.roomId() == null ? null : device.roomId().toString());
        payload.put("systemType", device.systemType().name());
        payload.put("kind", device.kind().name());
        payload.put("status", device.status().name());
        return payload;
    }
}
