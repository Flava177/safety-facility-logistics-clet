package gh.edu.clet.sfl.facilities.masterdata.domain;

import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordLifecycleStatus;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Fixed plant or equipment attached to the estate (SRS-SFL-S152-01, "facility asset").
 *
 * <p>§21.1 of the SRS makes this the parent of work orders, preventive schedules and closure
 * evidence: "One asset may have many faults, work orders, schedules and closure evidence records."
 * It is therefore the single highest-leverage record in this build — S153 cannot be built properly
 * without it, and every maintenance question ("what broke", "what is due", "what is critical here")
 * is a query over it.
 *
 * <p><strong>Not the same thing as AVAMP-Lite.</strong> {@code sfl-asset-visibility-service} owns
 * cross-programme asset and device <em>reference identity</em> — the thing a vehicle, a camera and a
 * laptop all have. This owns the chiller bolted to a plant room that maintenance is raised against.
 * The two are linked by {@code assetReferenceId}, held as a plain value: no foreign key, no read of
 * another service's schema.
 *
 * <p>Likewise distinct from {@link DeviceReference}: a device is an integration endpoint a vendor
 * reports on; an asset is plant we maintain. A fire panel is both, which is why an asset may carry a
 * {@code deviceReferenceId}.
 */
public record FacilityAsset(
        UUID id,
        String siteCode,
        String assetCode,
        String name,
        AssetCategory category,
        AssetCriticality criticality,
        AssetOperationalStatus operationalStatus,
        UUID roomId,
        String locationCode,
        String manufacturer,
        String modelNumber,
        String serialNumber,
        LocalDate installedOn,
        LocalDate warrantyExpiresOn,
        Integer serviceIntervalDays,
        LocalDate lastServicedOn,
        String custodian,
        UUID deviceReferenceId,
        UUID assetReferenceId,
        String statusNotes,
        Instant statusChangedAt,
        RecordLifecycleStatus lifecycleStatus,
        RecordMetadata metadata) {

    public FacilityAsset {
        Objects.requireNonNull(id, "id is required");
        Site.requireText(siteCode, "siteCode");
        Site.requireText(assetCode, "assetCode");
        Site.requireText(name, "name");
        Objects.requireNonNull(category, "category is required");
        Objects.requireNonNull(criticality, "criticality is required");
        Objects.requireNonNull(operationalStatus, "operationalStatus is required");
        Objects.requireNonNull(lifecycleStatus, "lifecycleStatus is required");
        Objects.requireNonNull(metadata, "metadata is required");
        if (serviceIntervalDays != null && serviceIntervalDays <= 0) {
            throw new IllegalArgumentException("serviceIntervalDays must be positive");
        }
        if (installedOn != null && warrantyExpiresOn != null && warrantyExpiresOn.isBefore(installedOn)) {
            throw new IllegalArgumentException("warrantyExpiresOn cannot precede installedOn");
        }
    }

    /** Registers an asset as {@link AssetOperationalStatus#OPERATIONAL}. */
    public static FacilityAsset register(UUID id, String siteCode, String assetCode, String name,
            AssetCategory category, AssetCriticality criticality, UUID roomId, String locationCode,
            String manufacturer, String modelNumber, String serialNumber, LocalDate installedOn,
            LocalDate warrantyExpiresOn, Integer serviceIntervalDays, String custodian, UUID deviceReferenceId,
            UUID assetReferenceId, String actorId, Instant at, SourceChannel channel, String correlationId) {
        return new FacilityAsset(id, Site.normalizeCode(siteCode), Site.normalizeCode(assetCode), name.strip(),
                category, criticality == null ? AssetCriticality.MEDIUM : criticality,
                AssetOperationalStatus.OPERATIONAL, roomId, Site.blankToNull(locationCode),
                Site.blankToNull(manufacturer), Site.blankToNull(modelNumber), Site.blankToNull(serialNumber),
                installedOn, warrantyExpiresOn, serviceIntervalDays, null, Site.blankToNull(custodian),
                deviceReferenceId, assetReferenceId, null, at, RecordLifecycleStatus.ACTIVE,
                RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    /** A null or blank field leaves the current value alone — this is a PATCH, not a replace. */
    public FacilityAsset update(String name, AssetCategory category, AssetCriticality criticality,
            String manufacturer, String modelNumber, String serialNumber, LocalDate warrantyExpiresOn,
            Integer serviceIntervalDays, String custodian, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        return new FacilityAsset(id, siteCode, assetCode,
                name == null || name.isBlank() ? this.name : name.strip(),
                category == null ? this.category : category,
                criticality == null ? this.criticality : criticality,
                operationalStatus, roomId, locationCode,
                manufacturer == null ? this.manufacturer : Site.blankToNull(manufacturer),
                modelNumber == null ? this.modelNumber : Site.blankToNull(modelNumber),
                serialNumber == null ? this.serialNumber : Site.blankToNull(serialNumber),
                installedOn,
                warrantyExpiresOn == null ? this.warrantyExpiresOn : warrantyExpiresOn,
                serviceIntervalDays == null ? this.serviceIntervalDays : serviceIntervalDays,
                lastServicedOn,
                custodian == null ? this.custodian : Site.blankToNull(custodian),
                deviceReferenceId, assetReferenceId, statusNotes, statusChangedAt, lifecycleStatus,
                metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    /**
     * Records that the asset's condition changed.
     *
     * <p>This is the event readiness listens to. Setting a critical asset to
     * {@link AssetOperationalStatus#OUT_OF_SERVICE} is what blocks the spaces it serves, so the
     * transition is audited and timestamped rather than being a silent column write.
     */
    public FacilityAsset changeOperationalStatus(AssetOperationalStatus target, String notes, String actorId,
            Instant at, SourceChannel channel, String correlationId) {
        Objects.requireNonNull(target, "operationalStatus is required");
        if (lifecycleStatus == RecordLifecycleStatus.ARCHIVED) {
            throw new gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException
                    .InvalidStateTransitionException("Archived asset " + assetCode + " cannot change status.");
        }
        return new FacilityAsset(id, siteCode, assetCode, name, category, criticality, target, roomId,
                locationCode, manufacturer, modelNumber, serialNumber, installedOn, warrantyExpiresOn,
                serviceIntervalDays, lastServicedOn, custodian, deviceReferenceId, assetReferenceId,
                Site.blankToNull(notes), at, lifecycleStatus,
                metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    /** Moves the asset to another space within the same site. */
    public FacilityAsset relocate(UUID newRoomId, String newLocationCode, String actorId, Instant at,
            SourceChannel channel, String correlationId) {
        return new FacilityAsset(id, siteCode, assetCode, name, category, criticality, operationalStatus,
                newRoomId, newLocationCode == null ? locationCode : Site.blankToNull(newLocationCode),
                manufacturer, modelNumber, serialNumber, installedOn, warrantyExpiresOn, serviceIntervalDays,
                lastServicedOn, custodian, deviceReferenceId, assetReferenceId, statusNotes, statusChangedAt,
                lifecycleStatus, metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    /** Records a completed service, which is what {@link #serviceDueOn()} counts from. */
    public FacilityAsset recordService(LocalDate servicedOn, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        Objects.requireNonNull(servicedOn, "servicedOn is required");
        return new FacilityAsset(id, siteCode, assetCode, name, category, criticality, operationalStatus, roomId,
                locationCode, manufacturer, modelNumber, serialNumber, installedOn, warrantyExpiresOn,
                serviceIntervalDays, servicedOn, custodian, deviceReferenceId, assetReferenceId, statusNotes,
                statusChangedAt, lifecycleStatus, metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    public FacilityAsset changeLifecycle(RecordLifecycleStatus target, String actorId, Instant at,
            SourceChannel channel, String correlationId) {
        return new FacilityAsset(id, siteCode, assetCode, name, category, criticality, operationalStatus, roomId,
                locationCode, manufacturer, modelNumber, serialNumber, installedOn, warrantyExpiresOn,
                serviceIntervalDays, lastServicedOn, custodian, deviceReferenceId, assetReferenceId, statusNotes,
                statusChangedAt, lifecycleStatus.transitionTo(target, "Asset"),
                metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    /**
     * When this asset is next due for service, or {@code null} when it is not on a schedule.
     *
     * <p>Counts from the last service where there is one, and from installation otherwise — an asset
     * installed and never serviced is due, and treating "never serviced" as "not due" is how a
     * generator goes three years without a look.
     */
    public LocalDate serviceDueOn() {
        if (serviceIntervalDays == null) {
            return null;
        }
        LocalDate from = lastServicedOn != null ? lastServicedOn : installedOn;
        return from == null ? null : from.plusDays(serviceIntervalDays);
    }

    /** {@code true} when the service due date has passed on {@code today}. */
    public boolean serviceOverdueOn(LocalDate today) {
        LocalDate due = serviceDueOn();
        return due != null && today != null && today.isAfter(due);
    }

    /** {@code true} when this asset should raise a readiness blocker for the space it sits in. */
    public boolean impairsReadiness() {
        return lifecycleStatus.isOperational() && operationalStatus.impairsReadiness();
    }

    /** Creation time, for the API shape used across the estate records. */
    public Instant createdAt() {
        return metadata.createdAt();
    }
}
