package gh.edu.clet.sfl.facilities.construction.domain;

import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.model.EstateCodes;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * A competency certification a contractor is required to hold - SRS-SFL-S176-02.
 *
 * <p>Every certification recorded against a contractor is treated as required: the record exists
 * because somebody at the site's HSE unit decided this contractor must hold it. One row per
 * certification code; a renewal updates the expiry in place and is audited, rather than leaving an
 * expired row beside a current one for a reader to reconcile.
 *
 * <p>The S140 HRMS is named by the SRS as the source of contractor competency records and is not
 * integrated; these are entered by hand (see the S176 gap report).
 */
public record ContractorCompetency(
        UUID id,
        UUID contractorId,
        String siteCode,
        String certificationCode,
        String description,
        String certificateReference,
        LocalDate expiresOn,
        RecordMetadata metadata) {

    public ContractorCompetency {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(contractorId, "contractorId is required");
        siteCode = EstateCodes.normalize(siteCode);
        certificationCode = EstateCodes.normalize(certificationCode);
        description = EstateCodes.blankToNull(description);
        certificateReference = EstateCodes.blankToNull(certificateReference);
        Objects.requireNonNull(expiresOn, "expiresOn is required");
        Objects.requireNonNull(metadata, "metadata is required");
    }

    public static ContractorCompetency record(UUID id, Contractor contractor, String code, String description,
            String certificateReference, LocalDate expiresOn, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        return new ContractorCompetency(id, contractor.id(), contractor.siteCode(), code, description,
                certificateReference, expiresOn, RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    public ContractorCompetency renew(String newDescription, String newReference, LocalDate newExpiry, String actorId,
            Instant at, SourceChannel channel, String correlationId) {
        return new ContractorCompetency(id, contractorId, siteCode, certificationCode,
                newDescription == null ? description : newDescription,
                newReference == null ? certificateReference : newReference, newExpiry,
                metadata.modifiedBy(actorId, at, channel, correlationId));
    }
}
