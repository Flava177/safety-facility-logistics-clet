package gh.edu.clet.sfl.facilities.construction.domain;

import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.model.EstateCodes;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * A capital or minor-works project on CLET premises - SRS-SFL-S176-01.
 *
 * <h2>What the record has to hold, and why each piece is here</h2>
 *
 * "A project records scope, budget, funding source, target milestones and the responsible
 * contractor(s)." Scope, the budget baseline and its currency, and the funding source live on this
 * record; milestones and contractor assignments are their own records because each one changes on its
 * own and each change is versioned or audited separately.
 *
 * <p>The funding source is a reference ({@code fundingSourceReference}) because the SRS resolves it
 * through Master Data Management (S223), which is not integrated. The name beside it is what the
 * project manager typed, kept so a reader can tell what the reference was meant to be; nothing here
 * validates it against S223.
 *
 * <h2>The budget baseline is not the current budget</h2>
 *
 * {@code budgetBaseline} is what the approver signed. The current budget is the baseline plus the
 * approved variation orders, and it is computed - never stored - so it cannot drift from the variation
 * records it is supposed to be traceable to (SRS-SFL-S176-03). Once works start the baseline is frozen;
 * a change in cost after that is a variation order or it is nothing.
 *
 * @param workTypes normalised codes from the configured catalogue; which of them need a permit is
 *        runtime configuration, read at the moment the project tries to start
 * @param baselineRevision 0 for a PROPOSED project with no baseline yet, 1 for the original, and one
 *        more for each revision. The history of every value is in the revision records.
 * @param approvalId the sign-off covering the current baseline; cleared when the baseline is revised
 * @param affectedRoomIds the S152 spaces an S158 space change said the works will touch. Handover
 *        must update each of them (SRS-SFL-S176-04)
 */
public record ConstructionProject(
        UUID id,
        String projectReference,
        String siteCode,
        String title,
        String scope,
        List<String> workTypes,
        BigDecimal budgetBaseline,
        String currency,
        int baselineRevision,
        String fundingSourceReference,
        String fundingSourceName,
        String projectManagerId,
        ProjectStatus status,
        ProjectOrigin origin,
        UUID spaceChangeRequestId,
        UUID committedScenarioId,
        List<UUID> affectedRoomIds,
        String requestingUnit,
        String justification,
        UUID approvalId,
        Instant startedAt,
        Instant practicalCompletionAt,
        Instant handedOverAt,
        LocalDate defectsLiabilityEndsOn,
        Instant closedAt,
        String cancellationReason,
        String registeredBy,
        Instant registeredAt,
        RecordMetadata metadata) {

    private static final Pattern CURRENCY = Pattern.compile("^[A-Z]{3}$");

    public ConstructionProject {
        Objects.requireNonNull(id, "id is required");
        projectReference = EstateCodes.normalize(projectReference);
        siteCode = EstateCodes.normalize(siteCode);
        EstateCodes.require(title, "title");
        title = title.strip();
        EstateCodes.require(scope, "scope");
        scope = scope.strip();
        workTypes = workTypes == null ? List.of() : workTypes.stream().map(EstateCodes::normalize).distinct()
                .sorted().toList();
        budgetBaseline = budgetBaseline == null ? null : budgetBaseline.setScale(2, RoundingMode.HALF_UP);
        currency = currency == null || currency.isBlank() ? null : currency.strip().toUpperCase(Locale.ROOT);
        fundingSourceReference = EstateCodes.blankToNull(fundingSourceReference);
        fundingSourceName = EstateCodes.blankToNull(fundingSourceName);
        projectManagerId = EstateCodes.blankToNull(projectManagerId);
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(origin, "origin is required");
        affectedRoomIds = affectedRoomIds == null ? List.of() : affectedRoomIds.stream().distinct().toList();
        requestingUnit = EstateCodes.blankToNull(requestingUnit);
        justification = EstateCodes.blankToNull(justification);
        cancellationReason = EstateCodes.blankToNull(cancellationReason);
        EstateCodes.require(registeredBy, "registeredBy");
        Objects.requireNonNull(registeredAt, "registeredAt is required");
        Objects.requireNonNull(metadata, "metadata is required");
        if (baselineRevision < 0) {
            throw new IllegalArgumentException("baselineRevision cannot be negative");
        }
    }

    /** The definition registration requires. Validated as a whole so a refusal names everything missing. */
    public record Definition(String scope, BigDecimal budgetBaseline, String currency, String fundingSourceReference,
            String fundingSourceName, List<String> workTypes) {

        /**
         * Refuses an incomplete definition.
         *
         * <p>A project that reaches REGISTERED without a baseline or a funding source is exactly the
         * "construction activity without documented scope, budget and sign-off" the S176-01 user story
         * exists to prevent - the approver would be signing a blank.
         */
        public Definition {
            StringBuilder missing = new StringBuilder();
            if (scope == null || scope.isBlank()) {
                missing.append(" scope;");
            }
            if (budgetBaseline == null || budgetBaseline.signum() <= 0) {
                missing.append(" a budget baseline greater than zero;");
            }
            if (currency == null || !CURRENCY.matcher(currency.strip().toUpperCase(Locale.ROOT)).matches()) {
                missing.append(" a three-letter currency code;");
            }
            if (fundingSourceReference == null || fundingSourceReference.isBlank()) {
                missing.append(" a funding source reference;");
            }
            if (workTypes == null || workTypes.isEmpty()) {
                missing.append(" at least one work type;");
            }
            if (!missing.isEmpty()) {
                throw new FacilitiesException.ValidationFailedException(
                        "A project must record" + missing.substring(0, missing.length() - 1) + ".");
            }
        }
    }

    /** A project manager registering a project directly. Lands in REGISTERED with revision 1. */
    public static ConstructionProject register(UUID id, String reference, String siteCode, String title,
            Definition definition, String projectManagerId, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        return new ConstructionProject(id, reference, siteCode, title, definition.scope(), definition.workTypes(),
                definition.budgetBaseline(), definition.currency(), 1, definition.fundingSourceReference(),
                definition.fundingSourceName(), projectManagerId == null ? actorId : projectManagerId,
                ProjectStatus.REGISTERED, ProjectOrigin.DIRECT, null, null, List.of(), null, null, null, null, null,
                null, null, null, null, actorId, at, RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    /** The S158 intake: a reference with a scope and an origin, and no budget yet. */
    public static ConstructionProject propose(UUID id, String reference, String siteCode, String title, String scope,
            UUID spaceChangeRequestId, UUID committedScenarioId, List<UUID> affectedRoomIds, String requestingUnit,
            String justification, String requestedBy, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        Objects.requireNonNull(spaceChangeRequestId, "spaceChangeRequestId is required");
        return new ConstructionProject(id, reference, siteCode, title, scope, List.of(), null, null, 0, null, null,
                null, ProjectStatus.PROPOSED, ProjectOrigin.S158_SPACE_CHANGE, spaceChangeRequestId,
                committedScenarioId, affectedRoomIds, requestingUnit, justification, null, null, null, null, null,
                null, null, requestedBy == null || requestedBy.isBlank() ? actorId : requestedBy, at,
                RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    /** Defines a PROPOSED project. The actor becomes its project manager unless another is named. */
    public ConstructionProject completeRegistration(Definition definition, String projectManager, String actorId,
            Instant at, SourceChannel channel, String correlationId) {
        ProjectStatus next = status.transitionTo(ProjectStatus.REGISTERED);
        return change(actorId, at, channel, correlationId, draft -> {
            draft.status = next;
            draft.scope = definition.scope();
            draft.budgetBaseline = definition.budgetBaseline();
            draft.currency = definition.currency();
            draft.baselineRevision = 1;
            draft.fundingSourceReference = definition.fundingSourceReference();
            draft.fundingSourceName = definition.fundingSourceName();
            draft.workTypes = definition.workTypes();
            draft.projectManagerId = projectManager == null ? actorId : projectManager;
        });
    }

    /**
     * Revises the budget baseline before works start.
     *
     * <p>An approved project goes back to REGISTERED: the sign-off covered the old number. After works
     * start this is refused - from then on the budget changes only through approved variation orders,
     * which is what keeps the current budget traceable (SRS-SFL-S176-03).
     */
    public ConstructionProject reviseBaseline(BigDecimal amount, String newCurrency, String actorId, Instant at,
            SourceChannel channel, String correlationId) {
        if (status == ProjectStatus.PROPOSED) {
            throw new FacilitiesException.InvalidStateTransitionException(
                    "A proposed project has no baseline to revise; register it first.");
        }
        if (!status.isPreStart()) {
            throw new FacilitiesException.InvalidStateTransitionException(
                    "Once works have started the budget changes only through approved variation orders.");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new FacilitiesException.ValidationFailedException("A budget baseline must be greater than zero.");
        }
        String effectiveCurrency = newCurrency == null || newCurrency.isBlank() ? currency
                : newCurrency.strip().toUpperCase(Locale.ROOT);
        if (!CURRENCY.matcher(effectiveCurrency).matches()) {
            throw new FacilitiesException.ValidationFailedException("A currency must be a three-letter code.");
        }
        ProjectStatus next = status == ProjectStatus.APPROVED ? status.transitionTo(ProjectStatus.REGISTERED) : status;
        return change(actorId, at, channel, correlationId, draft -> {
            draft.budgetBaseline = amount;
            draft.currency = effectiveCurrency;
            draft.baselineRevision = baselineRevision + 1;
            draft.status = next;
            draft.approvalId = null;
        });
    }

    /** Records the accountable approver's sign-off. The separation-of-duties check is the service's. */
    public ConstructionProject approve(UUID approval, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        Objects.requireNonNull(approval, "approval is required");
        ProjectStatus next = status.transitionTo(ProjectStatus.APPROVED);
        return change(actorId, at, channel, correlationId, draft -> {
            draft.status = next;
            draft.approvalId = approval;
        });
    }

    /**
     * Works begin. The gate - sign-off and permits - has been passed by the time this is called; see
     * {@code ProjectStartPolicy}. The null check here is the last line in Java, behind the database's.
     */
    public ConstructionProject start(String actorId, Instant at, SourceChannel channel, String correlationId) {
        if (approvalId == null) {
            throw new ConstructionRefusal(
                    gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesErrorCode.PROJECT_APPROVAL_MISSING,
                    null);
        }
        ProjectStatus next = status.transitionTo(ProjectStatus.IN_PROGRESS);
        return change(actorId, at, channel, correlationId, draft -> {
            draft.status = next;
            draft.startedAt = at;
        });
    }

    public ConstructionProject recordPracticalCompletion(String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        ProjectStatus next = status.transitionTo(ProjectStatus.PRACTICAL_COMPLETION);
        return change(actorId, at, channel, correlationId, draft -> {
            draft.status = next;
            draft.practicalCompletionAt = at;
        });
    }

    /** In the register and in use; the defects-liability period runs until {@code liabilityEndsOn}. */
    public ConstructionProject handOver(LocalDate liabilityEndsOn, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        Objects.requireNonNull(liabilityEndsOn, "liabilityEndsOn is required");
        ProjectStatus next = status.transitionTo(ProjectStatus.HANDED_OVER);
        return change(actorId, at, channel, correlationId, draft -> {
            draft.status = next;
            draft.handedOverAt = at;
            draft.defectsLiabilityEndsOn = liabilityEndsOn;
        });
    }

    public ConstructionProject close(String actorId, Instant at, SourceChannel channel, String correlationId) {
        ProjectStatus next = status.transitionTo(ProjectStatus.CLOSED);
        return change(actorId, at, channel, correlationId, draft -> {
            draft.status = next;
            draft.closedAt = at;
        });
    }

    public ConstructionProject cancel(String reason, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        EstateCodes.require(reason, "reason");
        ProjectStatus next = status.transitionTo(ProjectStatus.CANCELLED);
        return change(actorId, at, channel, correlationId, draft -> {
            draft.status = next;
            draft.cancellationReason = reason;
        });
    }

    /** Whether the defects-liability period is still running on this date. */
    public boolean inLiabilityPeriod(LocalDate on) {
        return status == ProjectStatus.HANDED_OVER && defectsLiabilityEndsOn != null
                && !on.isAfter(defectsLiabilityEndsOn);
    }

    // ---- copy-on-write -------------------------------------------------------------------------

    /**
     * Every mutator goes through here, so none can change the project without its provenance moving
     * with it - the same guarantee {@link RecordMetadata#modifiedBy} gives every other aggregate.
     */
    private ConstructionProject change(String actorId, Instant at, SourceChannel channel, String correlationId,
            Consumer<Draft> edit) {
        Draft draft = new Draft(this);
        edit.accept(draft);
        return new ConstructionProject(id, projectReference, siteCode, title, draft.scope, draft.workTypes,
                draft.budgetBaseline, draft.currency, draft.baselineRevision, draft.fundingSourceReference,
                draft.fundingSourceName, draft.projectManagerId, draft.status, origin, spaceChangeRequestId,
                committedScenarioId, affectedRoomIds, requestingUnit, justification, draft.approvalId,
                draft.startedAt, draft.practicalCompletionAt, draft.handedOverAt, draft.defectsLiabilityEndsOn,
                draft.closedAt, draft.cancellationReason, registeredBy, registeredAt,
                metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    private static final class Draft {
        private String scope;
        private List<String> workTypes;
        private BigDecimal budgetBaseline;
        private String currency;
        private int baselineRevision;
        private String fundingSourceReference;
        private String fundingSourceName;
        private String projectManagerId;
        private ProjectStatus status;
        private UUID approvalId;
        private Instant startedAt;
        private Instant practicalCompletionAt;
        private Instant handedOverAt;
        private LocalDate defectsLiabilityEndsOn;
        private Instant closedAt;
        private String cancellationReason;

        private Draft(ConstructionProject project) {
            scope = project.scope;
            workTypes = project.workTypes;
            budgetBaseline = project.budgetBaseline;
            currency = project.currency;
            baselineRevision = project.baselineRevision;
            fundingSourceReference = project.fundingSourceReference;
            fundingSourceName = project.fundingSourceName;
            projectManagerId = project.projectManagerId;
            status = project.status;
            approvalId = project.approvalId;
            startedAt = project.startedAt;
            practicalCompletionAt = project.practicalCompletionAt;
            handedOverAt = project.handedOverAt;
            defectsLiabilityEndsOn = project.defectsLiabilityEndsOn;
            closedAt = project.closedAt;
            cancellationReason = project.cancellationReason;
        }
    }
}
