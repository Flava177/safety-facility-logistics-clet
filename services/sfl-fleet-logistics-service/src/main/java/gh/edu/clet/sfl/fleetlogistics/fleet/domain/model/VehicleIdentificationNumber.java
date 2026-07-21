package gh.edu.clet.sfl.fleetlogistics.fleet.domain.model;

import java.util.Locale;

/**
 * A VIN or chassis number. Optional, because not every fleet record has one recorded, and treated as a
 * sensitive field: it is masked from roles without {@code FLEET_VEHICLE_SENSITIVE_READ}
 * (SRS-SFL-S166-01: "Sensitive fields are masked from roles without explicit permission").
 */
public record VehicleIdentificationNumber(String value) {

    private static final int MAX_LENGTH = 40;
    private static final int VISIBLE_SUFFIX = 4;

    public VehicleIdentificationNumber {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("vehicleIdentificationNumber cannot be blank when supplied");
        }
        value = value.strip().replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("vehicleIdentificationNumber cannot exceed " + MAX_LENGTH
                    + " characters");
        }
    }

    /** Returns {@code null} for a blank input, so optional fields need no null dance at call sites. */
    public static VehicleIdentificationNumber ofNullable(String value) {
        return value == null || value.isBlank() ? null : new VehicleIdentificationNumber(value);
    }

    /** The masked form shown to roles without the sensitive-read permission, e.g. {@code ••••1234}. */
    public String masked() {
        if (value.length() <= VISIBLE_SUFFIX) {
            return "•".repeat(value.length());
        }
        return "•".repeat(value.length() - VISIBLE_SUFFIX) + value.substring(value.length() - VISIBLE_SUFFIX);
    }

    @Override
    public String toString() {
        return value;
    }
}
