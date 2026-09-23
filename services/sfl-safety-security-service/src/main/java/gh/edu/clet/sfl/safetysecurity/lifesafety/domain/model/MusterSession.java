package gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * SRS-SFL-S162a-04: a roll-call opened for an affected zone on a fire/panic event. A supplement to -
 * never a replacement for - the certified system's own alarms and actuation.
 */
public record MusterSession(UUID id, String siteCode, String zoneCode, UUID triggeringEventId, MusterStatus status,
        Instant openedAt, Instant closedAt, RecordMetadata metadata) {

    public MusterSession close(Instant now, RecordMetadata nextMeta) {
        return new MusterSession(id, siteCode, zoneCode, triggeringEventId, MusterStatus.CLOSED, openedAt, now,
                nextMeta);
    }
}
