package gh.edu.clet.sfl.fleetlogistics.fleet.domain.model;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.MissingSiteScopeException;
import java.util.Locale;

/**
 * A CLET site scope. Every fleet operational record carries one (SRS-SFL-S166-01: "A record cannot
 * be saved without the required site scope and operational owner").
 *
 * <p>Normalised to upper case so site comparisons and uniqueness rules are case-insensitive and
 * agree with {@code SiteScopedPrincipal}'s normalisation.
 */
public record SiteCode(String value) implements Comparable<SiteCode> {

    private static final int MAX_LENGTH = 40;

    public SiteCode {
        if (value == null || value.isBlank()) {
            throw new MissingSiteScopeException();
        }
        value = value.strip().toUpperCase(Locale.ROOT);
        if (value.length() > MAX_LENGTH) {
            throw new MissingSiteScopeException(java.util.Map.of("maxLength", MAX_LENGTH));
        }
    }

    public static SiteCode of(String value) {
        return new SiteCode(value);
    }

    @Override
    public int compareTo(SiteCode other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
