package gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * SRS-SFL-S162a-01: an observed fire/life-safety signal, normalised from the certified system's
 * feed. Immutable once ingested - this module observes and records, it never revises history.
 */
public record LifeSafetyEvent(UUID id, String siteCode, String sourceSystem, String externalEventId,
        String deviceId, String zoneCode, LifeSafetyEventKind kind, Instant occurredAt, RecordMetadata metadata) {

    public boolean isFireOrPanic() {
        return kind == LifeSafetyEventKind.FIRE || kind == LifeSafetyEventKind.PANIC;
    }
}
