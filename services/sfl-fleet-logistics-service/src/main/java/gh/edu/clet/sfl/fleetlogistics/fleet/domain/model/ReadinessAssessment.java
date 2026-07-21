package gh.edu.clet.sfl.fleetlogistics.fleet.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The result of assessing whether a vehicle can be assigned (SRS-SFL-S166-05 readiness blockers).
 *
 * <p>The status is computed from the blockers rather than passed in, so a caller cannot report READY
 * while holding a blocking finding.
 */
public record ReadinessAssessment(
        UUID vehicleId,
        UUID driverId,
        ReadinessStatus status,
        List<ReadinessBlocker> blockers,
        Instant assessedAt,
        DateTimeRange assessedPeriod,
        OperatingMode operatingMode) {

    public ReadinessAssessment {
        Objects.requireNonNull(vehicleId, "vehicleId is required");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(assessedAt, "assessedAt is required");
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
    }

    public static ReadinessAssessment from(UUID vehicleId, UUID driverId, List<ReadinessBlocker> blockers,
            Instant assessedAt, DateTimeRange period, OperatingMode operatingMode) {
        return new ReadinessAssessment(vehicleId, driverId, statusFor(blockers), blockers, assessedAt, period,
                operatingMode);
    }

    private static ReadinessStatus statusFor(List<ReadinessBlocker> blockers) {
        if (blockers == null || blockers.isEmpty()) {
            return ReadinessStatus.READY;
        }
        return blockers.stream().anyMatch(ReadinessBlocker::isBlocking)
                ? ReadinessStatus.NOT_READY
                : ReadinessStatus.CONDITIONALLY_READY;
    }

    public boolean permitsAssignment() {
        return status.permitsAssignment();
    }

    public List<ReadinessBlockerCode> blockingCodes() {
        return blockers.stream().filter(ReadinessBlocker::isBlocking).map(ReadinessBlocker::code).toList();
    }

    public List<ReadinessBlockerCode> codes() {
        return blockers.stream().map(ReadinessBlocker::code).toList();
    }
}
