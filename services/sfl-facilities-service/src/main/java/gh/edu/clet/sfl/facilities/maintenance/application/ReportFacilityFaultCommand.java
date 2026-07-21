package gh.edu.clet.sfl.facilities.maintenance.application;

import gh.edu.clet.sfl.facilities.maintenance.domain.FaultPriority;

public record ReportFacilityFaultCommand(
        String siteCode,
        String locationCode,
        String title,
        String description,
        String category,
        FaultPriority priority,
        String reportedBy,
        String correlationId) {
}

