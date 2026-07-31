package gh.edu.clet.sfl.facilities.maintenance.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.maintenance.domain.MaintenanceVendor;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordLifecycleStatus;
import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.RecordMetadataEmbeddable;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

/** JPA mapping for {@link MaintenanceVendor}. */
@Entity
@Table(name = "maintenance_vendors", schema = "facilities")
public class MaintenanceVendorRecord {

    @Id
    private UUID id;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Column(name = "vendor_code", nullable = false, length = 80)
    private String vendorCode;
    @Column(nullable = false, length = 200)
    private String name;
    @Column(length = 200)
    private String specialisation;
    @Column(name = "contact_name", length = 200)
    private String contactName;
    @Column(name = "contact_email", length = 200)
    private String contactEmail;
    @Column(name = "contact_phone", length = 60)
    private String contactPhone;
    @Column(name = "response_hours")
    private Integer responseHours;
    @Column(name = "contract_reference", length = 120)
    private String contractReference;
    @Column(name = "contract_expires_on")
    private LocalDate contractExpiresOn;
    @Column(name = "external_vendor_id", length = 120)
    private String externalVendorId;
    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false, length = 20)
    private RecordLifecycleStatus lifecycleStatus;
    @Embedded
    private RecordMetadataEmbeddable metadata;

    protected MaintenanceVendorRecord() {
    }

    public static MaintenanceVendorRecord from(MaintenanceVendor vendor) {
        MaintenanceVendorRecord record = new MaintenanceVendorRecord();
        record.apply(vendor);
        return record;
    }

    public void apply(MaintenanceVendor vendor) {
        id = vendor.id();
        siteCode = vendor.siteCode();
        vendorCode = vendor.vendorCode();
        name = vendor.name();
        specialisation = vendor.specialisation();
        contactName = vendor.contactName();
        contactEmail = vendor.contactEmail();
        contactPhone = vendor.contactPhone();
        responseHours = vendor.responseHours();
        contractReference = vendor.contractReference();
        contractExpiresOn = vendor.contractExpiresOn();
        externalVendorId = vendor.externalVendorId();
        lifecycleStatus = vendor.lifecycleStatus();
        metadata = RecordMetadataEmbeddable.from(vendor.metadata());
    }

    public MaintenanceVendor toDomain() {
        return new MaintenanceVendor(id, siteCode, vendorCode, name, specialisation, contactName, contactEmail,
                contactPhone, responseHours, contractReference, contractExpiresOn, externalVendorId,
                lifecycleStatus, metadata.toDomain());
    }
}
