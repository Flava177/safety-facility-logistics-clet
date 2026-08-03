package gh.edu.clet.sfl.fleetlogistics.fuel.domain.model;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.RecordMetadata;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Driver-owned journey record with explicit, locked review transitions. */
public record DriverLogbook(UUID id, String logbookNumber, SiteCode siteCode, UUID driverId, UUID vehicleId,
        UUID tripId, LocalDate journeyDate, Instant startTime, Instant endTime, String origin, String destination,
        String routeNotes, UseClassification useClassification, String purpose, String passengerLoadNotes,
        long startOdometer, Long endOdometer, boolean declarationAccepted, UUID evidenceId, Status status,
        String reviewComment, String transitionReason, Instant submittedAt, Instant approvedAt, RecordMetadata metadata) {

    public enum UseClassification { OFFICIAL, PRIVATE, OPERATIONAL }
    public enum Status { DRAFT, SUBMITTED, UNDER_REVIEW, RETURNED, RESUBMITTED, APPROVED, REOPENED, CANCELLED }

    public DriverLogbook {
        Objects.requireNonNull(id); Objects.requireNonNull(siteCode); Objects.requireNonNull(driverId);
        Objects.requireNonNull(vehicleId); Objects.requireNonNull(journeyDate); Objects.requireNonNull(startTime);
        Objects.requireNonNull(useClassification); Objects.requireNonNull(status); Objects.requireNonNull(metadata);
        logbookNumber = require(logbookNumber, "logbookNumber"); origin = require(origin, "origin");
        destination = require(destination, "destination"); purpose = require(purpose, "purpose");
        if (endTime != null && endTime.isBefore(startTime)) throw new IllegalArgumentException("endTime cannot precede startTime");
        if (startOdometer < 0 || (endOdometer != null && endOdometer < startOdometer)) throw new IllegalArgumentException("logbook odometer regresses");
    }

    public long distance() { return endOdometer == null ? 0 : endOdometer - startOdometer; }

    public DriverLogbook submit(Instant now, RecordMetadata changed) {
        requireState(Status.DRAFT, Status.RETURNED, Status.REOPENED);
        if (!declarationAccepted || endTime == null || endOdometer == null) throw new IllegalStateException("completed journey and driver declaration are required");
        Status next = status == Status.RETURNED ? Status.RESUBMITTED : Status.SUBMITTED;
        return copy(next, reviewComment, "Submitted", now, null, changed);
    }
    public DriverLogbook amend(UUID driverId, UUID vehicleId, UUID tripId, LocalDate journeyDate, Instant startTime,
            Instant endTime, String origin, String destination, String routeNotes, UseClassification useClassification,
            String purpose, String passengerLoadNotes, long startOdometer, Long endOdometer,
            boolean declarationAccepted, UUID evidenceId, RecordMetadata changed) {
        requireState(Status.DRAFT, Status.RETURNED, Status.REOPENED);
        return new DriverLogbook(id, logbookNumber, siteCode, driverId, vehicleId, tripId, journeyDate, startTime,
                endTime, origin, destination, routeNotes, useClassification, purpose, passengerLoadNotes,
                startOdometer, endOdometer, declarationAccepted, evidenceId, status, reviewComment,
                "Draft amended", submittedAt, approvedAt, changed);
    }
    public DriverLogbook startReview(RecordMetadata changed) { requireState(Status.SUBMITTED, Status.RESUBMITTED); return copy(Status.UNDER_REVIEW, reviewComment, "Review started", submittedAt, null, changed); }
    public DriverLogbook returned(String comment, RecordMetadata changed) { requireState(Status.UNDER_REVIEW); return copy(Status.RETURNED, require(comment,"reviewComment"), "Correction requested", submittedAt, null, changed); }
    public DriverLogbook approved(Instant now, String comment, RecordMetadata changed) { requireState(Status.UNDER_REVIEW); return copy(Status.APPROVED, comment, "Approved", submittedAt, now, changed); }
    public DriverLogbook reopened(String reason, RecordMetadata changed) { requireState(Status.APPROVED); return copy(Status.REOPENED, reviewComment, require(reason,"reason"), submittedAt, approvedAt, changed); }
    public DriverLogbook cancelled(String reason, RecordMetadata changed) { requireState(Status.DRAFT, Status.SUBMITTED, Status.RETURNED); return copy(Status.CANCELLED, reviewComment, require(reason,"reason"), submittedAt, approvedAt, changed); }

    private DriverLogbook copy(Status next, String comment, String reason, Instant submitted, Instant approved, RecordMetadata changed) {
        return new DriverLogbook(id, logbookNumber, siteCode, driverId, vehicleId, tripId, journeyDate, startTime,
                endTime, origin, destination, routeNotes, useClassification, purpose, passengerLoadNotes,
                startOdometer, endOdometer, declarationAccepted, evidenceId, next, comment, reason, submitted, approved, changed);
    }
    private void requireState(Status... allowed) { if (java.util.Arrays.stream(allowed).noneMatch(s -> s == status)) throw new IllegalStateException("transition not allowed from " + status); }
    private static String require(String value, String field) { if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required"); return value.strip(); }
}
