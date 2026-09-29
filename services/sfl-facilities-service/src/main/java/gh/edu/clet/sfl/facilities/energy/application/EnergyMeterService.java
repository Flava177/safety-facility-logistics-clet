package gh.edu.clet.sfl.facilities.energy.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.facilities.energy.application.ports.EnergyDeviceDirectoryPort;
import gh.edu.clet.sfl.facilities.energy.application.ports.EnergyRepository;
import gh.edu.clet.sfl.facilities.energy.domain.EnergyMeter;
import gh.edu.clet.sfl.facilities.energy.domain.MeterSource;
import gh.edu.clet.sfl.facilities.energy.domain.Utility;
import gh.edu.clet.sfl.facilities.masterdata.application.ports.FacilitiesRepository;
import gh.edu.clet.sfl.facilities.masterdata.domain.Building;
import gh.edu.clet.sfl.facilities.masterdata.domain.FacilityRoom;
import gh.edu.clet.sfl.facilities.shared.application.FacilitiesAuthorization;
import gh.edu.clet.sfl.facilities.shared.application.ServiceOutbox;
import gh.edu.clet.sfl.facilities.shared.application.port.AuditPort;
import gh.edu.clet.sfl.facilities.shared.application.port.IdempotencyPort;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditAction;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesErrorCode;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.model.EstateCodes;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The meter register - SRS-SFL-S157-01 (resolution to S152) and SRS-SFL-S157-04 (one identity per device).
 *
 * <h2>The S157-04 rule, and where each half of it is enforced</h2>
 *
 * "A device must not be double-registered - one AVAMP asset identity per physical meter/device,
 * referenced by both modules." Registration asks two questions, in this order:
 *
 * <ol>
 *   <li><strong>Is the identity already a meter here?</strong> Refused with
 *       {@code ENERGY_DEVICE_DOUBLE_REGISTERED}. {@code ux_energy_meters_avamp} is what holds under a race;
 *       the adapter translates it to the same error.</li>
 *   <li><strong>Is the identity an S156 device?</strong> Then the meter must be {@code BMS_STREAM} and
 *       consume S156's normalised stream. Registering it as AMI would open a second vendor connection to a
 *       device S156 already ingests - "two competing [boundaries] for the same devices" - and is refused
 *       with {@code ENERGY_DEVICE_DOUBLE_REGISTERED}. MANUAL is refused for the same device too: a second
 *       identity is a second identity whether a gateway or a clipboard feeds it.</li>
 * </ol>
 *
 * <p>The reverse case - an AMI meter registered first, whose device S156 enrols later - cannot be refused at
 * registration because it was fine then. It is refused at ingestion (the AMI path rejects the message) and
 * listed by the health view as a conflict, with {@link #update} moving the meter onto the stream as the repair.
 */
@Service
public class EnergyMeterService {

    private static final String RESOURCE = "EnergyMeter";

    private final EnergyRepository repository;
    private final FacilitiesRepository facilities;
    private final EnergyDeviceDirectoryPort devices;
    private final EnergyConfiguration configuration;
    private final FacilitiesAuthorization authorization;
    private final AuditPort audit;
    private final IdempotencyPort idempotency;
    private final ServiceOutbox outbox;
    private final Clock clock;

    public EnergyMeterService(EnergyRepository repository, FacilitiesRepository facilities,
            EnergyDeviceDirectoryPort devices, EnergyConfiguration configuration, FacilitiesAuthorization authorization,
            AuditPort audit, IdempotencyPort idempotency, ServiceOutbox outbox, Clock clock) {
        this.repository = repository;
        this.facilities = facilities;
        this.devices = devices;
        this.configuration = configuration;
        this.authorization = authorization;
        this.audit = audit;
        this.idempotency = idempotency;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public EnergyMeter register(EnergyCommands.RegisterMeter command) {
        ActorContext actor = command.actor();
        String siteCode = requireCode(command.siteCode(), "siteCode");
        authorization.require(actor, SflPermission.FACILITIES_ENERGY_METER_MANAGE, siteCode, command.channel(),
                RESOURCE, "new");
        if (command.idempotencyKey() != null) {
            Optional<EnergyMeter> replayed = idempotency.findExistingResult("register-energy-meter",
                    command.idempotencyKey(), idempotency.fingerprint(command.idempotencyPayload()))
                    .flatMap(repository::findMeter);
            if (replayed.isPresent()) {
                return replayed.get();
            }
        }
        if (command.utility() == null || command.source() == null) {
            throw new FacilitiesException.ValidationFailedException("A meter needs a utility and a source.");
        }
        String meterCode = requireCode(command.meterCode(), "meterCode");
        if (command.name() == null || command.name().isBlank()) {
            throw new FacilitiesException.ValidationFailedException("A meter needs a name.");
        }
        String buildingCode = resolveLocation(siteCode, command.buildingCode(), command.roomId());
        if (repository.findMeterByCode(siteCode, meterCode).isPresent()) {
            throw new FacilitiesException.DuplicateIdentifierException("energy meter", meterCode, siteCode);
        }

        String avamp = command.avampAssetId() == null || command.avampAssetId().isBlank() ? null
                : command.avampAssetId().strip();
        UUID bmsDeviceId = avamp == null ? null : resolveDeviceIdentity(avamp, command.source(), siteCode);
        if (command.source() == MeterSource.BMS_STREAM && avamp == null) {
            throw new FacilitiesException.ValidationFailedException(
                    "A BMS_STREAM meter must carry the AVAMP identity of its S156 device.");
        }
        String vendorRef = command.vendorMeterRef() == null || command.vendorMeterRef().isBlank() ? null
                : command.vendorMeterRef().strip();
        if (command.source() != MeterSource.AMI) {
            vendorRef = null;
        } else if (vendorRef != null && repository.findMeterByVendorRef(vendorRef).isPresent()) {
            throw new FacilitiesException.DuplicateIdentifierException("AMI meter reference", vendorRef, siteCode);
        }
        int interval = command.expectedIntervalMinutes() == null
                ? configuration.defaultExpectedIntervalMinutes(siteCode, command.source())
                : command.expectedIntervalMinutes();

        Instant at = clock.instant();
        EnergyMeter meter = repository.saveMeter(EnergyMeter.register(UUID.randomUUID(), siteCode, buildingCode,
                command.roomId(), meterCode, command.name(), command.utility(), command.source(), avamp, vendorRef,
                bmsDeviceId, interval, actor.actorId(), at, command.channel(), actor.correlationId()));
        audit.record(actor, command.channel(), AuditAction.ENERGY_METER_REGISTERED, RESOURCE, meter.id().toString(),
                siteCode, null, meter);
        outbox.record(EnergyEvents.METER_REGISTERED, 1, RESOURCE, meter.id(), siteCode, actor.correlationId(),
                actor.actorId(), EnergyEvents.MeterRegistered.of(meter));
        if (command.idempotencyKey() != null) {
            idempotency.recordResult("register-energy-meter", command.idempotencyKey(),
                    idempotency.fingerprint(command.idempotencyPayload()), meter.id(), siteCode, actor.actorId());
        }
        return meter;
    }

    /** Renames, re-times, or moves an AMI/MANUAL meter onto S156's stream (the S157-04 repair). */
    @Transactional
    public EnergyMeter update(EnergyCommands.UpdateMeter command) {
        EnergyMeter meter = requireMeter(command.meterId());
        authorization.require(command.actor(), SflPermission.FACILITIES_ENERGY_METER_MANAGE, meter.siteCode(),
                command.channel(), RESOURCE, meter.id().toString());
        meter.metadata().requireVersion(command.expectedVersion(), RESOURCE, meter.id());
        UUID device = meter.bmsDeviceId();
        if (command.source() == MeterSource.BMS_STREAM && meter.source() != MeterSource.BMS_STREAM) {
            if (meter.avampAssetId() == null) {
                throw new FacilitiesException.ValidationFailedException(
                        "Meter " + meter.meterCode() + " has no AVAMP identity, so S156 cannot be streaming it.");
            }
            device = devices.findByAvampAssetId(meter.avampAssetId())
                    .map(EnergyDeviceDirectoryPort.DeviceIdentity::deviceId)
                    .orElseThrow(() -> new FacilitiesException.ValidationFailedException(
                            "AVAMP asset " + meter.avampAssetId() + " is not an S156 device; there is no stream to "
                                    + "consume."));
        }
        EnergyMeter updated = repository.saveMeter(meter.update(command.name(), command.expectedIntervalMinutes(),
                command.source(), device, command.actor().actorId(), clock.instant(), command.channel(),
                command.actor().correlationId()));
        audit.record(command.actor(), command.channel(), AuditAction.ENERGY_METER_UPDATED, RESOURCE,
                meter.id().toString(), meter.siteCode(), meter, updated);
        return updated;
    }

    @Transactional
    public EnergyMeter retire(EnergyCommands.RetireMeter command) {
        EnergyMeter meter = requireMeter(command.meterId());
        authorization.require(command.actor(), SflPermission.FACILITIES_ENERGY_METER_MANAGE, meter.siteCode(),
                command.channel(), RESOURCE, meter.id().toString());
        meter.metadata().requireVersion(command.expectedVersion(), RESOURCE, meter.id());
        EnergyMeter retired = repository.saveMeter(meter.retire(command.reason(), command.actor().actorId(),
                clock.instant(), command.channel(), command.actor().correlationId()));
        audit.record(command.actor(), command.channel(), AuditAction.ENERGY_METER_RETIRED, RESOURCE,
                meter.id().toString(), meter.siteCode(), meter, retired);
        return retired;
    }

    @Transactional(readOnly = true)
    public List<EnergyMeter> list(String siteCode, Utility utility, boolean activeOnly, ActorContext actor,
            SourceChannel channel) {
        String site = siteCode == null || siteCode.isBlank() ? null : EstateCodes.normalize(siteCode);
        authorization.require(actor, SflPermission.FACILITIES_ENERGY_READ, channel, RESOURCE, "list", site);
        authorization.requireRequestedSite(actor, site, channel, RESOURCE);
        return authorization.filterBySite(actor, repository.findMeters(site, utility, activeOnly),
                EnergyMeter::siteCode);
    }

    @Transactional(readOnly = true)
    public EnergyMeter get(UUID id, ActorContext actor, SourceChannel channel) {
        EnergyMeter meter = requireMeter(id);
        authorization.require(actor, SflPermission.FACILITIES_ENERGY_READ, meter.siteCode(), channel, RESOURCE,
                id.toString());
        return meter;
    }

    EnergyMeter requireMeter(UUID id) {
        return repository.findMeter(id).orElseThrow(() -> new FacilitiesException.RecordNotFoundException(RESOURCE, id));
    }

    /**
     * S157-01 "resolved to S152 site/building references". The building must exist and be in use at the
     * site; a room, when given, must be on a floor of that building - a meter "in HALL-A of the Kumasi
     * block" is a data-entry error, and one that would put a building's consumption against the wrong budget.
     */
    private String resolveLocation(String siteCode, String buildingCode, UUID roomId) {
        if (facilities.findSiteByCode(siteCode).isEmpty()) {
            throw new FacilitiesException.InvalidParentReferenceException("Site", siteCode);
        }
        String code = requireCode(buildingCode, "buildingCode");
        Building building = facilities.findBuildingByCode(siteCode, code)
                .orElseThrow(() -> new FacilitiesException.InvalidParentReferenceException("Building", code));
        if (!building.lifecycleStatus().isOperational()) {
            throw new FacilitiesException.ValidationFailedException(
                    "Building " + code + " is " + building.lifecycleStatus() + "; a meter cannot be registered to it.");
        }
        if (roomId != null) {
            FacilityRoom room = facilities.findRoom(roomId)
                    .orElseThrow(() -> new FacilitiesException.InvalidParentReferenceException("Space", roomId));
            boolean inBuilding = room.siteCode().equals(siteCode) && facilities.findFloor(room.floorId())
                    .map(floor -> floor.buildingId().equals(building.id())).orElse(false);
            if (!inBuilding) {
                throw new FacilitiesException.ValidationFailedException(
                        "Room " + room.roomCode() + " is not in building " + code + " at " + siteCode + ".");
            }
        }
        return code;
    }

    /** The two S157-04 questions. Returns the S156 device id for a BMS_STREAM meter. */
    private UUID resolveDeviceIdentity(String avamp, MeterSource source, String siteCode) {
        repository.findMeterByAvampAssetId(avamp).ifPresent(existing -> {
            throw new FacilitiesException(FacilitiesErrorCode.ENERGY_DEVICE_DOUBLE_REGISTERED,
                    FacilitiesErrorCode.ENERGY_DEVICE_DOUBLE_REGISTERED.defaultMessage() + " AVAMP asset " + avamp
                            + " is already meter " + existing.meterCode() + " at " + existing.siteCode() + ".");
        });
        Optional<EnergyDeviceDirectoryPort.DeviceIdentity> device = devices.findByAvampAssetId(avamp);
        if (device.isPresent()) {
            if (source != MeterSource.BMS_STREAM) {
                throw new FacilitiesException(FacilitiesErrorCode.ENERGY_DEVICE_DOUBLE_REGISTERED,
                        FacilitiesErrorCode.ENERGY_DEVICE_DOUBLE_REGISTERED.defaultMessage() + " AVAMP asset " + avamp
                                + " is already S156 device " + device.get().deviceCode()
                                + "; register the meter as BMS_STREAM so it consumes the existing normalised stream "
                                + "rather than opening a second vendor connection.");
            }
            if (!siteCode.equals(device.get().siteCode())) {
                throw new FacilitiesException.ValidationFailedException("S156 device " + device.get().deviceCode()
                        + " is at " + device.get().siteCode() + ", not " + siteCode + ".");
            }
            return device.get().deviceId();
        }
        if (source == MeterSource.BMS_STREAM) {
            throw new FacilitiesException.ValidationFailedException("AVAMP asset " + avamp
                    + " is not an S156 device. A BMS_STREAM meter consumes S156's stream, so the device must be "
                    + "registered in S156 first.");
        }
        return null;
    }

    private static String requireCode(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new FacilitiesException.ValidationFailedException(field + " is required.");
        }
        return EstateCodes.normalize(value);
    }
}
