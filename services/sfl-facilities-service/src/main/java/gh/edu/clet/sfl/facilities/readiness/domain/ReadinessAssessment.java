package gh.edu.clet.sfl.facilities.readiness.domain;

import gh.edu.clet.sfl.facilities.masterdata.domain.LocationReadinessStatus;
import gh.edu.clet.sfl.facilities.shared.domain.model.EstateCodes;
import gh.edu.clet.sfl.facilities.shared.domain.model.OperatingMode;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * One completed readiness inspection of a space (SRS-SFL-S152-01, "readiness profile ... assessment").
 *
 * <p>Immutable once submitted. An assessment is a statement about the state of a room at a moment,
 * signed by a named assessor — amending one after the fact would destroy the only thing it is good
 * for. A space that has changed gets a *new* assessment, and the history of both is preserved.
 *
 * <p>{@code checklistVersion} pins the questions asked. {@code operatingMode} pins the standard they
 * were asked under, because the same room assessed in examination mode and in routine mode is
 * answering different questions and the results are not comparable without it.
 */
public record ReadinessAssessment(
        UUID id,
        UUID roomId,
        String siteCode,
        UUID checklistId,
        String checklistCode,
        int checklistVersion,
        OperatingMode operatingMode,
        LocationReadinessStatus outcome,
        int score,
        List<ReadinessAssessmentItem> items,
        String notes,
        String assessedBy,
        Instant assessedAt) {

    public ReadinessAssessment {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(roomId, "roomId is required");
        EstateCodes.require(siteCode, "siteCode");
        Objects.requireNonNull(operatingMode, "operatingMode is required");
        Objects.requireNonNull(outcome, "outcome is required");
        EstateCodes.require(assessedBy, "assessedBy");
        Objects.requireNonNull(assessedAt, "assessedAt is required");
        items = items == null ? List.of() : List.copyOf(items);
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException("score must be between 0 and 100");
        }
    }

    /** Items the assessor marked as failed — the ones that become blockers. */
    public List<ReadinessAssessmentItem> failedItems() {
        return items.stream().filter(item -> !item.passed()).toList();
    }

    /** {@code true} when a mandatory item failed, whatever the score says. */
    public boolean hasMandatoryFailure() {
        return items.stream().anyMatch(item -> item.mandatory() && !item.passed());
    }

    /** How old this assessment is, as of {@code now} — the input to the staleness warning. */
    public java.time.Duration ageAt(Instant now) {
        return java.time.Duration.between(assessedAt, now);
    }
}
