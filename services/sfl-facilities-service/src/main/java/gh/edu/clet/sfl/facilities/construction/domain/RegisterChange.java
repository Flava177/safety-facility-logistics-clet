package gh.edu.clet.sfl.facilities.construction.domain;

import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.model.EstateCodes;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One S152 register change a handover applied - the evidence that the register reflects the new or
 * changed space "without a separate manual update" (SRS-SFL-S176-04 acceptance criterion).
 *
 * @param roomVersion the S152 room's record version after the change, so a reader can find the exact
 *        audit entry S152 wrote for it
 */
public record RegisterChange(
        UUID id,
        UUID handoverId,
        UUID projectId,
        String siteCode,
        Action action,
        UUID roomId,
        String roomCode,
        long roomVersion,
        RecordMetadata metadata) {

    public enum Action {
        CREATED,
        UPDATED
    }

    public RegisterChange {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(handoverId, "handoverId is required");
        Objects.requireNonNull(projectId, "projectId is required");
        siteCode = EstateCodes.normalize(siteCode);
        Objects.requireNonNull(action, "action is required");
        Objects.requireNonNull(roomId, "roomId is required");
        roomCode = EstateCodes.normalize(roomCode);
        Objects.requireNonNull(metadata, "metadata is required");
    }

    public static RegisterChange of(UUID handoverId, ConstructionProject project, Action action, UUID roomId,
            String roomCode, long roomVersion, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        return new RegisterChange(UUID.randomUUID(), handoverId, project.id(), project.siteCode(), action, roomId,
                roomCode, roomVersion, RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }
}
