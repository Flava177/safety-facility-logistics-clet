package gh.edu.clet.sfl.fleetlogistics.fleet.domain.policy;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ComplianceDocument;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ComplianceDocumentType;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.EligibilityAssessment;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ReadinessAssessment;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ReadinessBlocker;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ReadinessBlockerCode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.Vehicle;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleInspection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Decides whether a vehicle is ready, and says exactly why not.
 *
 * <p>Readiness is never a stored, freely editable flag: it is recomputed from explicit blockers every
 * time it is asked for, because a stored flag is wrong the moment a certificate lapses overnight. Each
 * finding carries a machine-readable code and a human-readable explanation, as the brief requires.
 *
 * <p>The policy collects <em>every</em> applicable blocker rather than returning on the first one. A
 * fleet officer preparing a vehicle needs the full list, not one problem at a time.
 *
 * <p>Traces: SRS-SFL-S166-01 (record state drives availability), SRS-SFL-S166-02 (assignment gating),
 * SRS-SFL-S166-05 ("readiness blockers" indicator).
 */
public final class VehicleReadinessPolicy {

    private VehicleReadinessPolicy() {
    }

    public static ReadinessAssessment assess(ReadinessContext context) {
        List<ReadinessBlocker> blockers = new ArrayList<>();
        Vehicle vehicle = context.vehicle();

        addLifecycleBlockers(context, blockers);
        addServiceBlockers(context, blockers);
        addComplianceBlockers(context, blockers);
        addInspectionBlockers(context, blockers);
        addAssignmentConflictBlockers(context, blockers);
        addDriverBlockers(context, blockers);
        addScopeAndRestrictionBlockers(context, blockers);
        addEvidenceAndProvenanceBlockers(context, blockers);

        return ReadinessAssessment.from(vehicle.id(),
                context.driver() == null ? null : context.driver().id(),
                blockers, context.assessedAt(), context.requestedPeriod(), context.operatingMode());
    }

    private static void addLifecycleBlockers(ReadinessContext context, List<ReadinessBlocker> blockers) {
        switch (context.vehicle().lifecycleStatus()) {
            case INACTIVE -> blockers.add(ReadinessBlocker.of(ReadinessBlockerCode.VEHICLE_NOT_ACTIVE,
                    Map.of("lifecycleStatus", "INACTIVE")));
            case SUSPENDED -> blockers.add(ReadinessBlocker.of(ReadinessBlockerCode.VEHICLE_SUSPENDED,
                    Map.of("lifecycleStatus", "SUSPENDED")));
            case ARCHIVED -> blockers.add(ReadinessBlocker.of(ReadinessBlockerCode.VEHICLE_ARCHIVED,
                    Map.of("lifecycleStatus", "ARCHIVED")));
            case ACTIVE -> {
                // No lifecycle blocker.
            }
            default -> throw new IllegalStateException("Unhandled vehicle lifecycle status: "
                    + context.vehicle().lifecycleStatus());
        }
    }

    private static void addServiceBlockers(ReadinessContext context, List<ReadinessBlocker> blockers) {
        switch (context.vehicle().serviceStatus()) {
            case OUT_OF_SERVICE -> blockers.add(ReadinessBlocker.of(ReadinessBlockerCode.VEHICLE_OUT_OF_SERVICE,
                    Map.of("serviceStatus", "OUT_OF_SERVICE")));
            case OVERDUE -> blockers.add(ReadinessBlocker.of(ReadinessBlockerCode.SERVICE_OVERDUE,
                    Map.of("serviceStatus", "OVERDUE")));
            case DUE -> blockers.add(ReadinessBlocker.of(ReadinessBlockerCode.SERVICE_DUE_SOON,
                    Map.of("serviceStatus", "DUE")));
            case IN_SERVICE -> {
                // No service blocker.
            }
            default -> throw new IllegalStateException("Unhandled vehicle service status: "
                    + context.vehicle().serviceStatus());
        }
    }

    private static void addComplianceBlockers(ReadinessContext context, List<ReadinessBlocker> blockers) {
        for (ComplianceDocumentType required : context.requiredDocumentTypes()) {
            Optional<ComplianceDocument> document = context.currentComplianceDocuments().stream()
                    .filter(candidate -> candidate.documentType() == required)
                    .findFirst();

            if (document.isEmpty()) {
                blockers.add(ReadinessBlocker.of(ReadinessBlockerCode.COMPLIANCE_DOCUMENT_MISSING,
                        "Missing: " + required + ".", Map.of("documentType", required.name())));
                continue;
            }
            evaluateDocument(context, document.get(), blockers);
        }

        // Non-mandatory documents that are on file still matter once they lapse: an expired permit is
        // a finding even though its absence would not have been.
        context.currentComplianceDocuments().stream()
                .filter(document -> !context.requiredDocumentTypes().contains(document.documentType()))
                .forEach(document -> evaluateDocument(context, document, blockers));
    }

    private static void evaluateDocument(ReadinessContext context, ComplianceDocument document,
            List<ReadinessBlocker> blockers) {
        if (document.isExpiredAt(context.assessedAt())) {
            blockers.add(ReadinessBlocker.of(ReadinessBlockerCode.COMPLIANCE_DOCUMENT_EXPIRED,
                    document.documentType() + " expired on " + document.expiresOn() + ".",
                    document.blockerContext()));
            return;
        }
        // A document valid today but expiring before the trip ends would leave the vehicle uninsured
        // mid-journey, so the assessment period — not just today — drives the check.
        if (context.requestedPeriod() != null
                && document.isExpiredAt(context.requestedPeriod().end())) {
            blockers.add(ReadinessBlocker.of(ReadinessBlockerCode.COMPLIANCE_DOCUMENT_EXPIRED,
                    document.documentType() + " expires on " + document.expiresOn()
                            + ", before the end of the requested period.",
                    document.blockerContext()));
            return;
        }
        if (document.daysUntilExpiry(context.assessedAt()) <= context.complianceWarningWindow().toDays()) {
            blockers.add(ReadinessBlocker.of(ReadinessBlockerCode.COMPLIANCE_DOCUMENT_EXPIRING,
                    document.documentType() + " expires on " + document.expiresOn() + ".",
                    document.blockerContext()));
        }
    }

    private static void addInspectionBlockers(ReadinessContext context, List<ReadinessBlocker> blockers) {
        VehicleInspection inspection = context.latestInspection();

        if (inspection == null) {
            if (context.inspectionRequired()) {
                blockers.add(ReadinessBlocker.of(ReadinessBlockerCode.MANDATORY_INSPECTION_MISSING,
                        Map.of("validityWindow", context.inspectionValidityWindow().toString())));
            }
            return;
        }

        if (!inspection.result().satisfiesReadiness()) {
            blockers.add(ReadinessBlocker.of(ReadinessBlockerCode.INSPECTION_FAILED,
                    Map.of("inspectionId", inspection.id().toString(),
                            "result", inspection.result().name(),
                            "performedAt", inspection.performedAt().toString())));
        }

        if (inspection.hasOpenCriticalDefect()) {
            blockers.add(ReadinessBlocker.of(ReadinessBlockerCode.OPEN_CRITICAL_DEFECT,
                    Map.of("inspectionId", inspection.id().toString(),
                            "defects", inspection.openCriticalDefects().stream()
                                    .map(finding -> finding.checkCode() + ": " + finding.description())
                                    .toList())));
        }

        if (context.inspectionRequired()
                && !inspection.isValidAt(context.assessedAt(), context.inspectionValidityWindow())) {
            blockers.add(ReadinessBlocker.of(ReadinessBlockerCode.MANDATORY_INSPECTION_MISSING,
                    "The most recent inspection was on " + inspection.performedAt() + ".",
                    Map.of("inspectionId", inspection.id().toString(),
                            "performedAt", inspection.performedAt().toString(),
                            "validityWindow", context.inspectionValidityWindow().toString())));
        }
    }

    private static void addAssignmentConflictBlockers(ReadinessContext context, List<ReadinessBlocker> blockers) {
        if (!context.vehicleAssignmentConflicts().isEmpty()) {
            blockers.add(ReadinessBlocker.of(ReadinessBlockerCode.VEHICLE_ASSIGNMENT_CONFLICT,
                    Map.of("conflictingTripIds", context.vehicleAssignmentConflicts())));
        }
        if (!context.driverAssignmentConflicts().isEmpty()) {
            blockers.add(ReadinessBlocker.of(ReadinessBlockerCode.DRIVER_ASSIGNMENT_CONFLICT,
                    Map.of("conflictingTripIds", context.driverAssignmentConflicts())));
        }
    }

    private static void addDriverBlockers(ReadinessContext context, List<ReadinessBlocker> blockers) {
        if (context.driver() == null) {
            // A driver is only required when readiness is being assessed for an actual assignment.
            if (context.requestedPeriod() != null) {
                blockers.add(ReadinessBlocker.of(ReadinessBlockerCode.DRIVER_MISSING));
            }
            return;
        }

        EligibilityAssessment eligibility = DriverEligibilityPolicy.assess(
                context.driver(),
                context.vehicle().specification().category(),
                context.assessedAt(),
                context.requestedPeriod() == null ? null : context.requestedPeriod().end(),
                context.complianceWarningWindow(),
                context.requestedSite());

        // The driver's own findings are surfaced verbatim so the operator sees the licence date, not
        // just "ineligible".
        blockers.addAll(eligibility.blockers());

        if (!eligibility.permitsAssignment()) {
            blockers.add(ReadinessBlocker.of(ReadinessBlockerCode.DRIVER_INELIGIBLE,
                    Map.of("driverId", context.driver().id().toString(),
                            "eligibilityStatus", eligibility.status().name())));
        }
    }

    private static void addScopeAndRestrictionBlockers(ReadinessContext context, List<ReadinessBlocker> blockers) {
        if (context.requestedSite() != null && !context.vehicle().siteCode().equals(context.requestedSite())) {
            blockers.add(ReadinessBlocker.of(ReadinessBlockerCode.SITE_RESTRICTION,
                    "The vehicle belongs to " + context.vehicle().siteCode() + ".",
                    Map.of("vehicleSiteCode", context.vehicle().siteCode().value(),
                            "requestedSiteCode", context.requestedSite().value())));
        }

        if (context.vehicle().restrictedUse().violatesEmergencyOnlyRule(context.operatingMode())) {
            blockers.add(ReadinessBlocker.of(ReadinessBlockerCode.EMERGENCY_ONLY_RESTRICTION,
                    Map.of("requestedOperatingMode", context.operatingMode().name())));
        } else if (!context.vehicle().restrictedUse().permits(context.operatingMode())) {
            blockers.add(ReadinessBlocker.of(ReadinessBlockerCode.OPERATING_MODE_RESTRICTION,
                    Map.of("requestedOperatingMode", context.operatingMode().name(),
                            "allowedOperatingModes", context.vehicle().restrictedUse().allowedOperatingModes()
                                    .stream().map(Enum::name).sorted().toList())));
        }
    }

    private static void addEvidenceAndProvenanceBlockers(ReadinessContext context,
            List<ReadinessBlocker> blockers) {
        if (!context.requiredEvidencePresent()) {
            blockers.add(ReadinessBlocker.of(ReadinessBlockerCode.MISSING_REQUIRED_EVIDENCE));
        }
        if (context.vehicle().odometer().isStaleAt(context.assessedAt(), context.odometerStalenessThreshold())) {
            blockers.add(ReadinessBlocker.of(ReadinessBlockerCode.ODOMETER_PROVENANCE_STALE,
                    "Last read on " + context.vehicle().odometer().recordedAt() + " from "
                            + context.vehicle().odometer().source() + ".",
                    Map.of("odometerRecordedAt", context.vehicle().odometer().recordedAt().toString(),
                            "odometerSource", context.vehicle().odometer().source().name())));
        }
    }
}
