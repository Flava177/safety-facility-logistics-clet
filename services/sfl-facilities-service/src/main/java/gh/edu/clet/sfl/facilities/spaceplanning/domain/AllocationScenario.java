package gh.edu.clet.sfl.facilities.spaceplanning.domain;

import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A versioned "what-if" allocation of S152 spaces - SRS-SFL-S158-01.
 *
 * <h2>Space plan and scenario version</h2>
 *
 * The SRS names two objects, a space plan and an allocation scenario, and says a plan "is a versioned
 * scenario". They are modelled as one table: a <em>plan</em> is the lineage sharing a
 * {@link #planReference} ({@code SP-MAIN-000004}), and each row is one numbered version of it
 * ({@link #versionNumber}). Revising a plan creates the next version as a new draft and leaves the
 * earlier one exactly as it was, so a version that was compared, discussed or committed is never
 * rewritten afterwards. Several drafts - of the same plan or of different ones - can exist at once for
 * comparison; nothing forces a single "current draft".
 *
 * <h2>Never current until committed</h2>
 *
 * A scenario references S152 spaces by id and never writes to S152 itself. The commit - {@link #commit}
 * - is the only way its allocations reach the register, it is explicit and named ({@link #committedBy},
 * {@link #committedAt}, {@link #commitOutcome}), and the application service audits it.
 *
 * @param spaceChangeRequestId the S158-04 request this scenario answers, if any
 * @param appliedToRegisterAt when its allocations were applied to S152 - at commit for like-for-like,
 *        at S176 handover for physical works; {@code null} until then
 * @param linkedProjectId the S176 project proposed for a physical-works commit, held by value
 */
public record AllocationScenario(
        UUID id,
        String siteCode,
        String planReference,
        int versionNumber,
        UUID basedOnScenarioId,
        String name,
        String description,
        UUID spaceChangeRequestId,
        ScenarioStatus status,
        CommitOutcome commitOutcome,
        String commitNote,
        String committedBy,
        Instant committedAt,
        Instant appliedToRegisterAt,
        UUID linkedProjectId,
        String linkedProjectReference,
        String handedOverBy,
        Instant handedOverAt,
        String discardedBy,
        Instant discardedAt,
        String discardReason,
        RecordMetadata metadata) {

    public AllocationScenario {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(metadata, "metadata is required");
        siteCode = requireText(siteCode, "siteCode", 40);
        planReference = requireText(planReference, "planReference", 40);
        name = requireText(name, "name", 200);
        description = optional(description, "description", 4000);
        if (versionNumber < 1) {
            throw new IllegalArgumentException("versionNumber starts at 1");
        }
    }

    public static AllocationScenario create(UUID id, String siteCode, String planReference, String name,
            String description, UUID spaceChangeRequestId, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        return new AllocationScenario(id, siteCode, planReference, 1, null, name, description, spaceChangeRequestId,
                ScenarioStatus.DRAFT, null, null, null, null, null, null, null, null, null, null, null, null,
                RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    /**
     * The next version of this plan, as a new draft.
     *
     * <p>Any status may be revised - revising a committed plan is how a reorganisation that changed its
     * mind is modelled - but the new version is always a draft and has to be committed on its own.
     */
    public AllocationScenario revise(UUID newId, int nextVersionNumber, String newName, String newDescription,
            String actorId, Instant at, SourceChannel channel, String correlationId) {
        return new AllocationScenario(newId, siteCode, planReference, nextVersionNumber, id,
                newName == null || newName.isBlank() ? name : newName,
                newDescription == null ? description : newDescription, spaceChangeRequestId, ScenarioStatus.DRAFT,
                null, null, null, null, null, null, null, null, null, null, null, null,
                RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    public AllocationScenario rename(String newName, String newDescription, String actorId, Instant at,
            SourceChannel channel, String correlationId) {
        requireDraft("edit");
        return with(status, newName == null || newName.isBlank() ? name : newName,
                newDescription == null ? description : newDescription, spaceChangeRequestId, commitOutcome,
                commitNote, committedBy, committedAt, appliedToRegisterAt, linkedProjectId, linkedProjectReference,
                handedOverBy, handedOverAt, discardedBy, discardedAt, discardReason,
                metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    /** Records that the scenario's allocations changed, so its version moves and a stale commit is refused. */
    public AllocationScenario allocationsChanged(String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        requireDraft("change allocations of");
        return with(status, name, description, spaceChangeRequestId, commitOutcome, commitNote, committedBy,
                committedAt, appliedToRegisterAt, linkedProjectId, linkedProjectReference, handedOverBy,
                handedOverAt, discardedBy, discardedAt, discardReason,
                metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    public AllocationScenario linkRequest(UUID requestId, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        if (spaceChangeRequestId != null && !spaceChangeRequestId.equals(requestId)) {
            throw new FacilitiesException.InvalidStateTransitionException(
                    "This scenario already answers another space-change request.");
        }
        return with(status, name, description, requestId, commitOutcome, commitNote, committedBy, committedAt,
                appliedToRegisterAt, linkedProjectId, linkedProjectReference, handedOverBy, handedOverAt,
                discardedBy, discardedAt, discardReason, metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    /** The explicit, named commit. The caller applies or proposes; this only records the decision. */
    public AllocationScenario commit(CommitOutcome outcome, String note, String actorId, Instant at,
            SourceChannel channel, String correlationId) {
        Objects.requireNonNull(outcome, "a commit must state its outcome");
        transition(ScenarioStatus.COMMITTED);
        return with(ScenarioStatus.COMMITTED, name, description, spaceChangeRequestId, outcome,
                optional(note, "commitNote", 2000), actorId, at, appliedToRegisterAt, linkedProjectId,
                linkedProjectReference, handedOverBy, handedOverAt, discardedBy, discardedAt, discardReason,
                metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    public AllocationScenario markApplied(Instant at) {
        return with(status, name, description, spaceChangeRequestId, commitOutcome, commitNote, committedBy,
                committedAt, at, linkedProjectId, linkedProjectReference, handedOverBy, handedOverAt, discardedBy,
                discardedAt, discardReason, metadata);
    }

    public AllocationScenario linkProject(UUID projectId, String projectReference) {
        return with(status, name, description, spaceChangeRequestId, commitOutcome, commitNote, committedBy,
                committedAt, appliedToRegisterAt, projectId, projectReference, handedOverBy, handedOverAt,
                discardedBy, discardedAt, discardReason, metadata);
    }

    public AllocationScenario handOver(String by, Instant at, SourceChannel channel, String correlationId) {
        transition(ScenarioStatus.HANDED_OVER);
        return with(ScenarioStatus.HANDED_OVER, name, description, spaceChangeRequestId, commitOutcome, commitNote,
                committedBy, committedAt, at, linkedProjectId, linkedProjectReference, by, at, discardedBy,
                discardedAt, discardReason, metadata.modifiedBy(by, at, channel, correlationId));
    }

    public AllocationScenario discard(String reason, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        if (reason == null || reason.isBlank()) {
            throw new FacilitiesException.ValidationFailedException("Discarding a scenario requires a reason.");
        }
        transition(ScenarioStatus.DISCARDED);
        return with(ScenarioStatus.DISCARDED, name, description, spaceChangeRequestId, commitOutcome, commitNote,
                committedBy, committedAt, appliedToRegisterAt, linkedProjectId, linkedProjectReference,
                handedOverBy, handedOverAt, actorId, at, optional(reason, "discardReason", 2000),
                metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    /** {@code SP-MAIN-000004 v2} - what the S152 register records as an allocation's source. */
    public String displayReference() {
        return planReference + " v" + versionNumber;
    }

    public boolean awaitingHandover() {
        return status == ScenarioStatus.COMMITTED && commitOutcome == CommitOutcome.PHYSICAL_WORKS
                && appliedToRegisterAt == null;
    }

    private void requireDraft(String verb) {
        if (!status.isDraft()) {
            throw new FacilitiesException.InvalidStateTransitionException("A " + status
                    + " scenario cannot be changed; revise it to model a new version instead of trying to "
                    + verb + " it.");
        }
    }

    private void transition(ScenarioStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new FacilitiesException.InvalidStateTransitionException(
                    "A " + status + " scenario cannot become " + target + ".");
        }
    }

    private AllocationScenario with(ScenarioStatus newStatus, String newName, String newDescription,
            UUID newRequestId, CommitOutcome newOutcome, String newCommitNote, String newCommittedBy,
            Instant newCommittedAt, Instant newAppliedAt, UUID newProjectId, String newProjectReference,
            String newHandedOverBy, Instant newHandedOverAt, String newDiscardedBy, Instant newDiscardedAt,
            String newDiscardReason, RecordMetadata newMetadata) {
        return new AllocationScenario(id, siteCode, planReference, versionNumber, basedOnScenarioId, newName,
                newDescription, newRequestId, newStatus, newOutcome, newCommitNote, newCommittedBy, newCommittedAt,
                newAppliedAt, newProjectId, newProjectReference, newHandedOverBy, newHandedOverAt, newDiscardedBy,
                newDiscardedAt, newDiscardReason, newMetadata);
    }

    static String requireText(String value, String field, int max) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        String stripped = value.strip();
        if (stripped.length() > max) {
            throw new IllegalArgumentException(field + " must be at most " + max + " characters");
        }
        return stripped;
    }

    static String optional(String value, String field, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return requireText(value, field, max);
    }
}
