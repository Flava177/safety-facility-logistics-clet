package gh.edu.clet.sfl.emergencynotification.domain.model;

import java.util.Locale;
import java.util.Objects;

/** A normalized CLET site scope. Framework-free domain value object. */
public record SiteCode(String value) {

    public SiteCode {
        Objects.requireNonNull(value, "site code is required");
        value = value.strip().toUpperCase(Locale.ROOT);
        if (value.isBlank()) {
            throw new IllegalArgumentException("site code is required");
        }
    }

    public static SiteCode of(String value) {
        return new SiteCode(value);
    }
}
