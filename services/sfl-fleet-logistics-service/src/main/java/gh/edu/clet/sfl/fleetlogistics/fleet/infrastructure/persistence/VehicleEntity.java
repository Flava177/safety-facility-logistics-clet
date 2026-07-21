package gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.persistence;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.DistanceUnit;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.OdometerReading;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.OdometerSource;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.OperatingMode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.RecordMetadata;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.RegistrationNumber;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.RestrictedUse;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.Vehicle;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleAvailabilityStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleCategory;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleIdentificationNumber;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleLifecycleStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleServiceStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleSpecification;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Persistence image of {@link Vehicle}.
 *
 * <p>A JPA entity, never a domain aggregate: it is mutable so Hibernate can manage the optimistic-lock
 * version, and {@link #applyFrom} copies a new aggregate state onto a managed row so the {@code @Version}
 * check runs on update instead of being bypassed by a fresh insert.
 */
@Entity
@Table(name = "vehicles", schema = "fleet_logistics")
public class VehicleEntity {

    @Id
    private UUID id;

    @Column(name = "registration_number", nullable = false, length = 40)
    private String registrationNumber;

    @Column(length = 40)
    private String vin;

    @Column(nullable = false, length = 80)
    private String make;

    @Column(nullable = false, length = 80)
    private String model;

    @Column(name = "manufacture_year", nullable = false)
    private int manufactureYear;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private VehicleCategory category;

    @Column(nullable = false)
    private int capacity;

    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;

    @Column(name = "responsible_unit", nullable = false, length = 160)
    private String responsibleUnit;

    @Column(name = "operational_owner", nullable = false, length = 160)
    private String operationalOwner;

    @Column(name = "acquisition_reference", length = 120)
    private String acquisitionReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false, length = 30)
    private VehicleLifecycleStatus lifecycleStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "service_status", nullable = false, length = 30)
    private VehicleServiceStatus serviceStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "availability_status", nullable = false, length = 30)
    private VehicleAvailabilityStatus availabilityStatus;

    @Column(name = "odometer_value", nullable = false)
    private long odometerValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "odometer_unit", nullable = false, length = 20)
    private DistanceUnit odometerUnit;

    @Enumerated(EnumType.STRING)
    @Column(name = "odometer_source", nullable = false, length = 40)
    private OdometerSource odometerSource;

    @Column(name = "odometer_recorded_at", nullable = false)
    private Instant odometerRecordedAt;

    @Column(name = "emergency_only", nullable = false)
    private boolean emergencyOnly;

    @Column(name = "allowed_operating_modes", nullable = false, length = 200)
    private String allowedOperatingModes;

    @Column(name = "current_trip_id")
    private UUID currentTripId;

    @Column(name = "created_by", nullable = false, length = 160)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_modified_by", nullable = false, length = 160)
    private String lastModifiedBy;

    @Column(name = "last_modified_at", nullable = false)
    private Instant lastModifiedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_channel", nullable = false, length = 40)
    private SourceChannel sourceChannel;

    @Column(name = "audit_correlation_id", length = 120)
    private String auditCorrelationId;

    protected VehicleEntity() {
    }

    public static VehicleEntity from(Vehicle vehicle) {
        VehicleEntity entity = new VehicleEntity();
        entity.id = vehicle.id();
        entity.applyFrom(vehicle);
        return entity;
    }

    /** Copies aggregate state onto this row, leaving the JPA-managed version alone. */
    public void applyFrom(Vehicle vehicle) {
        this.registrationNumber = vehicle.registrationNumber().value();
        this.vin = vehicle.vin() == null ? null : vehicle.vin().value();
        this.make = vehicle.specification().make();
        this.model = vehicle.specification().model();
        this.manufactureYear = vehicle.specification().manufactureYear();
        this.category = vehicle.specification().category();
        this.capacity = vehicle.specification().capacity();
        this.siteCode = vehicle.siteCode().value();
        this.responsibleUnit = vehicle.responsibleUnit();
        this.operationalOwner = vehicle.operationalOwner();
        this.acquisitionReference = vehicle.acquisitionReference();
        this.lifecycleStatus = vehicle.lifecycleStatus();
        this.serviceStatus = vehicle.serviceStatus();
        this.availabilityStatus = vehicle.availabilityStatus();
        this.odometerValue = vehicle.odometer().value();
        this.odometerUnit = vehicle.odometer().unit();
        this.odometerSource = vehicle.odometer().source();
        this.odometerRecordedAt = vehicle.odometer().recordedAt();
        this.emergencyOnly = vehicle.restrictedUse().emergencyOnly();
        this.allowedOperatingModes = vehicle.restrictedUse().allowedOperatingModes().stream()
                .map(Enum::name)
                .sorted()
                .collect(Collectors.joining(","));
        this.currentTripId = vehicle.currentTripId();
        this.createdBy = vehicle.metadata().createdBy();
        this.createdAt = vehicle.metadata().createdAt();
        this.lastModifiedBy = vehicle.metadata().lastModifiedBy();
        this.lastModifiedAt = vehicle.metadata().lastModifiedAt();
        this.sourceChannel = vehicle.metadata().sourceChannel();
        this.auditCorrelationId = vehicle.metadata().auditCorrelationId();
    }

    public Vehicle toDomain() {
        return new Vehicle(
                id,
                RegistrationNumber.of(registrationNumber),
                VehicleIdentificationNumber.ofNullable(vin),
                new VehicleSpecification(make, model, manufactureYear, category, capacity),
                SiteCode.of(siteCode),
                responsibleUnit,
                operationalOwner,
                acquisitionReference,
                lifecycleStatus,
                serviceStatus,
                availabilityStatus,
                new OdometerReading(odometerValue, odometerUnit, odometerSource, odometerRecordedAt),
                new RestrictedUse(emergencyOnly, parseModes(allowedOperatingModes)),
                currentTripId,
                RecordMetadata.rehydrate(createdBy, createdAt, lastModifiedBy, lastModifiedAt, version,
                        sourceChannel, auditCorrelationId));
    }

    public UUID id() {
        return id;
    }

    public long version() {
        return version;
    }

    private static Set<OperatingMode> parseModes(String raw) {
        if (raw == null || raw.isBlank()) {
            return EnumSet.allOf(OperatingMode.class);
        }
        return Arrays.stream(raw.split(","))
                .map(String::strip)
                .filter(value -> !value.isBlank())
                .map(OperatingMode::valueOf)
                .collect(Collectors.toUnmodifiableSet());
    }
}
