package gh.edu.clet.sfl.fleetlogistics.fleet.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The result of assessing whether a driver may be assigned (SRS-SFL-S166-05 "driver eligibility
 * blockers").
 *
 * <p>As with readiness, the status is derived from the blocker set rather than supplied.
 */
public record EligibilityAssessment(
        UUID driverId,
        DriverEligibilityStatus status,
        List<ReadinessBlocker> blockers,
        Instant assessedAt,
        VehicleCategory assessedForCategory) {

    public EligibilityAssessment {
        Objects.requireNonNull(driverId, "driverId is required");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(assessedAt, "assessedAt is required");
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
    }

    public static EligibilityAssessment from(UUID driverId, DriverLifecycleStatus lifecycleStatus,
            List<ReadinessBlocker> blockers, Instant assessedAt, VehicleCategory category) {
        return new EligibilityAssessment(driverId, statusFor(lifecycleStatus, blockers), blockers, assessedAt,
                category);
    }

    private static DriverEligibilityStatus statusFor(DriverLifecycleStatus lifecycleStatus,
            List<ReadinessBlocker> blockers) {
        if (lifecycleStatus == DriverLifecycleStatus.SUSPENDED) {
            return DriverEligibilityStatus.SUSPENDED;
        }
        if (blockers == null || blockers.isEmpty()) {
            return DriverEligibilityStatus.ELIGIBLE;
        }
        return blockers.stream().anyMatch(ReadinessBlocker::isBlocking)
                ? DriverEligibilityStatus.INELIGIBLE
                : DriverEligibilityStatus.CONDITIONAL;
    }

    public boolean permitsAssignment() {
        return status.permitsAssignment();
    }

    public List<ReadinessBlockerCode> codes() {
        return blockers.stream().map(ReadinessBlocker::code).toList();
    }
}
