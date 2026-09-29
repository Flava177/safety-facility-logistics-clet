package gh.edu.clet.sfl.facilities.construction.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.facilities.construction.application.ports.ConstructionRepository;
import gh.edu.clet.sfl.facilities.construction.domain.ConstructionProject;
import gh.edu.clet.sfl.facilities.construction.domain.ConstructionRefusal;
import gh.edu.clet.sfl.facilities.construction.domain.ProjectApproval;
import gh.edu.clet.sfl.facilities.construction.domain.VariationOrder;
import gh.edu.clet.sfl.facilities.construction.domain.policy.VariationEscalationPolicy;
import gh.edu.clet.sfl.facilities.shared.application.port.AuditPort;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditAction;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesErrorCode;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Variation orders and budget control - SRS-SFL-S176-03.
 *
 * <h2>Where escalation is decided</h2>
 *
 * At submission <em>and</em> at approval, by {@link VariationEscalationPolicy}. At submission, so a
 * variation that arrives while the project is already over the threshold - or while another is
 * waiting for escalation - is visibly held from the start ("when a further variation is submitted,
 * then it is held pending escalated approval"). At approval again, because the cumulative position
 * moves between the two: a variation that was fine when submitted can cross the line if two others
 * were approved in the meantime. An ordinary approval of a variation found to need escalation holds
 * it, audits {@code VARIATION_ESCALATION_REQUIRED}, publishes the escalation event and answers with
 * the error state - and the hold survives the refusal.
 *
 * <h2>Separation of duties</h2>
 *
 * Nobody approves or rejects a variation they submitted, at either authority level. V21 refuses it
 * too.
 *
 * <h2>The budget, traceably</h2>
 *
 * {@link #budget} is the baseline the approver signed plus each approved variation, listed one by one,
 * so the current budget can always be read back to the records that make it up. Nothing stores the
 * total; it cannot drift from its parts.
 */
@Service
public class VariationService {

    private final ConstructionContext context;
    private final ConstructionRepository repository;
    private final AuditPort audit;

    public VariationService(ConstructionContext context) {
        this.context = context;
        this.repository = context.repository();
        this.audit = context.audit();
    }

    @Transactional
    public VariationOrder submit(ConstructionCommands.SubmitVariation command) {
        ActorContext actor = command.actor();
        ConstructionProject project = context.requireProject(command.projectId());
        context.requireManage(actor, project, command.channel());
        if (!project.status().acceptsVariations()) {
            throw new FacilitiesException.InvalidStateTransitionException("A variation can be raised from approval "
                    + "until practical completion; " + project.projectReference() + " is " + project.status() + ".");
        }
        if (command.currency() != null && !command.currency().isBlank()
                && !command.currency().strip().equalsIgnoreCase(project.currency())) {
            throw new FacilitiesException.ValidationFailedException(
                    "A variation must be in the project's currency, " + project.currency() + ".");
        }
        Instant at = context.now();
        VariationOrder variation = VariationOrder.submit(UUID.randomUUID(),
                repository.nextVariationReference(project.siteCode()), project, command.changeDescription(),
                command.costDelta(), project.currency(), command.justification(), actor.actorId(), at,
                command.channel(), actor.correlationId());
        VariationOrder saved = repository.saveVariation(variation);
        audit.record(actor, command.channel(), AuditAction.VARIATION_SUBMITTED, "VariationOrder",
                saved.id().toString(), saved.siteCode(), null, saved);
        VariationEscalationPolicy.Assessment assessment = assess(project, saved);
        if (assessment.escalationRequired()) {
            saved = hold(project, saved, assessment, actor, command.channel());
        }
        return saved;
    }

    /** The ordinary decision. Refused with VARIATION_ESCALATION_REQUIRED for a variation that needs more. */
    @Transactional(noRollbackFor = ConstructionRefusal.class)
    public VariationOrder decide(ConstructionCommands.DecideVariation command) {
        ActorContext actor = command.actor();
        VariationOrder variation = requireVariation(command.variationId());
        ConstructionProject project = context.requireProject(variation.projectId());
        context.authorization().require(actor, SflPermission.FACILITIES_VARIATION_APPROVE, project.siteCode(),
                command.channel(), "VariationOrder", variation.id().toString());
        requireNotSubmitter(actor, variation, command.channel());
        Instant at = context.now();
        if (!command.approve()) {
            VariationOrder rejected = repository.saveVariation(variation.reject(actor.actorId(), command.note(), at,
                    command.channel(), actor.correlationId()));
            audit.record(actor, command.channel(), AuditAction.VARIATION_REJECTED, "VariationOrder",
                    rejected.id().toString(), rejected.siteCode(), variation, rejected);
            return rejected;
        }
        if (variation.status() != VariationOrder.Status.SUBMITTED) {
            throw new FacilitiesException.InvalidStateTransitionException(
                    "Variation " + variation.variationReference() + " has already been decided.");
        }
        VariationEscalationPolicy.Assessment assessment = assess(project, variation);
        if (variation.escalationRequired() || assessment.escalationRequired()) {
            VariationOrder held = assessment.escalationRequired()
                    ? hold(project, variation, assessment, actor, command.channel()) : variation;
            throw new ConstructionRefusal(FacilitiesErrorCode.VARIATION_ESCALATION_REQUIRED,
                    held.escalationReason() == null ? assessment.reason() : held.escalationReason());
        }
        VariationOrder approved = repository.saveVariation(variation.approve(actor.actorId(), command.note(),
                assessment.cumulativePercentAfter(), at, command.channel(), actor.correlationId()));
        return recordApproved(project, variation, approved, AuditAction.VARIATION_APPROVED, actor, command.channel());
    }

    /**
     * The escalated sign-off. Also valid for a variation that was never held: an escalated approver may
     * always decide at the higher level. Refused while a <em>different</em> held variation is waiting,
     * because that one was first and the budget position it creates is what this decision stands on.
     */
    @Transactional(noRollbackFor = ConstructionRefusal.class)
    public VariationOrder approveEscalated(ConstructionCommands.EscalatedApproval command) {
        ActorContext actor = command.actor();
        VariationOrder variation = requireVariation(command.variationId());
        ConstructionProject project = context.requireProject(variation.projectId());
        context.authorization().require(actor, SflPermission.FACILITIES_VARIATION_ESCALATED_APPROVE,
                project.siteCode(), command.channel(), "VariationOrder", variation.id().toString());
        requireNotSubmitter(actor, variation, command.channel());
        List<VariationOrder> all = repository.findVariations(project.id());
        VariationOrder earlierHeld = all.stream()
                .filter(other -> !other.id().equals(variation.id()) && other.awaitingEscalation()
                        && other.submittedAt().isBefore(variation.submittedAt()))
                .min(Comparator.comparing(VariationOrder::submittedAt)).orElse(null);
        if (earlierHeld != null) {
            throw new ConstructionRefusal(FacilitiesErrorCode.VARIATION_ESCALATION_REQUIRED,
                    "Variation " + earlierHeld.variationReference() + " was held first and must be decided before "
                            + variation.variationReference() + ".");
        }
        BigDecimal percent = VariationEscalationPolicy.percentOf(
                VariationEscalationPolicy.approvedTotal(all).add(variation.costDelta()), originalBaseline(project));
        VariationOrder approved = repository.saveVariation(variation.approveEscalated(actor.actorId(), command.note(),
                percent, context.now(), command.channel(), actor.correlationId()));
        return recordApproved(project, variation, approved, AuditAction.VARIATION_ESCALATED_APPROVAL_RECORDED, actor,
                command.channel());
    }

    // =============================================================================================
    // Queries
    // =============================================================================================

    @Transactional(readOnly = true)
    public List<VariationOrder> variations(UUID projectId, ActorContext actor, SourceChannel channel) {
        ConstructionProject project = context.requireProject(projectId);
        context.requireRead(actor, project.siteCode(), channel, "ConstructionProject", projectId.toString());
        return repository.findVariations(project.id()).stream()
                .sorted(Comparator.comparing(VariationOrder::submittedAt)).toList();
    }

    @Transactional(readOnly = true)
    public VariationOrder find(UUID variationId, ActorContext actor, SourceChannel channel) {
        VariationOrder variation = requireVariation(variationId);
        context.requireRead(actor, variation.siteCode(), channel, "VariationOrder", variationId.toString());
        return variation;
    }

    /**
     * @param originalBaseline the baseline the accountable approver signed
     * @param approvedVariations each approved variation - the only records that move the budget
     * @param pendingVariations submitted and not yet decided; shown, and excluded from the total
     */
    public record BudgetBreakdown(UUID projectId, String projectReference, String currency,
            BigDecimal originalBaseline, int baselineRevision, List<VariationOrder> approvedVariations,
            BigDecimal approvedVariationTotal, BigDecimal currentBudget, BigDecimal cumulativeVariationPercent,
            BigDecimal escalationThresholdPercent, List<VariationOrder> pendingVariations) {
    }

    @Transactional(readOnly = true)
    public BudgetBreakdown budget(UUID projectId, ActorContext actor, SourceChannel channel) {
        ConstructionProject project = context.requireProject(projectId);
        context.requireRead(actor, project.siteCode(), channel, "ConstructionProject", projectId.toString());
        return breakdown(project);
    }

    BudgetBreakdown breakdown(ConstructionProject project) {
        List<VariationOrder> all = repository.findVariations(project.id()).stream()
                .sorted(Comparator.comparing(VariationOrder::submittedAt)).toList();
        List<VariationOrder> approved = all.stream()
                .filter(variation -> variation.status() == VariationOrder.Status.APPROVED).toList();
        List<VariationOrder> pending = all.stream()
                .filter(variation -> variation.status() == VariationOrder.Status.SUBMITTED).toList();
        BigDecimal baseline = originalBaseline(project);
        BigDecimal total = VariationEscalationPolicy.approvedTotal(approved);
        return new BudgetBreakdown(project.id(), project.projectReference(), project.currency(), baseline,
                project.baselineRevision(), approved, total, baseline == null ? null : baseline.add(total),
                VariationEscalationPolicy.percentOf(total, baseline),
                context.configuration().escalationThresholdPercent(project.siteCode()), pending);
    }

    // =============================================================================================
    // Internals
    // =============================================================================================

    /** The approved baseline - what the threshold is measured against. The current baseline if unapproved. */
    BigDecimal originalBaseline(ConstructionProject project) {
        return repository.findApprovals(project.id()).stream()
                .filter(approval -> approval.id().equals(project.approvalId()))
                .map(ProjectApproval::baselineAmount)
                .findFirst()
                .orElse(project.budgetBaseline());
    }

    private VariationEscalationPolicy.Assessment assess(ConstructionProject project, VariationOrder variation) {
        return VariationEscalationPolicy.assess(variation, originalBaseline(project),
                repository.findVariations(project.id()), context.configuration().escalationThresholdPercent(
                        project.siteCode()));
    }

    private VariationOrder hold(ConstructionProject project, VariationOrder variation,
            VariationEscalationPolicy.Assessment assessment, ActorContext actor, SourceChannel channel) {
        if (variation.escalationRequired()) {
            return variation;
        }
        VariationOrder held = repository.saveVariation(variation.holdForEscalation(assessment.reason(),
                assessment.cumulativePercentAfter(), actor.actorId(), context.now(), channel, actor.correlationId()));
        audit.record(actor, channel, AuditAction.VARIATION_ESCALATION_REQUIRED, "VariationOrder",
                held.id().toString(), held.siteCode(), variation, held);
        context.publish(ConstructionEvents.VARIATION_ESCALATION_REQUIRED, "VariationOrder", held.id(), held.siteCode(),
                actor, ConstructionEvents.payload("projectId", project.id(), "projectReference",
                        project.projectReference(), "variationId", held.id(), "variationReference",
                        held.variationReference(), "costDelta", held.costDelta(), "currency", held.currency(),
                        "cumulativePercentAfter", assessment.cumulativePercentAfter(), "thresholdPercent",
                        context.configuration().escalationThresholdPercent(project.siteCode()), "blockedByPending",
                        assessment.blocked()));
        return held;
    }

    private VariationOrder recordApproved(ConstructionProject project, VariationOrder before, VariationOrder approved,
            AuditAction action, ActorContext actor, SourceChannel channel) {
        audit.record(actor, channel, action, "VariationOrder", approved.id().toString(), approved.siteCode(), before,
                approved);
        BudgetBreakdown budget = breakdown(project);
        context.publish(ConstructionEvents.VARIATION_APPROVED, "VariationOrder", approved.id(), approved.siteCode(),
                actor, ConstructionEvents.payload("projectId", project.id(), "projectReference",
                        project.projectReference(), "variationId", approved.id(), "variationReference",
                        approved.variationReference(), "costDelta", approved.costDelta(), "currency",
                        approved.currency(), "escalated", approved.escalatedApprovedBy() != null, "currentBudget",
                        budget.currentBudget(), "cumulativePercent", budget.cumulativeVariationPercent()));
        return approved;
    }

    private void requireNotSubmitter(ActorContext actor, VariationOrder variation, SourceChannel channel) {
        if (actor.actorId().equals(variation.submittedBy())) {
            audit.recordDenial(actor, channel, "VariationOrder", variation.id().toString(), variation.siteCode(),
                    "A variation may not be decided by the person who submitted it");
            throw new FacilitiesException.UnauthorizedApprovalException(
                    "A variation must be decided by someone other than its submitter.");
        }
    }

    private VariationOrder requireVariation(UUID id) {
        return repository.findVariation(id)
                .orElseThrow(() -> new FacilitiesException.RecordNotFoundException("Variation order", id));
    }
}
