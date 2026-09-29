package gh.edu.clet.sfl.facilities.cleaning.domain;

import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.model.EstateCodes;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One version of a vendor's contracted SLA terms - SRS-SFL-S169-03 "response time, completion time,
 * quality-rating floor".
 *
 * <p>Versioned, never edited: a task is judged against the version in force when it was raised
 * ({@link #inForceAt}), so renegotiating the contract in March does not retrospectively clear
 * February's breaches or invent new ones.
 */
public record VendorSlaTerms(
        UUID id,
        String siteCode,
        UUID vendorId,
        int version,
        int responseMinutes,
        int completionMinutes,
        BigDecimal qualityFloor,
        Instant effectiveFrom,
        Instant effectiveTo,
        RecordMetadata metadata) {

    public VendorSlaTerms {
        Objects.requireNonNull(id, "id is required");
        siteCode = EstateCodes.normalize(siteCode);
        Objects.requireNonNull(vendorId, "vendorId is required");
        if (version < 1) {
            throw new IllegalArgumentException("version starts at 1");
        }
        if (responseMinutes <= 0 || completionMinutes <= 0) {
            throw new FacilitiesException.ValidationFailedException(
                    "Contracted response and completion times must be positive.");
        }
        if (qualityFloor == null || qualityFloor.compareTo(BigDecimal.ONE) < 0
                || qualityFloor.compareTo(BigDecimal.valueOf(5)) > 0) {
            throw new FacilitiesException.ValidationFailedException("The quality-rating floor is between 1 and 5.");
        }
        Objects.requireNonNull(effectiveFrom, "effectiveFrom is required");
        Objects.requireNonNull(metadata, "metadata is required");
    }

    public static VendorSlaTerms create(UUID id, String siteCode, UUID vendorId, int version, int responseMinutes,
            int completionMinutes, BigDecimal qualityFloor, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        return new VendorSlaTerms(id, siteCode, vendorId, version, responseMinutes, completionMinutes, qualityFloor,
                at, null, RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    /** Superseded by a newer version at {@code at}. */
    public VendorSlaTerms close(String actorId, Instant at, SourceChannel channel, String correlationId) {
        return new VendorSlaTerms(id, siteCode, vendorId, version, responseMinutes, completionMinutes, qualityFloor,
                effectiveFrom, at, metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    /** Half-open: in force from {@code effectiveFrom} up to, not including, {@code effectiveTo}. */
    public boolean inForceAt(Instant instant) {
        return !instant.isBefore(effectiveFrom) && (effectiveTo == null || instant.isBefore(effectiveTo));
    }

    public Duration responseTime() {
        return Duration.ofMinutes(responseMinutes);
    }

    public Duration completionTime() {
        return Duration.ofMinutes(completionMinutes);
    }
}
