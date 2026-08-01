package gh.edu.clet.sfl.fleetlogistics.fleet.domain.model;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.InvalidStateTransitionException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.policy.DriverLifecyclePolicy;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * A driver as the fleet needs to know them (SRS-SFL-S166-01 "drivers or approved driver profile
 * references").
 *
 * <p>This is a <em>reference</em>, not a personnel record: HRMS owns the person, and this aggregate
 * holds only what fleet operations must decide on — the staff reference, the licence, medical
 * clearance, site scope and eligibility. Keeping it a reference is what stops SFL becoming a second,
 * diverging copy of the HR system (SRS-SFL-S166-04 HRMS integration).
 */
public record DriverProfileReference(
        UUID id,
        String staffReference,
        String displayName,
        LicenceDetails licence,
        LocalDate medicalClearanceExpiresOn,
        SiteCode siteCode,
        String responsibleUnit,
        DriverLifecycleStatus lifecycleStatus,
        DriverEligibilityStatus eligibilityStatus,
        String suspensionReason,
        String principalSubject,
        RecordMetadata metadata) {

    public DriverProfileReference {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(licence, "licence is required");
        Objects.requireNonNull(siteCode, "siteCode is required");
        Objects.requireNonNull(lifecycleStatus, "lifecycleStatus is required");
        Objects.requireNonNull(eligibilityStatus, "eligibilityStatus is required");
        Objects.requireNonNull(metadata, "metadata is required");
        staffReference = requireText(staffReference, "staffReference", 80).toUpperCase(Locale.ROOT);
        displayName = requireText(displayName, "displayName", 200);
        responsibleUnit = requireText(responsibleUnit, "responsibleUnit", 160);
        suspensionReason = suspensionReason == null || suspensionReason.isBlank()
                ? null
                : suspensionReason.strip();
        // Not upper-cased, unlike staffReference. A subject claim is opaque and case-sensitive: Keycloak
        // issues a lowercase UUID and folding it would break the match against the token it came from.
        principalSubject = principalSubject == null || principalSubject.isBlank()
                ? null
                : requireText(principalSubject, "principalSubject", 160);
    }

    public static DriverProfileReference register(UUID id, String staffReference, String displayName,
            LicenceDetails licence, LocalDate medicalClearanceExpiresOn, SiteCode siteCode, String responsibleUnit,
            String principalSubject, RecordMetadata metadata) {
        return new DriverProfileReference(id, staffReference, displayName, licence, medicalClearanceExpiresOn,
                siteCode, responsibleUnit, DriverLifecycleStatus.ACTIVE, DriverEligibilityStatus.ELIGIBLE, null,
                principalSubject, metadata);
    }

    /**
     * Binds — or unbinds, with {@code null} — the identity that signs in as this driver.
     *
     * <p>Separate from {@link #updateDetails} because it is a different act with a different authority:
     * correcting a licence class is fleet administration, while deciding whose sign-in maps to this
     * profile determines which trips a person can see. The two are audited as different events and
     * gated on different permissions.
     */
    public DriverProfileReference bindPrincipal(String newPrincipalSubject, RecordMetadata newMetadata) {
        DriverLifecyclePolicy.requireEditable(lifecycleStatus);
        return new DriverProfileReference(id, staffReference, displayName, licence, medicalClearanceExpiresOn,
                siteCode, responsibleUnit, lifecycleStatus, eligibilityStatus, suspensionReason,
                newPrincipalSubject, newMetadata);
    }

    /** Whether anybody signs in as this driver. An unbound profile is assignable but sees nothing. */
    public boolean isBound() {
        return principalSubject != null;
    }

    public DriverProfileReference updateDetails(String newDisplayName, LicenceDetails newLicence,
            LocalDate newMedicalClearanceExpiresOn, String newResponsibleUnit, RecordMetadata newMetadata) {
        DriverLifecyclePolicy.requireEditable(lifecycleStatus);
        return copy(staffReference, newDisplayName, newLicence, newMedicalClearanceExpiresOn, siteCode,
                newResponsibleUnit, lifecycleStatus, eligibilityStatus, suspensionReason, newMetadata);
    }

    /**
     * Applies a lifecycle transition. Suspension carries a reason so the eligibility assessment can
     * explain the block rather than just refusing.
     */
    public DriverProfileReference changeLifecycle(DriverLifecycleStatus target, String reason,
            RecordMetadata newMetadata) {
        DriverLifecyclePolicy.requireTransition(lifecycleStatus, target);
        if (target == DriverLifecycleStatus.SUSPENDED && (reason == null || reason.isBlank())) {
            throw new InvalidStateTransitionException(Map.of(
                    "aggregate", "DriverProfileReference",
                    "fromStatus", lifecycleStatus.name(),
                    "toStatus", target.name(),
                    "reason", "A suspension reason is required"));
        }
        String newSuspensionReason = target == DriverLifecycleStatus.SUSPENDED ? reason : null;
        return copy(staffReference, displayName, licence, medicalClearanceExpiresOn, siteCode, responsibleUnit,
                target, eligibilityStatus, newSuspensionReason, newMetadata);
    }

    /** Applies a recomputed eligibility status. */
    public DriverProfileReference withEligibility(DriverEligibilityStatus status, RecordMetadata newMetadata) {
        Objects.requireNonNull(status, "eligibilityStatus is required");
        return copy(staffReference, displayName, licence, medicalClearanceExpiresOn, siteCode, responsibleUnit,
                lifecycleStatus, status, suspensionReason, newMetadata);
    }

    private DriverProfileReference copy(String newStaffReference, String newDisplayName, LicenceDetails newLicence,
            LocalDate newMedicalClearanceExpiresOn, SiteCode newSiteCode, String newResponsibleUnit,
            DriverLifecycleStatus newLifecycleStatus, DriverEligibilityStatus newEligibilityStatus,
            String newSuspensionReason, RecordMetadata newMetadata) {
        return new DriverProfileReference(id, newStaffReference, newDisplayName, newLicence,
                newMedicalClearanceExpiresOn, newSiteCode, newResponsibleUnit, newLifecycleStatus,
                newEligibilityStatus, newSuspensionReason, principalSubject, newMetadata);
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        String stripped = value.strip();
        if (stripped.length() > maxLength) {
            throw new IllegalArgumentException(field + " cannot exceed " + maxLength + " characters");
        }
        return stripped;
    }
}
