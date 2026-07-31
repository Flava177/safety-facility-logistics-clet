package gh.edu.clet.sfl.facilities.maintenance.domain;

import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.model.EstateCodes;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordLifecycleStatus;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * A maintenance contractor CLET assigns work to.
 *
 * <p>SRS-SFL-S153-01 names "vendor assignment" as an operational record and S153-04 lists
 * "procurement/vendor master" among the systems this module integrates with. This is deliberately
 * <strong>not</strong> that master. It is a local reference — enough to assign work, track the
 * contracted response time and see whether the contract has expired — carrying
 * {@link #externalVendorId} as the procurement system's identifier for the same company.
 *
 * <p>That is the same shape S152 uses for AVAMP asset references: a value, not a foreign key, and
 * never a claim to own the record. When procurement is integrated, this becomes a projection of it
 * rather than something to reconcile against.
 *
 * @param responseHours the contracted hours to respond, used as the SLA when work is assigned to
 *        this vendor and it is tighter than the priority's own rule.
 */
public record MaintenanceVendor(
        UUID id,
        String siteCode,
        String vendorCode,
        String name,
        String specialisation,
        String contactName,
        String contactEmail,
        String contactPhone,
        Integer responseHours,
        String contractReference,
        LocalDate contractExpiresOn,
        String externalVendorId,
        RecordLifecycleStatus lifecycleStatus,
        RecordMetadata metadata) {

    public MaintenanceVendor {
        Objects.requireNonNull(id, "id is required");
        siteCode = EstateCodes.normalize(siteCode);
        vendorCode = EstateCodes.normalize(vendorCode);
        EstateCodes.require(name, "name");
        name = name.strip();
        specialisation = EstateCodes.blankToNull(specialisation);
        contactName = EstateCodes.blankToNull(contactName);
        contactEmail = EstateCodes.blankToNull(contactEmail);
        contactPhone = EstateCodes.blankToNull(contactPhone);
        contractReference = EstateCodes.blankToNull(contractReference);
        externalVendorId = EstateCodes.blankToNull(externalVendorId);
        Objects.requireNonNull(lifecycleStatus, "lifecycleStatus is required");
        Objects.requireNonNull(metadata, "metadata is required");
        if (responseHours != null && responseHours <= 0) {
            throw new IllegalArgumentException("responseHours must be greater than zero");
        }
    }

    public static MaintenanceVendor register(UUID id, String siteCode, String vendorCode, String name,
            String specialisation, String contactName, String contactEmail, String contactPhone,
            Integer responseHours, String contractReference, LocalDate contractExpiresOn,
            String externalVendorId, String actorId, Instant at, SourceChannel channel, String correlationId) {
        return new MaintenanceVendor(id, siteCode, vendorCode, name, specialisation, contactName, contactEmail,
                contactPhone, responseHours, contractReference, contractExpiresOn, externalVendorId,
                RecordLifecycleStatus.ACTIVE, RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    public MaintenanceVendor update(String newName, String newSpecialisation, String newContactName,
            String newContactEmail, String newContactPhone, Integer newResponseHours,
            String newContractReference, LocalDate newContractExpiresOn, String actorId, Instant at,
            SourceChannel channel, String correlationId) {
        return new MaintenanceVendor(id, siteCode, vendorCode,
                newName == null || newName.isBlank() ? name : newName,
                newSpecialisation == null ? specialisation : newSpecialisation,
                newContactName == null ? contactName : newContactName,
                newContactEmail == null ? contactEmail : newContactEmail,
                newContactPhone == null ? contactPhone : newContactPhone,
                newResponseHours == null ? responseHours : newResponseHours,
                newContractReference == null ? contractReference : newContractReference,
                newContractExpiresOn == null ? contractExpiresOn : newContractExpiresOn,
                externalVendorId, lifecycleStatus, metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    public MaintenanceVendor changeLifecycle(RecordLifecycleStatus target, String actorId, Instant at,
            SourceChannel channel, String correlationId) {
        RecordLifecycleStatus next = lifecycleStatus.transitionTo(target, "Maintenance vendor");
        return new MaintenanceVendor(id, siteCode, vendorCode, name, specialisation, contactName, contactEmail,
                contactPhone, responseHours, contractReference, contractExpiresOn, externalVendorId, next,
                metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    /** {@code true} when work may be assigned to this vendor today. */
    public boolean isAssignable(LocalDate today) {
        return lifecycleStatus.isOperational()
                && (contractExpiresOn == null || !contractExpiresOn.isBefore(today));
    }

    /** Why this vendor cannot take work, or {@code null} when it can. */
    public String unassignableReason(LocalDate today) {
        if (!lifecycleStatus.isOperational()) {
            return "Vendor " + vendorCode + " is " + lifecycleStatus + ".";
        }
        if (contractExpiresOn != null && contractExpiresOn.isBefore(today)) {
            return "The contract for vendor " + vendorCode + " expired on " + contractExpiresOn + ".";
        }
        return null;
    }
}
