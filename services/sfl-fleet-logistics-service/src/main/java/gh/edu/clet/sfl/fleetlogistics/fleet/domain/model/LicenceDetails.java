package gh.edu.clet.sfl.fleetlogistics.fleet.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Objects;

/**
 * A driver's licence. The number is a sensitive field: it is masked from roles without
 * {@code FLEET_DRIVER_SENSITIVE_READ} (SRS-SFL-S166-01 sensitive-field masking).
 */
public record LicenceDetails(String number, LicenceClass licenceClass, LocalDate expiresOn) {

    private static final int VISIBLE_SUFFIX = 3;

    public LicenceDetails {
        if (number == null || number.isBlank()) {
            throw new IllegalArgumentException("licence number is required");
        }
        number = number.strip().replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        Objects.requireNonNull(licenceClass, "licenceClass is required");
        Objects.requireNonNull(expiresOn, "licence expiresOn is required");
    }

    /** A licence expiring today is still valid today. */
    public boolean isExpiredAt(Instant at) {
        return expiresOn.isBefore(LocalDate.ofInstant(at, ComplianceDocument.OPERATING_ZONE));
    }

    /** True when the licence expires before the end of a proposed assignment period. */
    public boolean expiresBefore(Instant instant) {
        return expiresOn.isBefore(LocalDate.ofInstant(instant, ComplianceDocument.OPERATING_ZONE));
    }

    public long daysUntilExpiry(Instant at) {
        return ChronoUnit.DAYS.between(LocalDate.ofInstant(at, ComplianceDocument.OPERATING_ZONE), expiresOn);
    }

    public boolean covers(VehicleCategory category) {
        return licenceClass.covers(category);
    }

    /** Masked form for roles without the sensitive-read permission. */
    public String maskedNumber() {
        if (number.length() <= VISIBLE_SUFFIX) {
            return "•".repeat(number.length());
        }
        return "•".repeat(number.length() - VISIBLE_SUFFIX) + number.substring(number.length() - VISIBLE_SUFFIX);
    }
}
