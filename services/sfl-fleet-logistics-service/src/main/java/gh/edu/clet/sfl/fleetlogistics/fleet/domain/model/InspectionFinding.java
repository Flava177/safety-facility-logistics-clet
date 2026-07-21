package gh.edu.clet.sfl.fleetlogistics.fleet.domain.model;

import java.util.Objects;

/**
 * One checklist finding on an inspection.
 *
 * @param resolved whether the defect has since been rectified; an unresolved critical finding is what
 *        raises the {@code OPEN_CRITICAL_DEFECT} readiness blocker
 */
public record InspectionFinding(
        String checkCode,
        String description,
        DefectSeverity severity,
        boolean resolved,
        String resolutionReference) {

    public InspectionFinding {
        checkCode = requireText(checkCode, "checkCode", 80);
        description = requireText(description, "description", 1000);
        Objects.requireNonNull(severity, "severity is required");
        resolutionReference = resolutionReference == null || resolutionReference.isBlank()
                ? null
                : resolutionReference.strip();
    }

    public static InspectionFinding of(String checkCode, String description, DefectSeverity severity) {
        return new InspectionFinding(checkCode, description, severity, false, null);
    }

    public InspectionFinding resolve(String reference) {
        return new InspectionFinding(checkCode, description, severity, true, reference);
    }

    public boolean isOpenCriticalDefect() {
        return severity.takesVehicleOutOfService() && !resolved;
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        String stripped = value.strip();
        if (stripped.length() > maxLength) {
            throw new IllegalArgumentException(field + " cannot exceed " + maxLength + " characters");
        }
        return stripped;
    }
}
