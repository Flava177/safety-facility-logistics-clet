package gh.edu.clet.sfl.fleetlogistics.fleet.domain.model;

import java.util.Objects;

/**
 * What the vehicle physically is: make, model, year, category and seated/load capacity
 * (SRS-SFL-S166-01 recommended vehicle information).
 */
public record VehicleSpecification(
        String make,
        String model,
        int manufactureYear,
        VehicleCategory category,
        int capacity) {

    /** Earliest year a CLET fleet record is plausible; guards typos such as a two-digit year. */
    private static final int EARLIEST_YEAR = 1950;
    private static final int MAX_CAPACITY = 200;

    public VehicleSpecification {
        make = requireText(make, "make", 80);
        model = requireText(model, "model", 80);
        Objects.requireNonNull(category, "category is required");
        if (manufactureYear < EARLIEST_YEAR) {
            throw new IllegalArgumentException("manufactureYear cannot be earlier than " + EARLIEST_YEAR);
        }
        if (capacity < 1 || capacity > MAX_CAPACITY) {
            throw new IllegalArgumentException("capacity must be between 1 and " + MAX_CAPACITY);
        }
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
