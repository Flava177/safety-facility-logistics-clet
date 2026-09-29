package gh.edu.clet.sfl.facilities.construction.domain;

import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.model.EstateCodes;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One thing S164 said about a permit, parsed and validated - the payload contract S176 expects of the
 * reserved {@code sfl.ssemp.permit-*.v1} events. Documented in {@code docs/facilities/S176_Event_Contracts.md}.
 *
 * <p>Nothing publishes these yet. The contract is S176's statement of what it needs, written so the
 * S164 build can satisfy it rather than guess.
 *
 * @param messageId the envelope's message id, kept on the projection for tracing
 * @param occurredAt when S164 says the change happened; orders notices that arrive out of order
 */
public record PermitNotice(
        Kind kind,
        String eventType,
        UUID messageId,
        String permitId,
        String permitReference,
        String siteCode,
        String workType,
        Instant validFrom,
        Instant validTo,
        String contractorReference,
        String originReference,
        Instant occurredAt) {

    public enum Kind {
        ISSUED,
        SUSPENDED,
        EXTENDED,
        CLOSED
    }

    /**
     * Schema validation. A notice that fails it is logged and dropped by the handler, never applied -
     * an "issued" with no validity window cannot be current, and applying it half would be a guess.
     */
    public PermitNotice {
        Objects.requireNonNull(kind, "kind is required");
        EstateCodes.require(eventType, "eventType");
        if (permitId == null || permitId.isBlank()) {
            throw new FacilitiesException.ValidationFailedException("A permit notice must carry permitId.");
        }
        permitId = permitId.strip();
        if (siteCode == null || siteCode.isBlank()) {
            throw new FacilitiesException.ValidationFailedException("A permit notice must carry siteCode.");
        }
        siteCode = EstateCodes.normalize(siteCode);
        workType = workType == null || workType.isBlank() ? null : EstateCodes.normalize(workType);
        permitReference = EstateCodes.blankToNull(permitReference);
        contractorReference = EstateCodes.blankToNull(contractorReference);
        originReference = EstateCodes.blankToNull(originReference);
        if (occurredAt == null) {
            throw new FacilitiesException.ValidationFailedException("A permit notice must carry occurredAt.");
        }
        if (kind == Kind.ISSUED) {
            if (workType == null || validFrom == null || validTo == null) {
                throw new FacilitiesException.ValidationFailedException(
                        "A permit-issued notice must carry workType, validFrom and validTo.");
            }
        }
        if (kind == Kind.EXTENDED && validTo == null) {
            throw new FacilitiesException.ValidationFailedException("A permit-extended notice must carry validTo.");
        }
        if (validFrom != null && validTo != null && !validTo.isAfter(validFrom)) {
            throw new FacilitiesException.ValidationFailedException("A permit's validTo must be after validFrom.");
        }
    }
}
