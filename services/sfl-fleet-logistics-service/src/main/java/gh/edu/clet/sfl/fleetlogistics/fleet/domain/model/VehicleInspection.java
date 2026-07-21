package gh.edu.clet.sfl.fleetlogistics.fleet.domain.model;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.InvalidStateTransitionException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * A vehicle inspection.
 *
 * <p>Traced to SRS-SFL-S166-01 (an inspection is an operational record carrying service status and
 * availability evidence) and SRS-SFL-S166-02 (the pre-trip inspection is the evidence-bearing workflow
 * step that can block a trip). The workplan's "S166-06" identifier is deliberately not used — it has
 * no formal SRS requirement behind it (gap report C-01).
 *
 * <p>The result is derived from the findings rather than supplied, so an inspector cannot record a
 * critical defect and mark the vehicle passed in the same breath.
 */
public record VehicleInspection(
        UUID id,
        UUID vehicleId,
        UUID tripId,
        SiteCode siteCode,
        InspectionType inspectionType,
        InspectionStatus status,
        InspectionResult result,
        String performedBy,
        Instant performedAt,
        long odometerReading,
        UUID evidenceId,
        List<InspectionFinding> findings,
        String notes,
        RecordMetadata metadata) {

    public VehicleInspection {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(vehicleId, "vehicleId is required");
        Objects.requireNonNull(siteCode, "siteCode is required");
        Objects.requireNonNull(inspectionType, "inspectionType is required");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(result, "result is required");
        Objects.requireNonNull(performedAt, "performedAt is required");
        Objects.requireNonNull(metadata, "metadata is required");
        if (performedBy == null || performedBy.isBlank()) {
            throw new IllegalArgumentException("performedBy is required");
        }
        performedBy = performedBy.strip();
        if (odometerReading < 0) {
            throw new IllegalArgumentException("odometerReading cannot be negative");
        }
        findings = findings == null ? List.of() : List.copyOf(findings);
        notes = notes == null || notes.isBlank() ? null : notes.strip();
    }

    /**
     * Records a submitted inspection.
     *
     * <p>Evidence is mandatory for a failing inspection: a failure takes a vehicle off the road, and
     * SRS-SFL-S166-03 requires the evidence behind such a decision to be captured.
     */
    public static VehicleInspection record(UUID id, UUID vehicleId, UUID tripId, SiteCode siteCode,
            InspectionType inspectionType, String performedBy, Instant performedAt, long odometerReading,
            UUID evidenceId, List<InspectionFinding> findings, String notes, RecordMetadata metadata) {
        InspectionResult derivedResult = deriveResult(findings);
        if (derivedResult == InspectionResult.FAILED && evidenceId == null) {
            throw new gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.ClosureEvidenceMissingException(Map.of(
                    "inspectionType", inspectionType.name(),
                    "reason", "A failed inspection must carry evidence"));
        }
        return new VehicleInspection(id, vehicleId, tripId, siteCode, inspectionType, InspectionStatus.SUBMITTED,
                derivedResult, performedBy, performedAt, odometerReading, evidenceId, findings, notes, metadata);
    }

    /** Accepts a submitted inspection after supervisory review. */
    public VehicleInspection accept(RecordMetadata newMetadata) {
        requireStatus(InspectionStatus.SUBMITTED, InspectionStatus.ACCEPTED);
        return withStatus(InspectionStatus.ACCEPTED, findings, newMetadata);
    }

    /** Rejects a submitted inspection — for example when the evidence does not match the findings. */
    public VehicleInspection reject(RecordMetadata newMetadata) {
        requireStatus(InspectionStatus.SUBMITTED, InspectionStatus.REJECTED);
        return withStatus(InspectionStatus.REJECTED, findings, newMetadata);
    }

    /**
     * Marks a defect rectified.
     *
     * <p>The findings list is the only part of a submitted inspection that may change, because a
     * defect being fixed later is a real event that must be reflected without rewriting the original
     * inspection result.
     */
    public VehicleInspection resolveDefect(String checkCode, String resolutionReference,
            RecordMetadata newMetadata) {
        List<InspectionFinding> updated = findings.stream()
                .map(finding -> finding.checkCode().equals(checkCode)
                        ? finding.resolve(resolutionReference)
                        : finding)
                .toList();
        return withStatus(status, updated, newMetadata);
    }

    /** True when this inspection leaves an unresolved critical defect on the vehicle. */
    public boolean hasOpenCriticalDefect() {
        return findings.stream().anyMatch(InspectionFinding::isOpenCriticalDefect);
    }

    /** True when the inspection is recent enough to satisfy the configured validity window. */
    public boolean isValidAt(Instant at, Duration validityWindow) {
        return Duration.between(performedAt, at).compareTo(validityWindow) <= 0;
    }

    /** True when this inspection permits the vehicle to be used. */
    public boolean permitsUse() {
        return result.satisfiesReadiness() && !hasOpenCriticalDefect()
                && status != InspectionStatus.REJECTED;
    }

    public List<InspectionFinding> openCriticalDefects() {
        return findings.stream().filter(InspectionFinding::isOpenCriticalDefect).toList();
    }

    private static InspectionResult deriveResult(List<InspectionFinding> findings) {
        if (findings == null || findings.isEmpty()) {
            return InspectionResult.PASSED;
        }
        boolean failing = findings.stream()
                .anyMatch(finding -> finding.severity() == DefectSeverity.CRITICAL
                        || finding.severity() == DefectSeverity.MAJOR);
        if (failing) {
            return InspectionResult.FAILED;
        }
        return findings.stream().anyMatch(finding -> finding.severity() != DefectSeverity.ADVISORY)
                ? InspectionResult.PASSED_WITH_DEFECTS
                : InspectionResult.PASSED;
    }

    private void requireStatus(InspectionStatus expected, InspectionStatus target) {
        if (status != expected) {
            throw InvalidStateTransitionException.of("VehicleInspection", status, target);
        }
    }

    private VehicleInspection withStatus(InspectionStatus newStatus, List<InspectionFinding> newFindings,
            RecordMetadata newMetadata) {
        return new VehicleInspection(id, vehicleId, tripId, siteCode, inspectionType, newStatus, result,
                performedBy, performedAt, odometerReading, evidenceId, newFindings, notes, newMetadata);
    }
}
