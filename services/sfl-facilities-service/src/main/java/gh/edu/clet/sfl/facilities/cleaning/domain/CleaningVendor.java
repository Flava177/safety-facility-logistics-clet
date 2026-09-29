package gh.edu.clet.sfl.facilities.cleaning.domain;

import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.model.EstateCodes;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A Vendor Master (S133) vendor engaged for cleaning at a site - SRS-SFL-S169-03 "vendor base is drawn
 * from Vendor Master".
 *
 * <p>S169 does not own the vendor. It owns the engagement - the site, the SLA terms, the scorecard -
 * and holds {@code vendorMasterReference} by value as the key back to S133. {@code name} is a copy
 * taken when the vendor was registered, for display; S133 remains the authority for the legal entity.
 */
public record CleaningVendor(
        UUID id,
        String siteCode,
        String vendorMasterReference,
        String name,
        VendorStatus status,
        RecordMetadata metadata) {

    public CleaningVendor {
        Objects.requireNonNull(id, "id is required");
        siteCode = EstateCodes.normalize(siteCode);
        vendorMasterReference = EstateCodes.normalize(vendorMasterReference);
        EstateCodes.require(name, "name");
        name = name.strip();
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(metadata, "metadata is required");
    }

    public static CleaningVendor register(UUID id, String siteCode, String reference, String name, String actorId,
            Instant at, SourceChannel channel, String correlationId) {
        return new CleaningVendor(id, siteCode, reference, name, VendorStatus.ACTIVE,
                RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    public CleaningVendor withStatus(VendorStatus target, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        if (target == null) {
            throw new FacilitiesException.ValidationFailedException("A vendor status is required.");
        }
        if (target == status) {
            throw new FacilitiesException.InvalidStateTransitionException("The vendor is already " + status + ".");
        }
        return new CleaningVendor(id, siteCode, vendorMasterReference, name, target,
                metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    public boolean assignable() {
        return status == VendorStatus.ACTIVE;
    }
}
