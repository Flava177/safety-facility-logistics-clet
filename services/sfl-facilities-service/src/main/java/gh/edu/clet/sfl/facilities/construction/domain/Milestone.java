package gh.edu.clet.sfl.facilities.construction.domain;

import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.model.EstateCodes;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * A target milestone and its current target date - SRS-SFL-S176-01.
 *
 * <p>This record carries the current target only. Every target it has ever had is a
 * {@link ProjectRevision}, written in the same transaction as the change here, so "versioned, not
 * overwritten" holds even though this row moves: the value it replaced is never lost, and
 * {@code revision} says which of the history rows is current.
 */
public record Milestone(
        UUID id,
        UUID projectId,
        String siteCode,
        String milestoneCode,
        String name,
        LocalDate targetDate,
        int revision,
        LocalDate achievedOn,
        RecordMetadata metadata) {

    public Milestone {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(projectId, "projectId is required");
        siteCode = EstateCodes.normalize(siteCode);
        milestoneCode = EstateCodes.normalize(milestoneCode);
        EstateCodes.require(name, "name");
        name = name.strip();
        Objects.requireNonNull(targetDate, "targetDate is required");
        Objects.requireNonNull(metadata, "metadata is required");
        if (revision < 1) {
            throw new IllegalArgumentException("revision starts at 1");
        }
    }

    public static Milestone create(UUID id, UUID projectId, String siteCode, String code, String name,
            LocalDate targetDate, String actorId, Instant at, SourceChannel channel, String correlationId) {
        return new Milestone(id, projectId, siteCode, code, name, targetDate, 1, null,
                RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    public Milestone revise(LocalDate newTarget, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        Objects.requireNonNull(newTarget, "targetDate is required");
        return new Milestone(id, projectId, siteCode, milestoneCode, name, newTarget, revision + 1, achievedOn,
                metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    public Milestone achieve(LocalDate on, String actorId, Instant at, SourceChannel channel, String correlationId) {
        Objects.requireNonNull(on, "achievedOn is required");
        return new Milestone(id, projectId, siteCode, milestoneCode, name, targetDate, revision, on,
                metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    /** Past its current target and not achieved - a milestone exception on the S176 dashboard. */
    public boolean isOverdue(LocalDate today) {
        return achievedOn == null && targetDate.isBefore(today);
    }
}
