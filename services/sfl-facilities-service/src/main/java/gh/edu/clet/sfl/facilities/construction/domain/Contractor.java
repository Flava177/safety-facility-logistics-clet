package gh.edu.clet.sfl.facilities.construction.domain;

import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.model.EstateCodes;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordLifecycleStatus;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * A contractor performing works on a CLET site - SRS-SFL-S176-02.
 *
 * <p>"Contractor records carry insurance expiry, required competency certifications and the permits
 * currently authorising their presence." Insurance is here; certifications are
 * {@link ContractorCompetency} records, one per certificate, each with its own expiry; permits are
 * read from the S164 projection at the moment they are asked about, never copied here, so a permit
 * S164 suspends stops authorising the contractor the moment its event arrives.
 *
 * <p>{@code vendorReference} is the Vendor Master (S133) identity, held by value and unverified - S133
 * is not integrated (see the S176 gap report). {@code insuranceExpiresOn} is the last day the cover
 * is in force.
 */
public record Contractor(
        UUID id,
        String siteCode,
        String contractorCode,
        String name,
        String vendorReference,
        String insuranceProvider,
        String insurancePolicyReference,
        LocalDate insuranceExpiresOn,
        RecordLifecycleStatus lifecycleStatus,
        RecordMetadata metadata) {

    public Contractor {
        Objects.requireNonNull(id, "id is required");
        siteCode = EstateCodes.normalize(siteCode);
        contractorCode = EstateCodes.normalize(contractorCode);
        EstateCodes.require(name, "name");
        name = name.strip();
        vendorReference = EstateCodes.blankToNull(vendorReference);
        insuranceProvider = EstateCodes.blankToNull(insuranceProvider);
        insurancePolicyReference = EstateCodes.blankToNull(insurancePolicyReference);
        Objects.requireNonNull(insuranceExpiresOn, "insuranceExpiresOn is required");
        Objects.requireNonNull(lifecycleStatus, "lifecycleStatus is required");
        Objects.requireNonNull(metadata, "metadata is required");
    }

    public static Contractor register(UUID id, String siteCode, String code, String name, String vendorReference,
            String insuranceProvider, String policyReference, LocalDate insuranceExpiresOn, String actorId,
            Instant at, SourceChannel channel, String correlationId) {
        return new Contractor(id, siteCode, code, name, vendorReference, insuranceProvider, policyReference,
                insuranceExpiresOn, RecordLifecycleStatus.ACTIVE,
                RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    /** New cover, or a correction. Does not revive a suspended grant - see {@code SiteAccessGrant}. */
    public Contractor updateInsurance(String provider, String policyReference, LocalDate expiresOn, String actorId,
            Instant at, SourceChannel channel, String correlationId) {
        Objects.requireNonNull(expiresOn, "insuranceExpiresOn is required");
        return new Contractor(id, siteCode, contractorCode, name, vendorReference,
                provider == null ? insuranceProvider : provider, policyReference == null
                        ? insurancePolicyReference : policyReference,
                expiresOn, lifecycleStatus, metadata.modifiedBy(actorId, at, channel, correlationId));
    }
}
