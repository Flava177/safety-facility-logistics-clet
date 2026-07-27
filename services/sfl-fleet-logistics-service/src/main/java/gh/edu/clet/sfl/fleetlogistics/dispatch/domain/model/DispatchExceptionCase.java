package gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.RecordMetadata;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Accountable dispatch exception workflow. Custody gaps, receipt variances, scan mismatches, undelivered
 * items and return discrepancies each open a case; closure is explanation, decision, closure-reason and
 * evidence gated. While an open case exists for a dispatch/item, related custody/dispatch closure is
 * blocked. The lifecycle mirrors the shared fleet/fuel workflow.
 */
public record DispatchExceptionCase(UUID id, String exceptionNumber, SiteCode siteCode, String occurrenceKey,
        UUID courierItemId, UUID dispatchId, UUID handoverId, UUID receiptId, UUID tripId, Type type,
        Severity severity, boolean securityRelevant, Status status, String assignee, Instant slaDueAt,
        String explanation, UUID evidenceId, Decision decision, String closureReason, int escalationLevel,
        List<String> detectedRules, RecordMetadata metadata) {

    public enum Type { UNREGISTERED_ITEM, CUSTODY_GAP, RECEIPT_VARIANCE, SCAN_MISMATCH, UNDELIVERED_ITEM,
        RETURN_DISCREPANCY }
    public enum Severity { LOW, MEDIUM, HIGH, CRITICAL }
    public enum Status { DETECTED, ASSIGNED, UNDER_REVIEW, AWAITING_EXPLANATION, EXPLANATION_RECEIVED, APPROVED,
        REJECTED, ESCALATED, CLOSED, HELD, CANCELLED, REOPENED }
    public enum Decision { APPROVED, REJECTED }

    public DispatchExceptionCase {
        Objects.requireNonNull(id); Objects.requireNonNull(siteCode); Objects.requireNonNull(type);
        Objects.requireNonNull(severity); Objects.requireNonNull(status); Objects.requireNonNull(slaDueAt);
        Objects.requireNonNull(metadata);
        exceptionNumber = require(exceptionNumber, "exceptionNumber");
        occurrenceKey = require(occurrenceKey, "occurrenceKey");
        detectedRules = detectedRules == null ? List.of() : List.copyOf(detectedRules);
        if (escalationLevel < 0) throw new IllegalArgumentException("escalationLevel cannot be negative");
    }

    public boolean open() {
        return status != Status.CLOSED && status != Status.CANCELLED;
    }

    public DispatchExceptionCase assign(String owner, RecordMetadata changed) {
        requireState(Status.DETECTED, Status.REOPENED);
        return copy(Status.ASSIGNED, require(owner, "assignee"), explanation, evidenceId, decision, closureReason, escalationLevel, changed);
    }
    public DispatchExceptionCase reassign(String owner, RecordMetadata changed) {
        requireState(Status.ASSIGNED, Status.UNDER_REVIEW, Status.AWAITING_EXPLANATION, Status.EXPLANATION_RECEIVED, Status.HELD);
        return copy(Status.ASSIGNED, require(owner, "assignee"), explanation, evidenceId, decision, closureReason, escalationLevel, changed);
    }
    public DispatchExceptionCase review(RecordMetadata changed) {
        requireState(Status.ASSIGNED, Status.EXPLANATION_RECEIVED);
        return copy(Status.UNDER_REVIEW, assignee, explanation, evidenceId, decision, closureReason, escalationLevel, changed);
    }
    public DispatchExceptionCase requestExplanation(RecordMetadata changed) {
        requireState(Status.UNDER_REVIEW);
        return copy(Status.AWAITING_EXPLANATION, assignee, explanation, evidenceId, decision, closureReason, escalationLevel, changed);
    }
    public DispatchExceptionCase explain(String text, UUID evidence, RecordMetadata changed) {
        requireState(Status.AWAITING_EXPLANATION);
        return copy(Status.EXPLANATION_RECEIVED, assignee, require(text, "explanation"), evidence, decision, closureReason, escalationLevel, changed);
    }
    public DispatchExceptionCase decide(Decision value, String reason, RecordMetadata changed) {
        requireState(Status.UNDER_REVIEW);
        return copy(value == Decision.APPROVED ? Status.APPROVED : Status.REJECTED, assignee, explanation, evidenceId, value, require(reason, "decision reason"), escalationLevel, changed);
    }
    public DispatchExceptionCase escalate(String reason, RecordMetadata changed) {
        if (status == Status.CLOSED || status == Status.CANCELLED) throw new IllegalStateException("transition not allowed from " + status);
        return copy(Status.ESCALATED, assignee, explanation, evidenceId, decision, require(reason, "escalation reason"), escalationLevel + 1, changed);
    }
    public DispatchExceptionCase hold(String reason, RecordMetadata changed) {
        requireState(Status.ASSIGNED, Status.UNDER_REVIEW, Status.AWAITING_EXPLANATION, Status.EXPLANATION_RECEIVED);
        return copy(Status.HELD, assignee, explanation, evidenceId, decision, require(reason, "hold reason"), escalationLevel, changed);
    }
    public DispatchExceptionCase resume(RecordMetadata changed) {
        requireState(Status.HELD);
        return copy(Status.UNDER_REVIEW, assignee, explanation, evidenceId, decision, closureReason, escalationLevel, changed);
    }
    public DispatchExceptionCase cancel(String reason, RecordMetadata changed) {
        requireState(Status.DETECTED, Status.ASSIGNED, Status.UNDER_REVIEW, Status.AWAITING_EXPLANATION, Status.EXPLANATION_RECEIVED, Status.HELD, Status.REOPENED);
        return copy(Status.CANCELLED, assignee, explanation, evidenceId, decision, require(reason, "cancellation reason"), escalationLevel, changed);
    }
    public DispatchExceptionCase close(String reason, UUID evidence, RecordMetadata changed) {
        requireState(Status.APPROVED, Status.REJECTED, Status.ESCALATED);
        if (explanation == null || decision == null || evidence == null) {
            throw new IllegalStateException("explanation, decision and evidence are required for closure");
        }
        return copy(Status.CLOSED, assignee, explanation, evidence, decision, require(reason, "closure reason"), escalationLevel, changed);
    }
    public DispatchExceptionCase reopen(String reason, RecordMetadata changed) {
        requireState(Status.CLOSED);
        return copy(Status.REOPENED, assignee, explanation, evidenceId, decision, require(reason, "reopen reason"), escalationLevel, changed);
    }

    private DispatchExceptionCase copy(Status next, String owner, String explanationValue, UUID evidence,
            Decision decisionValue, String closure, int level, RecordMetadata changed) {
        return new DispatchExceptionCase(id, exceptionNumber, siteCode, occurrenceKey, courierItemId, dispatchId,
                handoverId, receiptId, tripId, type, severity, securityRelevant, next, owner, slaDueAt,
                explanationValue, evidence, decisionValue, closure, level, detectedRules, changed);
    }
    private void requireState(Status... allowed) {
        for (Status s : allowed) if (s == status) return;
        throw new IllegalStateException("transition not allowed from " + status);
    }
    private static String require(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.strip();
    }
}
