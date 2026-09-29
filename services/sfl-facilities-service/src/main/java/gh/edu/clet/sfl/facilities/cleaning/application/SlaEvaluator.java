package gh.edu.clet.sfl.facilities.cleaning.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.cleaning.domain.AssigneeType;
import gh.edu.clet.sfl.facilities.cleaning.domain.CleaningTask;
import gh.edu.clet.sfl.facilities.cleaning.domain.SlaBreach;
import gh.edu.clet.sfl.facilities.cleaning.domain.VendorSlaTerms;
import gh.edu.clet.sfl.facilities.cleaning.domain.policy.SlaCompliancePolicy;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditAction;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Turns {@link SlaCompliancePolicy} findings into recorded breaches on a vendor's scorecard -
 * SRS-SFL-S169-03 "task lifecycle timestamps captured, compared to contracted SLA, compliance computed,
 * vendor scorecard updated".
 *
 * <p>Called at every moment a measurement becomes available - a vendor task starting, completing, being
 * rated - and by the sweep for reactive requests nobody has attended yet. Each breach type is recorded
 * at most once per task ({@code ux_cleaning_sla_breaches_task_type}), so evaluating the same task twice
 * never counts it twice.
 *
 * <p>The terms applied are the version in force when the task was raised, not the current one: a
 * contract renegotiated on the 15th judges work raised on the 14th by the terms of the 14th.
 */
@Component
public class SlaEvaluator {

    private final CleaningSupport support;

    public SlaEvaluator(CleaningSupport support) {
        this.support = support;
    }

    /** Time-based breaches for a vendor task as it stands now. A no-op for in-house work. */
    List<SlaBreach> evaluateTimes(CleaningTask task, ActorContext actor, SourceChannel channel) {
        Optional<VendorSlaTerms> terms = termsFor(task);
        if (terms.isEmpty()) {
            return List.of();
        }
        List<SlaBreach> recorded = new ArrayList<>();
        for (SlaCompliancePolicy.Finding finding : SlaCompliancePolicy.timeFindings(task, terms.get(), support.now())) {
            record(task, terms.get(), finding, actor, channel).ifPresent(recorded::add);
        }
        return List.copyOf(recorded);
    }

    /** A rating below the vendor's contracted quality floor. */
    Optional<SlaBreach> evaluateRating(CleaningTask task, int rating, ActorContext actor, SourceChannel channel) {
        Optional<VendorSlaTerms> terms = termsFor(task);
        if (terms.isEmpty()) {
            return Optional.empty();
        }
        return SlaCompliancePolicy.quality(rating, terms.get())
                .flatMap(finding -> record(task, terms.get(), finding, actor, channel));
    }

    /** The version in force when the task was raised, or empty for staff work or a vendor with no terms. */
    Optional<VendorSlaTerms> termsFor(CleaningTask task) {
        if (task.assigneeType() != AssigneeType.VENDOR || task.vendorId() == null) {
            return Optional.empty();
        }
        List<VendorSlaTerms> history = support.repository().findTermsHistory(task.vendorId());
        Optional<VendorSlaTerms> atRaise = history.stream().filter(terms -> terms.inForceAt(task.requestedAt()))
                .findFirst();
        // A vendor given terms only after a task was raised is still held to its earliest terms rather
        // than to none: "no terms applied" would read on the scorecard as full compliance.
        return atRaise.isPresent() ? atRaise : history.stream().findFirst();
    }

    private Optional<SlaBreach> record(CleaningTask task, VendorSlaTerms terms, SlaCompliancePolicy.Finding finding,
            ActorContext actor, SourceChannel channel) {
        if (support.repository().existsBreach(task.id(), finding.type())) {
            return Optional.empty();
        }
        SlaBreach breach = support.repository().saveBreach(SlaBreach.record(UUID.randomUUID(), task, terms,
                finding.type(), finding.contracted(), finding.actual(), finding.basis(), actor.actorId(),
                support.now(), channel, actor.correlationId()));
        support.audit().record(actor, channel, AuditAction.CLEANING_SLA_BREACH_RECORDED, "CleaningSlaBreach",
                breach.id().toString(), breach.siteCode(), null, breach);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("breachId", breach.id().toString());
        payload.put("vendorId", breach.vendorId().toString());
        payload.put("taskId", breach.taskId().toString());
        payload.put("taskNumber", task.taskNumber());
        payload.put("slaTermsId", breach.slaTermsId().toString());
        payload.put("slaTermsVersion", terms.version());
        payload.put("breachType", breach.type().name());
        payload.put("contractedValue", breach.contractedValue().toPlainString());
        payload.put("actualValue", breach.actualValue().toPlainString());
        payload.put("origin", task.origin().name());
        support.publish("sfl.ifimp.cleaning-sla-breach-recorded.v1", "CleaningSlaBreach", breach.id(),
                breach.siteCode(), payload, actor);
        return Optional.of(breach);
    }
}
