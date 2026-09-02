package gh.edu.clet.sfl.safetysecurity.incident.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * A CAPA item against a {@link SecurityIncident} - SRS §D.9 step 4: "owner, due date and status".
 *
 * <p>A separate persisted entity referencing {@code incidentId}, mirroring how {@code VisitorApproval}
 * sits beside {@code VisitorVisit} rather than living inside it - CAPA items are created and updated
 * independently of the incident's own lifecycle, and {@link #mandatory} is exactly the fact
 * {@link SecurityIncident#close} needs counted, not something the incident aggregate needs to hold a
 * collection of.
 *
 * <p>{@link #blocksClosure()} is hard rule 1 in one place: mandatory and not yet in a terminal state.
 * "Overdue" (D.9: "Overdue actions flagged and escalated") is deliberately not a stored status - it is
 * a fact of {@link #dueDate} against the current date, computed at read time rather than drifting out
 * of sync with a stored flag nobody re-evaluates.
 */
public record CorrectiveAction(
        UUID id,
        UUID incidentId,
        String siteCode,
        String description,
        String ownerId,
        LocalDate dueDate,
        boolean mandatory,
        CapaStatus status,
        String verificationNotes,
        String createdBy,
        Instant createdAt,
        String resolvedBy,
        Instant resolvedAt) {

    public CorrectiveAction {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(incidentId, "incidentId is required");
        siteCode = normalizeSite(siteCode);
        require(description, "description");
        description = description.strip();
        require(ownerId, "ownerId");
        ownerId = ownerId.strip();
        Objects.requireNonNull(dueDate, "dueDate is required");
        Objects.requireNonNull(status, "status is required");
        verificationNotes = blankToNull(verificationNotes);
        require(createdBy, "createdBy");
        createdBy = createdBy.strip();
        Objects.requireNonNull(createdAt, "createdAt is required");
        resolvedBy = blankToNull(resolvedBy);
        if (status.isTerminal() != (resolvedAt != null)) {
            throw new IllegalArgumentException("resolvedAt must be set exactly when the status is terminal");
        }
    }

    public static CorrectiveAction open(UUID id, SecurityIncident incident, String description, String ownerId,
            LocalDate dueDate, boolean mandatory, String actorId, Instant at) {
        return new CorrectiveAction(id, incident.id(), incident.siteCode(), description, ownerId, dueDate,
                mandatory, CapaStatus.OPEN, null, actorId, at, null, null);
    }

    /** Work has started. */
    public CorrectiveAction startProgress(String actorId, Instant at) {
        CapaStatus next = status.transitionTo(CapaStatus.IN_PROGRESS);
        return new CorrectiveAction(id, incidentId, siteCode, description, ownerId, dueDate, mandatory, next,
                verificationNotes, createdBy, createdAt, null, null);
    }

    /**
     * Effectiveness verified and the action closed - SRS §D.9 step 7's "completion requires
     * effectiveness verification". Who is entitled to verify (the owner's supervisor, or an
     * independent officer - open question Q-163-5) is not resolved by the SRS; this method enforces
     * only that a note is on record, and {@code IncidentPermissionMatrix}/{@code INCIDENT_CAPA_VERIFY}
     * gate who may call it.
     */
    public CorrectiveAction verify(String verificationNotes, String actorId, Instant at) {
        require(verificationNotes, "verificationNotes");
        CapaStatus next = status.transitionTo(CapaStatus.VERIFIED);
        return new CorrectiveAction(id, incidentId, siteCode, description, ownerId, dueDate, mandatory, next,
                verificationNotes.strip(), createdBy, createdAt, actorId, at);
    }

    /** Withdrawn, with a reason - a mandatory action that turns out unnecessary still needs one on record. */
    public CorrectiveAction cancel(String reason, String actorId, Instant at) {
        require(reason, "reason");
        CapaStatus next = status.transitionTo(CapaStatus.CANCELLED);
        return new CorrectiveAction(id, incidentId, siteCode, description, ownerId, dueDate, mandatory, next,
                reason.strip(), createdBy, createdAt, actorId, at);
    }

    /** {@code true} while this action still counts against {@link SecurityIncident#close}'s gate. */
    public boolean blocksClosure() {
        return mandatory && !status.isTerminal();
    }

    /** {@code true} when the due date has passed and the action is still open - D.9's overdue flag, computed. */
    public boolean isOverdue(LocalDate today) {
        return !status.isTerminal() && today.isAfter(dueDate);
    }

    private static String normalizeSite(String siteCode) {
        require(siteCode, "siteCode");
        return siteCode.strip().toUpperCase(Locale.ROOT);
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
