package gh.edu.clet.sfl.fleetlogistics.fleet.domain.model;

import java.util.Locale;

/**
 * A vehicle registration number.
 *
 * <p>Normalised to upper case with internal whitespace collapsed to a single space, so that
 * {@code gt 1234-26} and {@code GT 1234-26} cannot both be registered as active in the same site —
 * that is the SRS-SFL-S166-01 duplicate-identifier rule, and it only holds if the comparison form is
 * canonical. The database partial unique index applies the same normalisation.
 */
public record RegistrationNumber(String value) implements Comparable<RegistrationNumber> {

    private static final int MAX_LENGTH = 40;

    public RegistrationNumber {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("registrationNumber is required");
        }
        value = value.strip().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("registrationNumber cannot exceed " + MAX_LENGTH + " characters");
        }
    }

    public static RegistrationNumber of(String value) {
        return new RegistrationNumber(value);
    }

    @Override
    public int compareTo(RegistrationNumber other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
