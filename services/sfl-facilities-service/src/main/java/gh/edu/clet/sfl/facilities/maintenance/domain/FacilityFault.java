package gh.edu.clet.sfl.facilities.maintenance.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record FacilityFault(
        UUID id,
        String faultNumber,
        String siteCode,
        String locationCode,
        String title,
        String description,
        String category,
        FaultPriority priority,
        FacilityFaultStatus status,
        String reportedBy,
        Instant reportedAt,
        UUID workOrderId) {

    public FacilityFault {
        Objects.requireNonNull(id, "id is required");
        requireText(faultNumber, "faultNumber");
        requireText(siteCode, "siteCode");
        requireText(locationCode, "locationCode");
        requireText(title, "title");
        requireText(description, "description");
        Objects.requireNonNull(priority, "priority is required");
        Objects.requireNonNull(status, "status is required");
        requireText(reportedBy, "reportedBy");
        Objects.requireNonNull(reportedAt, "reportedAt is required");
    }

    public static FacilityFault report(
            UUID id,
            String faultNumber,
            String siteCode,
            String locationCode,
            String title,
            String description,
            String category,
            FaultPriority priority,
            String reportedBy,
            Instant reportedAt) {
        return new FacilityFault(
                id,
                faultNumber,
                siteCode,
                locationCode,
                title,
                description,
                category,
                priority,
                FacilityFaultStatus.REPORTED,
                reportedBy,
                reportedAt,
                null);
    }

    public FacilityFault linkWorkOrder(UUID newWorkOrderId) {
        Objects.requireNonNull(newWorkOrderId, "workOrderId is required");
        if (workOrderId != null && !workOrderId.equals(newWorkOrderId)) {
            throw new IllegalStateException("Facility fault is already linked to a work order");
        }
        return new FacilityFault(id, faultNumber, siteCode, locationCode, title, description, category, priority,
                status, reportedBy, reportedAt, newWorkOrderId);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}

