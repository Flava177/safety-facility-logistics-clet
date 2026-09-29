package gh.edu.clet.sfl.facilities.eventlogistics.domain;

import java.util.Locale;
import java.util.Optional;

/**
 * The event status a CCP Events (S078) hand-off states.
 *
 * <p>S078's own status vocabulary is not specified anywhere S173 can read (see the gap report). These
 * are the states a hand-off needs to distinguish: only a {@link #CONFIRMED} event creates or updates a
 * set-up task (S173-01, "a confirmed event ... creates a set-up task"), and {@link #CANCELLED} withdraws
 * one. Anything else - a draft, a tentative hold - is a hand-off for an event that is not confirmed and
 * is rejected rather than created speculatively.
 */
public enum S078EventStatus {
    DRAFT,
    TENTATIVE,
    CONFIRMED,
    CANCELLED;

    public static Optional<S078EventStatus> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(raw.strip().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException unknown) {
            return Optional.empty();
        }
    }
}
