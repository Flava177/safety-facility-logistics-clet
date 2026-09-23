package gh.edu.clet.sfl.safetysecurity.intrusion.domain.model;

import gh.edu.clet.sfl.safetysecurity.intrusion.domain.exception.IntrusionErrorCode;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.exception.IntrusionException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The SOC-queue alarm - SRS-SFL-S162-02/03/04. Doubles as both the normalised alarm event and the
 * queue item requiring acknowledgement: unlike S160a, where an {@code AccessEvent} log and an
 * {@code AccessException} queue item are separate because most events never become an exception,
 * here the SRS's own language treats every zone-alarm/tamper/device-fault signal as "raised to the
 * SOC queue" directly (S162-02), so one aggregate carries both concerns.
 *
 * <p>A repeating signal for the same open alarm is coalesced into it ({@link #coalesce}) rather than
 * raising a new alarm - {@code signalCount}/{@code lastSignalAt} track that, and {@code
 * IntrusionIngestionService} flags a flapping panel by moving {@code status} to {@link
 * AlarmStatus#COALESCED} once the flap threshold is crossed, so a faulty device cannot flood the SOC
 * (SRS-SFL-S162-04 renumbered in the summary table as S162-04's backpressure clause).
 *
 * @param evidenceRef a soft reference to CCTV (S161) evidence, held by value (a plain string/id) -
 *        S162 has no compile-time dependency on the cctv module, exactly as it has none on incident;
 *        see {@code IntrusionIncidentSeedingPort} for how the incident link is made instead.
 */
public record IntrusionAlarm(UUID id, String siteCode, String panelId, String zoneCode, AlarmType alarmType,
        AlarmSeverity severity, AlarmStatus status, int signalCount, Instant firstSignalAt, Instant lastSignalAt,
        Instant ackDueAt, Instant acknowledgedAt, String acknowledgedBy, Instant escalatedAt, String escalatedTo,
        Instant resolvedAt, String resolvedBy, String evidenceRef, UUID seededIncidentId, RecordMetadata metadata) {

    public IntrusionAlarm {
        Objects.requireNonNull(id, "id is required");
        require(siteCode, "siteCode");
        require(panelId, "panelId");
        require(zoneCode, "zoneCode");
        Objects.requireNonNull(alarmType, "alarmType is required");
        Objects.requireNonNull(severity, "severity is required");
        Objects.requireNonNull(status, "status is required");
        if (signalCount < 1) {
            throw new IllegalArgumentException("signalCount must be at least 1");
        }
        Objects.requireNonNull(firstSignalAt, "firstSignalAt is required");
        Objects.requireNonNull(lastSignalAt, "lastSignalAt is required");
        acknowledgedBy = blankToNull(acknowledgedBy);
        escalatedTo = blankToNull(escalatedTo);
        resolvedBy = blankToNull(resolvedBy);
        evidenceRef = blankToNull(evidenceRef);
        Objects.requireNonNull(metadata, "metadata is required");
    }

    public static IntrusionAlarm raise(UUID id, String siteCode, String panelId, String zoneCode,
            AlarmType alarmType, AlarmSeverity severity, Instant occurredAt, Instant ackDueAt, String actorId,
            Instant at, SourceChannel channel, String correlationId) {
        return new IntrusionAlarm(id, siteCode, panelId, zoneCode, alarmType, severity, AlarmStatus.RAISED, 1,
                occurredAt, occurredAt, ackDueAt, null, null, null, null, null, null, null, null,
                RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    /** SRS-SFL-S162-04's backpressure clause: a repeat signal for this still-open alarm bumps the
     * count and the last-seen time rather than raising a new alarm. */
    public IntrusionAlarm coalesce(Instant signalAt, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        requireOpen();
        return new IntrusionAlarm(id, siteCode, panelId, zoneCode, alarmType, severity, status, signalCount + 1,
                firstSignalAt, signalAt, ackDueAt, acknowledgedAt, acknowledgedBy, escalatedAt, escalatedTo,
                resolvedAt, resolvedBy, evidenceRef, seededIncidentId,
                metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    /** A flapping panel: the alarm stays visible but is explicitly flagged as coalesced, and the
     * escalation sweep skips it (see {@code IntrusionAlarmService}) so a faulty device cannot keep
     * re-escalating itself. */
    public IntrusionAlarm markFlapping(String actorId, Instant at, SourceChannel channel, String correlationId) {
        requireOpen();
        return new IntrusionAlarm(id, siteCode, panelId, zoneCode, alarmType, severity, AlarmStatus.COALESCED,
                signalCount, firstSignalAt, lastSignalAt, ackDueAt, acknowledgedAt, acknowledgedBy, escalatedAt,
                escalatedTo, resolvedAt, resolvedBy, evidenceRef, seededIncidentId,
                metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    public IntrusionAlarm acknowledge(String actorId, Instant at, SourceChannel channel, String correlationId) {
        requireOpen();
        return new IntrusionAlarm(id, siteCode, panelId, zoneCode, alarmType, severity, AlarmStatus.ACKNOWLEDGED,
                signalCount, firstSignalAt, lastSignalAt, ackDueAt, at, actorId, escalatedAt, escalatedTo, resolvedAt,
                resolvedBy, evidenceRef, seededIncidentId, metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    /** Reached only from the auto-escalation sweep - SRS-SFL-S162-02: "escalates automatically per the
     * configured rule". */
    public IntrusionAlarm escalate(String escalatedToRole, Instant at, SourceChannel channel, String correlationId) {
        requireOpen();
        return new IntrusionAlarm(id, siteCode, panelId, zoneCode, alarmType, severity, AlarmStatus.ESCALATED,
                signalCount, firstSignalAt, lastSignalAt, ackDueAt, acknowledgedAt, acknowledgedBy, at,
                escalatedToRole, resolvedAt, resolvedBy, evidenceRef, seededIncidentId,
                metadata.modifiedBy("SYSTEM-ESCALATION-SWEEP", at, channel, correlationId));
    }

    public IntrusionAlarm resolve(String actorId, Instant at, SourceChannel channel, String correlationId) {
        requireOpen();
        return new IntrusionAlarm(id, siteCode, panelId, zoneCode, alarmType, severity, AlarmStatus.RESOLVED,
                signalCount, firstSignalAt, lastSignalAt, ackDueAt, acknowledgedAt, acknowledgedBy, escalatedAt,
                escalatedTo, at, actorId, evidenceRef, seededIncidentId,
                metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    /** SRS-SFL-S162-03: "an acknowledged alarm can be paired with surveillance (S161) evidence". */
    public IntrusionAlarm linkEvidence(String reference, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        requireAcknowledgedOrLater();
        return new IntrusionAlarm(id, siteCode, panelId, zoneCode, alarmType, severity, status, signalCount,
                firstSignalAt, lastSignalAt, ackDueAt, acknowledgedAt, acknowledgedBy, escalatedAt, escalatedTo,
                resolvedAt, resolvedBy, reference, seededIncidentId,
                metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    /** SRS-SFL-S162-03: "escalated into a security incident (S163) with a full trail". */
    public IntrusionAlarm linkIncident(UUID incidentId, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        requireAcknowledgedOrLater();
        return new IntrusionAlarm(id, siteCode, panelId, zoneCode, alarmType, severity, status, signalCount,
                firstSignalAt, lastSignalAt, ackDueAt, acknowledgedAt, acknowledgedBy, escalatedAt, escalatedTo,
                resolvedAt, resolvedBy, evidenceRef, incidentId, metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    public boolean isOpen() {
        return status == AlarmStatus.RAISED || status == AlarmStatus.ACKNOWLEDGED || status == AlarmStatus.ESCALATED
                || status == AlarmStatus.COALESCED;
    }

    public boolean isOverdue(Instant now) {
        return (status == AlarmStatus.RAISED) && ackDueAt != null && !ackDueAt.isAfter(now);
    }

    private void requireOpen() {
        if (!isOpen()) {
            throw IntrusionException.of(IntrusionErrorCode.INTRUSION_ALARM_ALREADY_CLOSED);
        }
    }

    private void requireAcknowledgedOrLater() {
        if (status == AlarmStatus.RAISED) {
            throw IntrusionException.of(IntrusionErrorCode.INTRUSION_ALARM_NOT_ACKNOWLEDGED);
        }
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
