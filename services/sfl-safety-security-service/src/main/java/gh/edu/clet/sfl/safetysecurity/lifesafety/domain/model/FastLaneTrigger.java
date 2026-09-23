package gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * SRS-SFL-S162a-02: one attempt to drive S174's fast-lane/break-glass activation from an observed
 * fire/panic event, with the timing needed for latency verification and whether it degraded to a
 * fallback rather than reaching the emergency workflow.
 */
public record FastLaneTrigger(UUID id, String siteCode, String zoneCode, UUID lifeSafetyEventId,
        UUID activationId, FastLaneStatus status, long latencyMillis, String note, Instant triggeredAt,
        RecordMetadata metadata) {
}
