package gh.edu.clet.sfl.facilities.spaceplanning.api;

import gh.edu.clet.sfl.facilities.masterdata.domain.SpaceAllocation;
import gh.edu.clet.sfl.facilities.spaceplanning.application.SpacePlanningDashboardService;
import gh.edu.clet.sfl.facilities.spaceplanning.application.SpaceScenarioService;
import gh.edu.clet.sfl.facilities.spaceplanning.application.UtilisationReconciliationService;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.AllocationScenario;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.OccupancyOverride;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.OccupancyStandard;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.RoomCompliance;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.ScenarioAllocation;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.SpaceChangeRequest;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.UtilisationSignal;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.UtilisationSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** The S158 response bodies, one {@code from} factory per domain shape - the {@code BookingResponses} pattern. */
public final class SpacePlanningResponses {

    private SpacePlanningResponses() {
    }

    public record ScenarioResponse(UUID id, String siteCode, String planReference, int versionNumber, String name,
            String description, UUID spaceChangeRequestId, String status, String commitOutcome, String committedBy,
            Instant committedAt, UUID linkedProjectId, String linkedProjectReference, boolean awaitingHandover,
            long version) {
        public static ScenarioResponse from(AllocationScenario s) {
            return new ScenarioResponse(s.id(), s.siteCode(), s.planReference(), s.versionNumber(), s.name(),
                    s.description(), s.spaceChangeRequestId(), s.status().name(),
                    s.commitOutcome() == null ? null : s.commitOutcome().name(), s.committedBy(), s.committedAt(),
                    s.linkedProjectId(), s.linkedProjectReference(), s.awaitingHandover(), s.metadata().version());
        }
    }

    public record ScenarioLineResponse(UUID id, UUID roomId, String roomCode, String allocatedUnit, int headcount,
            String complianceStatus, String complianceDetail) {
        public static ScenarioLineResponse from(ScenarioAllocation line) {
            return new ScenarioLineResponse(line.id(), line.roomId(), line.roomCode(), line.allocatedUnit(),
                    line.headcount(), line.complianceStatus().name(), line.complianceDetail());
        }
    }

    public record RoomComplianceResponse(UUID roomId, String roomCode, String spaceType, int totalHeadcount,
            Integer capacity, BigDecimal areaSqm, String status, String reasonCode, List<String> findings,
            boolean flagged, boolean overridden) {
        public static RoomComplianceResponse from(RoomCompliance c) {
            return new RoomComplianceResponse(c.roomId(), c.roomCode(), c.spaceType().name(), c.totalHeadcount(),
                    c.capacity(), c.areaSqm(), c.status().name(), c.reasonCode(), c.findings(), c.flagged(),
                    c.overridden());
        }
    }

    public record CommitResultResponse(ScenarioResponse scenario, List<RoomComplianceResponse> compliance) {
        public static CommitResultResponse from(SpaceScenarioService.CommitResult result) {
            return new CommitResultResponse(ScenarioResponse.from(result.scenario()),
                    result.compliance().stream().map(RoomComplianceResponse::from).toList());
        }
    }

    public record AllocationResponse(UUID id, String siteCode, UUID roomId, String roomCode, String allocatedUnit,
            int headcount, Instant allocatedSince, UUID sourceScenarioId, String sourceReference) {
        public static AllocationResponse from(SpaceAllocation a) {
            return new AllocationResponse(a.id(), a.siteCode(), a.roomId(), a.roomCode(), a.allocatedUnit(),
                    a.headcount(), a.allocatedSince(), a.sourceScenarioId(), a.sourceReference());
        }
    }

    public record StandardResponse(UUID id, String siteCode, String spaceType, int versionNumber,
            Integer maxCapacityPercent, BigDecimal minAreaPerPersonSqm, String note, boolean active) {
        public static StandardResponse from(OccupancyStandard s) {
            return new StandardResponse(s.id(), s.siteCode(), s.spaceType().name(), s.versionNumber(),
                    s.maxCapacityPercent(), s.minAreaPerPersonSqm(), s.note(), s.isActive());
        }
    }

    public record OverrideResponse(UUID id, String siteCode, UUID scenarioId, UUID roomId, String roomCode,
            int headcountCovered, String reason, String status, String requestedBy, Instant requestedAt,
            String approvedBy, Instant approvedAt) {
        public static OverrideResponse from(OccupancyOverride o) {
            return new OverrideResponse(o.id(), o.siteCode(), o.scenarioId(), o.roomId(), o.roomCode(),
                    o.headcountCovered(), o.reason(), o.status().name(), o.requestedBy(), o.requestedAt(),
                    o.approvedBy(), o.approvedAt());
        }
    }

    public record SignalResponse(UUID id, String siteCode, UUID roomId, String roomCode, String kind, String status,
            Instant raisedAt, Instant latestPeriodEnd, BigDecimal latestUtilisationRate, BigDecimal thresholdRate,
            String detail) {
        public static SignalResponse from(UtilisationSignal s) {
            return new SignalResponse(s.id(), s.siteCode(), s.roomId(), s.roomCode(), s.kind().name(),
                    s.status().name(), s.raisedAt(), s.latestPeriodEnd(), s.latestUtilisationRate(),
                    s.thresholdRate(), s.detail());
        }
    }

    public record SnapshotResponse(UUID roomId, String roomCode, Instant periodStart, Instant periodEnd,
            Integer capacity, int bookingCount, int takenUpCount, int noShowCount, BigDecimal utilisationRate,
            int plannedHeadcount, BigDecimal plannedOccupancyRate, boolean observable) {
        public static SnapshotResponse from(UtilisationSnapshot s) {
            return new SnapshotResponse(s.roomId(), s.roomCode(), s.periodStart(), s.periodEnd(), s.capacity(),
                    s.bookingCount(), s.takenUpCount(), s.noShowCount(), s.utilisationRate(), s.plannedHeadcount(),
                    s.plannedOccupancyRate(), s.observable());
        }
    }

    public record ReconciliationRunResponse(String siteCode, Instant periodStart, Instant periodEnd,
            int roomsSnapshotted, int signalsRaised, int signalsCleared, int activeSignals) {
        public static ReconciliationRunResponse from(UtilisationReconciliationService.RunSummary run) {
            return new ReconciliationRunResponse(run.siteCode(), run.periodStart(), run.periodEnd(),
                    run.roomsSnapshotted(), run.signalsRaised(), run.signalsCleared(), run.activeSignals());
        }
    }

    public record SpaceChangeRequestResponse(UUID id, String reference, String siteCode, String requestingUnit,
            String justification, UUID targetRoomId, String targetRoomCode, String targetAreaDescription,
            Integer requiredHeadcount, String urgency, String status, String requestedBy, Instant requestedAt,
            String decidedBy, Instant decidedAt, String decisionReason, String outcomeType, UUID linkedScenarioId,
            UUID linkedProjectId, String linkedProjectReference, String resolvedBy, Instant resolvedAt,
            long version) {
        public static SpaceChangeRequestResponse from(SpaceChangeRequest r) {
            return new SpaceChangeRequestResponse(r.id(), r.reference(), r.siteCode(), r.requestingUnit(),
                    r.justification(), r.targetRoomId(), r.targetRoomCode(), r.targetAreaDescription(),
                    r.requiredHeadcount(), r.urgency().name(), r.status().name(), r.requestedBy(), r.requestedAt(),
                    r.decidedBy(), r.decidedAt(), r.decisionReason(),
                    r.outcomeType() == null ? null : r.outcomeType().name(), r.linkedScenarioId(),
                    r.linkedProjectId(), r.linkedProjectReference(), r.resolvedBy(), r.resolvedAt(),
                    r.metadata().version());
        }
    }

    public record ComparisonResponse(String siteCode, List<SpaceScenarioService.ScenarioTotals> scenarios,
            List<SpaceScenarioService.RoomComparison> rooms) {
        public static ComparisonResponse from(SpaceScenarioService.ScenarioComparison comparison) {
            return new ComparisonResponse(comparison.siteCode(), comparison.scenarios(), comparison.rooms());
        }
    }

    public record DashboardResponse(String siteCode, List<SpacePlanningDashboardService.UtilisationRow> currentVersusPlanned,
            java.util.Map<String, Long> scenariosByStatus,
            List<SpaceScenarioService.ScenarioTotals> draftComparison, List<String> awaitingHandover,
            java.util.Map<String, Long> requestPipeline, java.util.Map<String, Long> openRequestsByUrgency,
            List<SpacePlanningDashboardService.UnitCompliance> complianceByUnit,
            java.util.Map<String, Long> activeSignals) {
        public static DashboardResponse from(SpacePlanningDashboardService.Dashboard d) {
            java.util.Map<String, Long> byStatus = new java.util.LinkedHashMap<>();
            d.scenariosByStatus().forEach((k, v) -> byStatus.put(k.name(), v));
            java.util.Map<String, Long> pipeline = new java.util.LinkedHashMap<>();
            d.requestPipeline().forEach((k, v) -> pipeline.put(k.name(), v));
            java.util.Map<String, Long> urgency = new java.util.LinkedHashMap<>();
            d.openRequestsByUrgency().forEach((k, v) -> urgency.put(k.name(), v));
            java.util.Map<String, Long> signals = new java.util.LinkedHashMap<>();
            d.activeSignals().forEach((k, v) -> signals.put(k.name(), v));
            return new DashboardResponse(d.siteCode(), d.currentVersusPlanned(), byStatus, d.draftComparison(),
                    d.awaitingHandover(), pipeline, urgency, d.complianceByUnit(), signals);
        }
    }
}
