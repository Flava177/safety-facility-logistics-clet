package gh.edu.clet.sfl.fleetlogistics.fleet.domain.policy;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ComplianceDocument;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ComplianceDocumentType;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.DateTimeRange;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.DriverProfileReference;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.OperatingMode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.Vehicle;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleInspection;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Everything {@link VehicleReadinessPolicy} needs to reach a decision.
 *
 * <p>Gathering the inputs into one value keeps the policy a pure function: the application layer does
 * the loading, the policy does the deciding, and the decision can be unit-tested without a database.
 *
 * @param requiredDocumentTypes which document types must be present; configurable so a site can add a
 *        local requirement without a code change
 * @param vehicleAssignmentConflicts identifiers of active trips overlapping the requested period on
 *        this vehicle; the application layer resolves them from the trip repository
 * @param requiredEvidencePresent whether every evidence item the operation demands has been attached
 */
public record ReadinessContext(
        Vehicle vehicle,
        DriverProfileReference driver,
        List<ComplianceDocument> currentComplianceDocuments,
        Set<ComplianceDocumentType> requiredDocumentTypes,
        VehicleInspection latestInspection,
        List<String> vehicleAssignmentConflicts,
        List<String> driverAssignmentConflicts,
        DateTimeRange requestedPeriod,
        OperatingMode operatingMode,
        SiteCode requestedSite,
        Instant assessedAt,
        Duration complianceWarningWindow,
        Duration inspectionValidityWindow,
        Duration odometerStalenessThreshold,
        boolean requiredEvidencePresent,
        boolean inspectionRequired) {

    public ReadinessContext {
        Objects.requireNonNull(vehicle, "vehicle is required");
        Objects.requireNonNull(assessedAt, "assessedAt is required");
        currentComplianceDocuments = currentComplianceDocuments == null
                ? List.of()
                : List.copyOf(currentComplianceDocuments);
        requiredDocumentTypes = requiredDocumentTypes == null
                ? defaultRequiredDocumentTypes()
                : Set.copyOf(requiredDocumentTypes);
        vehicleAssignmentConflicts = vehicleAssignmentConflicts == null
                ? List.of()
                : List.copyOf(vehicleAssignmentConflicts);
        driverAssignmentConflicts = driverAssignmentConflicts == null
                ? List.of()
                : List.copyOf(driverAssignmentConflicts);
        complianceWarningWindow = complianceWarningWindow == null
                ? Duration.ofDays(30)
                : complianceWarningWindow;
        inspectionValidityWindow = inspectionValidityWindow == null
                ? Duration.ofDays(1)
                : inspectionValidityWindow;
        odometerStalenessThreshold = odometerStalenessThreshold == null
                ? Duration.ofDays(30)
                : odometerStalenessThreshold;
        operatingMode = operatingMode == null ? OperatingMode.ROUTINE : operatingMode;
    }

    /** The road-legal document set every CLET vehicle must hold. */
    public static Set<ComplianceDocumentType> defaultRequiredDocumentTypes() {
        return java.util.Arrays.stream(ComplianceDocumentType.values())
                .filter(ComplianceDocumentType::isMandatory)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /**
     * A minimal context for the "is this vehicle ready right now?" question, with no driver, period or
     * inspection requirement — what the register and the dashboard ask.
     */
    public static ReadinessContext forVehicleOnly(Vehicle vehicle, List<ComplianceDocument> documents,
            VehicleInspection latestInspection, Instant assessedAt, Duration complianceWarningWindow,
            Duration inspectionValidityWindow, Duration odometerStalenessThreshold) {
        return new ReadinessContext(vehicle, null, documents, null, latestInspection, List.of(), List.of(), null,
                OperatingMode.ROUTINE, null, assessedAt, complianceWarningWindow, inspectionValidityWindow,
                odometerStalenessThreshold, true, false);
    }
}
