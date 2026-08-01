package gh.edu.clet.sfl.fleetlogistics.fleet.api.mapper;

import gh.edu.clet.sfl.fleetlogistics.fleet.api.response.BlockerResponse;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.response.DriverResponse;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.response.EligibilityResponse;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.response.ReadinessResponse;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.DriverProfileReference;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.EligibilityAssessment;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ReadinessAssessment;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ReadinessBlocker;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

/** Maps drivers and the two assessment results to their API representations. */
@Component
public class FleetAssessmentMapper {

    public DriverResponse toResponse(DriverProfileReference driver, boolean includeSensitive, Instant now) {
        String licenceNumber = includeSensitive
                ? driver.licence().number()
                : driver.licence().maskedNumber();

        return new DriverResponse(
                driver.id(),
                driver.staffReference(),
                driver.displayName(),
                licenceNumber,
                !includeSensitive,
                driver.licence().licenceClass(),
                driver.licence().expiresOn(),
                driver.licence().daysUntilExpiry(now),
                driver.medicalClearanceExpiresOn(),
                driver.siteCode().value(),
                driver.responsibleUnit(),
                driver.lifecycleStatus(),
                driver.eligibilityStatus(),
                driver.suspensionReason(),
                driver.isBound(),
                driver.metadata().createdBy(),
                driver.metadata().createdAt(),
                driver.metadata().lastModifiedBy(),
                driver.metadata().lastModifiedAt(),
                driver.metadata().version());
    }

    public EligibilityResponse toResponse(EligibilityAssessment assessment) {
        return new EligibilityResponse(
                assessment.driverId(),
                assessment.status(),
                assessment.permitsAssignment(),
                toBlockers(assessment.blockers()),
                assessment.assessedAt(),
                assessment.assessedForCategory());
    }

    public ReadinessResponse toResponse(ReadinessAssessment assessment) {
        return new ReadinessResponse(
                assessment.vehicleId(),
                assessment.driverId(),
                assessment.status(),
                assessment.permitsAssignment(),
                toBlockers(assessment.blockers()),
                assessment.assessedAt(),
                assessment.assessedPeriod() == null ? null : assessment.assessedPeriod().start(),
                assessment.assessedPeriod() == null ? null : assessment.assessedPeriod().end(),
                assessment.operatingMode());
    }

    public List<BlockerResponse> toBlockers(List<ReadinessBlocker> blockers) {
        return blockers.stream()
                .map(blocker -> new BlockerResponse(blocker.code(), blocker.message(), blocker.severity(),
                        blocker.context()))
                .toList();
    }
}
