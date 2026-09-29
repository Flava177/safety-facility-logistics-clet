package gh.edu.clet.sfl.facilities.construction.domain;

import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.model.EstateCodes;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * A handover attempt and its outcome - SRS-SFL-S176-04.
 *
 * <p>Every attempt is kept, including the ones that failed. "A handover without a corresponding S152
 * update is flagged as incomplete, not silently accepted as done": an INCOMPLETE row is the flag, the
 * project stays at practical completion, and the dashboard's handover-completeness figure counts them.
 *
 * <h2>The S158 confirmation</h2>
 *
 * Where the project came from an S158 space change with a committed scenario, handover confirms that
 * commit through {@code ScenarioHandover}. {@code UNRESOLVED} is the honest state for "S158 does not
 * recognise that scenario" - which, until S158 is merged, is every scenario. The S152 register is the
 * authoritative current state and has already been updated, so the handover completes; the
 * outstanding confirmation is shown and can be retried rather than blocking the space from opening.
 */
public record Handover(
        UUID id,
        UUID projectId,
        String siteCode,
        Outcome outcome,
        String incompleteReason,
        LocalDate handoverDate,
        String notes,
        int registerChangeCount,
        UUID scenarioId,
        ScenarioConfirmation scenarioConfirmation,
        String scenarioConfirmationDetail,
        String recordedBy,
        Instant recordedAt,
        RecordMetadata metadata) {

    public enum Outcome {
        COMPLETE,
        INCOMPLETE
    }

    public enum ScenarioConfirmation {
        /** The project carries no S158 scenario. */
        NOT_APPLICABLE,
        /** S158 confirmed the scenario as handed over. */
        CONFIRMED,
        /** S158 did not recognise the scenario, or was not in a state to confirm it. Retryable. */
        UNRESOLVED
    }

    public Handover {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(projectId, "projectId is required");
        siteCode = EstateCodes.normalize(siteCode);
        Objects.requireNonNull(outcome, "outcome is required");
        incompleteReason = EstateCodes.blankToNull(incompleteReason);
        Objects.requireNonNull(handoverDate, "handoverDate is required");
        notes = EstateCodes.blankToNull(notes);
        scenarioConfirmation = scenarioConfirmation == null ? ScenarioConfirmation.NOT_APPLICABLE
                : scenarioConfirmation;
        scenarioConfirmationDetail = EstateCodes.blankToNull(scenarioConfirmationDetail);
        EstateCodes.require(recordedBy, "recordedBy");
        Objects.requireNonNull(recordedAt, "recordedAt is required");
        Objects.requireNonNull(metadata, "metadata is required");
        if (outcome == Outcome.COMPLETE && registerChangeCount < 1) {
            throw new IllegalArgumentException("a complete handover has applied at least one S152 change");
        }
        if (outcome == Outcome.INCOMPLETE && incompleteReason == null) {
            throw new IllegalArgumentException("an incomplete handover says why");
        }
    }

    public static Handover incomplete(ConstructionProject project, LocalDate date, String notes, String reason,
            String actorId, Instant at, SourceChannel channel, String correlationId) {
        return new Handover(UUID.randomUUID(), project.id(), project.siteCode(), Outcome.INCOMPLETE, reason, date,
                notes, 0, project.committedScenarioId(), ScenarioConfirmation.NOT_APPLICABLE, null, actorId, at,
                RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    public static Handover complete(UUID id, ConstructionProject project, LocalDate date, String notes,
            int changes, ScenarioConfirmation confirmation, String detail, String actorId, Instant at,
            SourceChannel channel, String correlationId) {
        return new Handover(id, project.id(), project.siteCode(), Outcome.COMPLETE, null, date, notes, changes,
                project.committedScenarioId(), confirmation, detail, actorId, at,
                RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    public Handover withScenarioConfirmation(ScenarioConfirmation confirmation, String detail, String actorId,
            Instant at, SourceChannel channel, String correlationId) {
        return new Handover(id, projectId, siteCode, outcome, incompleteReason, handoverDate, notes,
                registerChangeCount, scenarioId, confirmation, detail, recordedBy, recordedAt,
                metadata.modifiedBy(actorId, at, channel, correlationId));
    }
}
