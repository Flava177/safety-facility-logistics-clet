package gh.edu.clet.sfl.facilities.masterdata.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.facilities.masterdata.application.ports.FacilitiesRepository;
import gh.edu.clet.sfl.facilities.masterdata.domain.DeviceReference;
import gh.edu.clet.sfl.facilities.shared.application.FacilitiesAuthorization;
import gh.edu.clet.sfl.facilities.shared.application.port.AuditPort;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditAction;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import java.util.Optional;
import java.util.UUID;

/**
 * The device-reference commands - split out of {@link FacilitiesMasterDataService} for the same
 * reason the rest of that class's Javadoc gives. Holds a reference to that class to reuse {@code
 * requireDeviceReference}, {@code requireRoomInSite}, {@code replay}, {@code remember}, {@code
 * publish}, {@code now} and {@code normalize}.
 */
final class DeviceReferenceCommands {

    private final FacilitiesMasterDataService service;
    private final FacilitiesRepository facilities;
    private final FacilitiesAuthorization authorization;
    private final AuditPort audit;

    DeviceReferenceCommands(FacilitiesMasterDataService service, FacilitiesRepository facilities,
            FacilitiesAuthorization authorization, AuditPort audit) {
        this.service = service;
        this.facilities = facilities;
        this.authorization = authorization;
        this.audit = audit;
    }

    DeviceReference register(FacilitiesCommands.RegisterDeviceReference command) {
        ActorContext actor = command.actor();
        String siteCode = FacilitiesMasterDataService.normalize(command.siteCode());
        authorization.require(actor, SflPermission.FACILITIES_DEVICE_REFERENCE_REGISTER, siteCode,
                command.channel(), "DeviceReference", FacilitiesMasterDataService.normalize(command.deviceCode()));

        Optional<DeviceReference> replayed = service.replay("register-device-reference",
                command.idempotencyKey(), command.idempotencyPayload(), facilities::findDeviceReference);
        if (replayed.isPresent()) {
            return replayed.get();
        }

        String deviceCode = FacilitiesMasterDataService.normalize(command.deviceCode());
        facilities.findDeviceReferenceByCode(siteCode, deviceCode).ifPresent(existing -> {
            if (existing.lifecycleStatus().occupiesIdentifier()) {
                throw new FacilitiesException.DuplicateIdentifierException("device reference", deviceCode,
                        siteCode);
            }
        });
        if (command.roomId() != null) {
            service.requireRoomInSite(command.roomId(), siteCode);
        }

        DeviceReference saved = facilities.saveDeviceReference(DeviceReference.register(UUID.randomUUID(),
                siteCode, deviceCode, command.name(), command.type(), command.roomId(), command.locationCode(),
                command.vendor(), command.externalReference(), actor.actorId(), service.now(), command.channel(),
                actor.correlationId()));

        audit.record(actor, command.channel(), AuditAction.DEVICE_REFERENCE_REGISTERED, "DeviceReference",
                saved.id().toString(), saved.siteCode(), null, saved);
        service.publish("sfl.ifimp.device-reference-registered.v1", "DeviceReference", saved.id(),
                saved.siteCode(), actor, saved);
        service.remember("register-device-reference", command.idempotencyKey(), command.idempotencyPayload(),
                saved.id(), saved.siteCode(), actor);
        return saved;
    }

    /**
     * Corrects a device reference.
     *
     * <p>Takes the registration permission rather than a new one: whoever may put a device on the
     * estate map is the one who has to fix it when the vendor renames it, and inventing a second
     * grant for the correction would leave the register accumulating wrong names nobody was allowed
     * to touch.
     *
     * <p>The status is not editable here. It belongs to the vendor feed, and a hand-set status would
     * be this service asserting an observation it has not made.
     */
    DeviceReference update(FacilitiesCommands.UpdateDeviceReference command) {
        ActorContext actor = command.actor();
        DeviceReference device = service.requireDeviceReference(command.deviceId());
        authorization.require(actor, SflPermission.FACILITIES_DEVICE_REFERENCE_REGISTER, device.siteCode(),
                command.channel(), "DeviceReference", device.id().toString());
        device.metadata().requireVersion(command.expectedVersion(), "DeviceReference", device.id());

        DeviceReference saved = facilities.saveDeviceReference(device.update(command.name(), command.type(),
                command.vendor(), command.externalReference(), actor.actorId(), service.now(), command.channel(),
                actor.correlationId()));

        audit.record(actor, command.channel(), AuditAction.DEVICE_REFERENCE_UPDATED, "DeviceReference",
                saved.id().toString(), saved.siteCode(), device, saved);
        service.publish("sfl.ifimp.device-reference-updated.v1", "DeviceReference", saved.id(), saved.siteCode(),
                actor, saved);
        return saved;
    }

    DeviceReference changeLifecycle(FacilitiesCommands.ChangeDeviceReferenceLifecycle command) {
        ActorContext actor = command.actor();
        DeviceReference device = service.requireDeviceReference(command.deviceId());
        authorization.require(actor, SflPermission.FACILITIES_DEVICE_REFERENCE_REGISTER, device.siteCode(),
                command.channel(), "DeviceReference", device.id().toString());
        device.metadata().requireVersion(command.expectedVersion(), "DeviceReference", device.id());

        DeviceReference saved = facilities.saveDeviceReference(device.changeLifecycle(command.status(),
                actor.actorId(), service.now(), command.channel(), actor.correlationId()));

        audit.record(actor, command.channel(), AuditAction.DEVICE_REFERENCE_LIFECYCLE_CHANGED,
                "DeviceReference", saved.id().toString(), saved.siteCode(), device.lifecycleStatus(),
                saved.lifecycleStatus());
        service.publish("sfl.ifimp.device-reference-lifecycle-changed.v1", "DeviceReference", saved.id(),
                saved.siteCode(), actor, saved);
        return saved;
    }
}
