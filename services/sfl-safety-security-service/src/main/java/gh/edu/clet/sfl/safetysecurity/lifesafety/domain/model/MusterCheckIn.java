package gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model;

import java.time.Instant;
import java.util.UUID;

/** One person accounted for at the muster point during a {@link MusterSession}. */
public record MusterCheckIn(UUID id, UUID musterSessionId, String personRef, Instant checkedInAt,
        SourceChannel source) {
}
