package gh.edu.clet.sfl.facilities.construction.domain;

import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.model.EstateCodes;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * One version of a baseline or a milestone target - SRS-SFL-S176-01 "milestone dates and budget
 * revisions are versioned, not overwritten".
 *
 * <p>Immutable and append-only, and the V21 trigger refuses an UPDATE or DELETE on the table, so the
 * history is a property of the database and not of this class being used carefully. Revision 1 is the
 * original value; each later revision must say why it moved, because "the date slipped" with no
 * reason is the silent overwrite the requirement exists to prevent, one step removed.
 *
 * @param subjectId the milestone's id, or {@code null} for the budget baseline
 * @param subjectCode the milestone code, or {@link #BASELINE}
 */
public record ProjectRevision(
        UUID id,
        UUID projectId,
        String siteCode,
        RevisionSubject subject,
        UUID subjectId,
        String subjectCode,
        int revision,
        BigDecimal amount,
        String currency,
        LocalDate targetDate,
        String reason,
        String revisedBy,
        Instant revisedAt,
        RecordMetadata metadata) {

    public static final String BASELINE = "BASELINE";

    public ProjectRevision {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(projectId, "projectId is required");
        siteCode = EstateCodes.normalize(siteCode);
        Objects.requireNonNull(subject, "subject is required");
        subjectCode = EstateCodes.normalize(subjectCode);
        reason = EstateCodes.blankToNull(reason);
        EstateCodes.require(revisedBy, "revisedBy");
        Objects.requireNonNull(revisedAt, "revisedAt is required");
        Objects.requireNonNull(metadata, "metadata is required");
        if (revision < 1) {
            throw new IllegalArgumentException("revision starts at 1");
        }
        if (revision > 1 && reason == null) {
            throw new FacilitiesException.ValidationFailedException("A revision must record why the value changed.");
        }
        if (subject == RevisionSubject.BUDGET_BASELINE && (amount == null || currency == null)) {
            throw new IllegalArgumentException("a baseline revision carries an amount and a currency");
        }
        if (subject == RevisionSubject.MILESTONE_TARGET && (targetDate == null || subjectId == null)) {
            throw new IllegalArgumentException("a milestone revision carries the milestone and its target date");
        }
    }

    /** The version of the project's baseline the project now carries. */
    public static ProjectRevision baseline(ConstructionProject project, String reason, String actorId, Instant at,
            SourceChannel channel, String correlationId) {
        return new ProjectRevision(UUID.randomUUID(), project.id(), project.siteCode(),
                RevisionSubject.BUDGET_BASELINE, null, BASELINE, project.baselineRevision(),
                project.budgetBaseline(), project.currency(), null, reason, actorId, at,
                RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    /** The version of the milestone's target the milestone now carries. */
    public static ProjectRevision milestone(Milestone milestone, String reason, String actorId, Instant at,
            SourceChannel channel, String correlationId) {
        return new ProjectRevision(UUID.randomUUID(), milestone.projectId(), milestone.siteCode(),
                RevisionSubject.MILESTONE_TARGET, milestone.id(), milestone.milestoneCode(), milestone.revision(),
                null, null, milestone.targetDate(), reason, actorId, at,
                RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }
}
