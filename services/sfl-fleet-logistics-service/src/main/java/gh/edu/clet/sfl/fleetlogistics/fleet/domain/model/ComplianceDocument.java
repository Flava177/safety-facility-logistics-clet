package gh.edu.clet.sfl.fleetlogistics.fleet.domain.model;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.InvalidStateTransitionException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * A vehicle compliance document (SRS-SFL-S166-01 "compliance documents", SRS-SFL-S166-05 "expired or
 * expiring compliance").
 *
 * <p>Validity is held as {@link LocalDate} because a roadworthiness certificate expires on a calendar
 * day in the operating jurisdiction, not at an instant. The comparison uses the Ghana operating zone
 * ({@code Africa/Accra}) so a certificate does not appear to expire a day early or late.
 */
public record ComplianceDocument(
        UUID id,
        UUID vehicleId,
        SiteCode siteCode,
        ComplianceDocumentType documentType,
        String documentReference,
        String issuingAuthority,
        LocalDate issuedOn,
        LocalDate expiresOn,
        ComplianceDocumentStatus status,
        UUID evidenceId,
        RetentionClass retentionClass,
        RecordMetadata metadata) {

    /** Ghana operates on UTC+00:00 with no daylight saving; expiry is judged in local calendar days. */
    public static final ZoneId OPERATING_ZONE = ZoneId.of("Africa/Accra");

    public ComplianceDocument {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(vehicleId, "vehicleId is required");
        Objects.requireNonNull(siteCode, "siteCode is required");
        Objects.requireNonNull(documentType, "documentType is required");
        Objects.requireNonNull(issuedOn, "issuedOn is required");
        Objects.requireNonNull(expiresOn, "expiresOn is required");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(retentionClass, "retentionClass is required");
        Objects.requireNonNull(metadata, "metadata is required");
        documentReference = requireText(documentReference, "documentReference");
        issuingAuthority = requireText(issuingAuthority, "issuingAuthority");
        if (expiresOn.isBefore(issuedOn)) {
            throw new IllegalArgumentException("expiresOn cannot precede issuedOn");
        }
    }

    public static ComplianceDocument register(UUID id, UUID vehicleId, SiteCode siteCode,
            ComplianceDocumentType documentType, String documentReference, String issuingAuthority,
            LocalDate issuedOn, LocalDate expiresOn, UUID evidenceId, RetentionClass retentionClass,
            Instant now, java.time.Duration warningWindow, RecordMetadata metadata) {
        ComplianceDocumentStatus status = statusFor(expiresOn, now, warningWindow);
        return new ComplianceDocument(id, vehicleId, siteCode, documentType, documentReference, issuingAuthority,
                issuedOn, expiresOn, status, evidenceId, retentionClass, metadata);
    }

    /**
     * Re-evaluates the date-driven status. Documents that were superseded or revoked keep their status:
     * those are decisions, not observations, and a date sweep must not undo them.
     */
    public ComplianceDocument reassessAt(Instant now, java.time.Duration warningWindow, RecordMetadata newMetadata) {
        if (status == ComplianceDocumentStatus.SUPERSEDED || status == ComplianceDocumentStatus.REVOKED) {
            return this;
        }
        ComplianceDocumentStatus reassessed = statusFor(expiresOn, now, warningWindow);
        return reassessed == status ? this : withStatus(reassessed, newMetadata);
    }

    /** Marks the document replaced by a newer one of the same type. */
    public ComplianceDocument supersede(RecordMetadata newMetadata) {
        requireCurrent(ComplianceDocumentStatus.SUPERSEDED);
        return withStatus(ComplianceDocumentStatus.SUPERSEDED, newMetadata);
    }

    /** Withdraws the document by an authorised action. */
    public ComplianceDocument revoke(RecordMetadata newMetadata) {
        requireCurrent(ComplianceDocumentStatus.REVOKED);
        return withStatus(ComplianceDocumentStatus.REVOKED, newMetadata);
    }

    /**
     * True when the document no longer provides cover on {@code at}.
     *
     * <p>A certificate that expires today is still valid today, so expiry is strictly "before today".
     */
    public boolean isExpiredAt(Instant at) {
        return expiresOn.isBefore(LocalDate.ofInstant(at, OPERATING_ZONE));
    }

    /** Days remaining before expiry at {@code at}; negative once expired. */
    public long daysUntilExpiry(Instant at) {
        return ChronoUnit.DAYS.between(LocalDate.ofInstant(at, OPERATING_ZONE), expiresOn);
    }

    private void requireCurrent(ComplianceDocumentStatus target) {
        if (status == ComplianceDocumentStatus.SUPERSEDED || status == ComplianceDocumentStatus.REVOKED) {
            throw InvalidStateTransitionException.of("ComplianceDocument", status, target);
        }
    }

    private ComplianceDocument withStatus(ComplianceDocumentStatus newStatus, RecordMetadata newMetadata) {
        return new ComplianceDocument(id, vehicleId, siteCode, documentType, documentReference, issuingAuthority,
                issuedOn, expiresOn, newStatus, evidenceId, retentionClass, newMetadata);
    }

    private static ComplianceDocumentStatus statusFor(LocalDate expiresOn, Instant now,
            java.time.Duration warningWindow) {
        LocalDate today = LocalDate.ofInstant(now, OPERATING_ZONE);
        if (expiresOn.isBefore(today)) {
            return ComplianceDocumentStatus.EXPIRED;
        }
        long daysRemaining = ChronoUnit.DAYS.between(today, expiresOn);
        return daysRemaining <= warningWindow.toDays()
                ? ComplianceDocumentStatus.EXPIRING
                : ComplianceDocumentStatus.ACTIVE;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        String stripped = value.strip();
        if (stripped.length() > 160) {
            throw new IllegalArgumentException(field + " cannot exceed 160 characters");
        }
        return stripped;
    }

    /** Machine-readable context used when this document blocks readiness. */
    public Map<String, Object> blockerContext() {
        return Map.of(
                "documentId", id.toString(),
                "documentType", documentType.name(),
                "expiresOn", expiresOn.toString());
    }
}
