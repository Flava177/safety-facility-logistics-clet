package gh.edu.clet.sfl.facilities.shared.domain.model;

import java.util.Locale;

/**
 * The one normalisation rule for every identifier in the facilities service.
 *
 * <p>{@code "main"}, {@code " MAIN "} and {@code "Main"} are the same site to every module, and that
 * is only true because they all normalise here. The rule lived on {@code Site} while the estate was
 * the only thing that had codes; readiness checklists and blockers have them too, and reaching into
 * another aggregate for a static helper is not a dependency worth having.
 *
 * <p>Deliberately trivial and deliberately shared. The risk it removes is not complexity — it is two
 * modules disagreeing about whether a code is case-sensitive.
 */
public final class EstateCodes {

    private EstateCodes() {
    }

    /** Trims and upper-cases an identifier, refusing a blank one. */
    public static String normalize(String value) {
        require(value, "code");
        return value.strip().toUpperCase(Locale.ROOT);
    }

    /** Trims free text, mapping blank to {@code null} so "unset" and "empty" are one thing. */
    public static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    /** Rejects a missing required field, naming it. */
    public static void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
