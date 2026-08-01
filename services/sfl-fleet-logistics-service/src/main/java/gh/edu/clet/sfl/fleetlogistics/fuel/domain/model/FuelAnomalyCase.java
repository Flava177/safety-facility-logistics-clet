package gh.edu.clet.sfl.fleetlogistics.fuel.domain.model;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.RecordMetadata;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Accountable fuel exception workflow; closure is evidence and decision gated. */
public record FuelAnomalyCase(UUID id, String anomalyNumber, SiteCode siteCode, UUID transactionId,
        UUID logbookId, UUID vehicleId, UUID driverId, UUID tripId, Type type, Severity severity, boolean material,
        Status status, String assignee, Instant slaDueAt, String explanation, UUID evidenceId, Decision decision,
        String closureReason, int escalationLevel, List<String> detectedRules, RecordMetadata metadata) {
    public enum Type { DUPLICATE, MISSING_RECEIPT, LIMIT_EXCEEDED, TANK_CAPACITY, FUEL_PRODUCT, IDENTITY_MISMATCH, OUTSIDE_TRIP, VEHICLE_UNAVAILABLE, DRIVER_INELIGIBLE, ODOMETER_REGRESSION, ODOMETER_JUMP, ABNORMAL_CONSUMPTION, LOGBOOK_MISMATCH, VENDOR, UNUSUAL_PATTERN, MISSING_LOGBOOK, COST_VARIANCE, DAILY_LIMIT_EXCEEDED, MONTHLY_LIMIT_EXCEEDED, /* SRS-SFL-S168fuel-04: the card is not in the register, or not live. */ CARD_UNKNOWN, /* The card is assigned to a different vehicle than the one filled — the commonest fuel fraud. */ CARD_VEHICLE_MISMATCH, /* Over the ceiling set on the card itself, which overrides the site policy. */ CARD_LIMIT_EXCEEDED, CARD_DAILY_LIMIT_EXCEEDED, CARD_MONTHLY_LIMIT_EXCEEDED }
    public enum Severity { LOW, MEDIUM, HIGH, CRITICAL }
    public enum Status { DETECTED, ASSIGNED, UNDER_REVIEW, AWAITING_EXPLANATION, EXPLANATION_RECEIVED, APPROVED, REJECTED, ESCALATED, CLOSED, HELD, CANCELLED, REOPENED }
    public enum Decision { APPROVED, REJECTED }

    public FuelAnomalyCase {
        Objects.requireNonNull(id); Objects.requireNonNull(siteCode); Objects.requireNonNull(type);
        Objects.requireNonNull(severity); Objects.requireNonNull(status); Objects.requireNonNull(slaDueAt);
        Objects.requireNonNull(metadata); anomalyNumber = require(anomalyNumber, "anomalyNumber");
        detectedRules = detectedRules == null ? List.of() : List.copyOf(detectedRules);
        if (escalationLevel < 0) throw new IllegalArgumentException("escalationLevel cannot be negative");
    }
    public FuelAnomalyCase assign(String owner, RecordMetadata changed) { requireState(Status.DETECTED, Status.REOPENED); return copy(Status.ASSIGNED, require(owner,"assignee"), explanation, evidenceId, decision, closureReason, escalationLevel, changed); }
    public FuelAnomalyCase review(RecordMetadata changed) { requireState(Status.ASSIGNED, Status.EXPLANATION_RECEIVED); return copy(Status.UNDER_REVIEW, assignee, explanation, evidenceId, decision, closureReason, escalationLevel, changed); }
    public FuelAnomalyCase requestExplanation(RecordMetadata changed) { requireState(Status.UNDER_REVIEW); return copy(Status.AWAITING_EXPLANATION, assignee, explanation, evidenceId, decision, closureReason, escalationLevel, changed); }
    public FuelAnomalyCase explain(String text, UUID evidence, RecordMetadata changed) { requireState(Status.AWAITING_EXPLANATION); return copy(Status.EXPLANATION_RECEIVED, assignee, require(text,"explanation"), evidence, decision, closureReason, escalationLevel, changed); }
    public FuelAnomalyCase decide(Decision value, String reason, RecordMetadata changed) { requireState(Status.UNDER_REVIEW); return copy(value == Decision.APPROVED ? Status.APPROVED : Status.REJECTED, assignee, explanation, evidenceId, value, require(reason,"decision reason"), escalationLevel, changed); }
    public FuelAnomalyCase escalate(String reason, RecordMetadata changed) { require(reason,"escalation reason"); return copy(Status.ESCALATED, assignee, explanation, evidenceId, decision, reason, escalationLevel + 1, changed); }
    public FuelAnomalyCase close(String reason, UUID evidence, RecordMetadata changed) { requireState(Status.APPROVED, Status.REJECTED, Status.ESCALATED); if (explanation == null || decision == null || evidence == null) throw new IllegalStateException("explanation, decision and evidence are required for closure"); return copy(Status.CLOSED, assignee, explanation, evidence, decision, require(reason,"closure reason"), escalationLevel, changed); }
    public FuelAnomalyCase reopen(String reason, RecordMetadata changed) { requireState(Status.CLOSED); return copy(Status.REOPENED, assignee, explanation, evidenceId, decision, require(reason,"reopen reason"), escalationLevel, changed); }
    public FuelAnomalyCase reassign(String owner, RecordMetadata changed) { requireState(Status.ASSIGNED, Status.UNDER_REVIEW, Status.AWAITING_EXPLANATION, Status.EXPLANATION_RECEIVED, Status.HELD); return copy(Status.ASSIGNED, require(owner,"assignee"), explanation, evidenceId, decision, closureReason, escalationLevel, changed); }
    public FuelAnomalyCase hold(String reason, RecordMetadata changed) { requireState(Status.ASSIGNED, Status.UNDER_REVIEW, Status.AWAITING_EXPLANATION, Status.EXPLANATION_RECEIVED); return copy(Status.HELD, assignee, explanation, evidenceId, decision, require(reason,"hold reason"), escalationLevel, changed); }
    public FuelAnomalyCase resume(RecordMetadata changed) { requireState(Status.HELD); return copy(Status.UNDER_REVIEW, assignee, explanation, evidenceId, decision, closureReason, escalationLevel, changed); }
    public FuelAnomalyCase cancel(String reason, RecordMetadata changed) { requireState(Status.DETECTED, Status.ASSIGNED, Status.UNDER_REVIEW, Status.AWAITING_EXPLANATION, Status.EXPLANATION_RECEIVED, Status.HELD, Status.REOPENED); return copy(Status.CANCELLED, assignee, explanation, evidenceId, decision, require(reason,"cancellation reason"), escalationLevel, changed); }
    private FuelAnomalyCase copy(Status next, String owner, String explanation, UUID evidence, Decision decision, String closure, int level, RecordMetadata changed) { return new FuelAnomalyCase(id, anomalyNumber, siteCode, transactionId, logbookId, vehicleId, driverId, tripId, type, severity, material, next, owner, slaDueAt, explanation, evidence, decision, closure, level, detectedRules, changed); }
    private void requireState(Status... allowed) { if (java.util.Arrays.stream(allowed).noneMatch(s -> s == status)) throw new IllegalStateException("transition not allowed from " + status); }
    private static String require(String value, String field) { if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required"); return value.strip(); }
}
