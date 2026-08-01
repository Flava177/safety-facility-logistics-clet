package gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.persistence;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.DriverEligibilityStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.DriverLifecycleStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.DriverProfileReference;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.LicenceClass;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.LicenceDetails;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.RecordMetadata;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Persistence image of {@link DriverProfileReference}. */
@Entity
@Table(name = "driver_profile_references", schema = "fleet_logistics")
public class DriverProfileEntity {

    @Id
    private UUID id;

    @Column(name = "staff_reference", nullable = false, length = 80)
    private String staffReference;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Column(name = "licence_number", nullable = false, length = 80)
    private String licenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "licence_class", nullable = false, length = 10)
    private LicenceClass licenceClass;

    @Column(name = "licence_expires_on", nullable = false)
    private LocalDate licenceExpiresOn;

    @Column(name = "medical_clearance_expires_on")
    private LocalDate medicalClearanceExpiresOn;

    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;

    @Column(name = "responsible_unit", nullable = false, length = 160)
    private String responsibleUnit;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false, length = 30)
    private DriverLifecycleStatus lifecycleStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "eligibility_status", nullable = false, length = 30)
    private DriverEligibilityStatus eligibilityStatus;

    @Column(name = "suspension_reason", length = 1000)
    private String suspensionReason;

    /** The identity provider subject that signs in as this driver. Null means nobody does. */
    @Column(name = "principal_subject", length = 160)
    private String principalSubject;

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

    protected DriverProfileEntity() {
    }

    public static DriverProfileEntity from(DriverProfileReference driver) {
        DriverProfileEntity entity = new DriverProfileEntity();
        entity.id = driver.id();
        entity.applyFrom(driver);
        return entity;
    }

    public void applyFrom(DriverProfileReference driver) {
        this.staffReference = driver.staffReference();
        this.displayName = driver.displayName();
        this.licenceNumber = driver.licence().number();
        this.licenceClass = driver.licence().licenceClass();
        this.licenceExpiresOn = driver.licence().expiresOn();
        this.medicalClearanceExpiresOn = driver.medicalClearanceExpiresOn();
        this.siteCode = driver.siteCode().value();
        this.responsibleUnit = driver.responsibleUnit();
        this.lifecycleStatus = driver.lifecycleStatus();
        this.eligibilityStatus = driver.eligibilityStatus();
        this.suspensionReason = driver.suspensionReason();
        this.principalSubject = driver.principalSubject();
        this.createdBy = driver.metadata().createdBy();
        this.createdAt = driver.metadata().createdAt();
        this.lastModifiedBy = driver.metadata().lastModifiedBy();
        this.lastModifiedAt = driver.metadata().lastModifiedAt();
        this.sourceChannel = driver.metadata().sourceChannel();
        this.auditCorrelationId = driver.metadata().auditCorrelationId();
    }

    public DriverProfileReference toDomain() {
        return new DriverProfileReference(id, staffReference, displayName,
                new LicenceDetails(licenceNumber, licenceClass, licenceExpiresOn), medicalClearanceExpiresOn,
                SiteCode.of(siteCode), responsibleUnit, lifecycleStatus, eligibilityStatus, suspensionReason,
                principalSubject,
                RecordMetadata.rehydrate(createdBy, createdAt, lastModifiedBy, lastModifiedAt, version,
                        sourceChannel, auditCorrelationId));
    }
}
