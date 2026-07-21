package gh.edu.clet.sfl.fleetlogistics.fleet.support;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.FleetWorkflowRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.SlaRuleRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.TripRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.VehicleInspectionRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.SiteScopeFilter;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.workflow.FleetWorkflowRaiser;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ComplianceDocument;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.DateTimeRange;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.FleetWorkflowItem;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.Trip;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.Vehicle;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleInspection;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.WorkflowComment;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.WorkflowTransition;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.policy.SlaPolicy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** In-memory ports for the trip, inspection and workflow slices. */
public final class FleetWorkflowTestDoubles {

    private FleetWorkflowTestDoubles() {
    }

    /**
     * In-memory trip store.
     *
     * <p>The conflict queries use the same half-open overlap rule as the SQL and the database exclusion
     * constraint, so a test that passes here is exercising the real rule rather than a looser one.
     */
    public static final class InMemoryTripRepository implements TripRepository {

        private final Map<UUID, Trip> store = new LinkedHashMap<>();

        @Override
        public Trip save(Trip trip) {
            store.put(trip.id(), trip);
            return trip;
        }

        @Override
        public Optional<Trip> findById(UUID id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public List<Trip> findVehicleConflicts(UUID vehicleId, DateTimeRange period, UUID excludingTripId) {
            return conflicts(trip -> vehicleId.equals(trip.vehicleId()), period, excludingTripId);
        }

        @Override
        public List<Trip> findDriverConflicts(UUID driverId, DateTimeRange period, UUID excludingTripId) {
            return conflicts(trip -> driverId.equals(trip.driverId()), period, excludingTripId);
        }

        private List<Trip> conflicts(java.util.function.Predicate<Trip> matches, DateTimeRange period,
                UUID excludingTripId) {
            if (period == null) {
                return List.of();
            }
            return store.values().stream()
                    .filter(Trip::holdsAssignment)
                    .filter(matches)
                    .filter(trip -> excludingTripId == null || !trip.id().equals(excludingTripId))
                    .filter(trip -> trip.plannedPeriod().overlaps(period))
                    .toList();
        }

        @Override
        public TripPage search(TripSearchCriteria criteria, SiteScopeFilter scope) {
            List<Trip> matching = store.values().stream()
                    .filter(trip -> scope.permits(trip.siteCode().value()))
                    .filter(trip -> criteria.status() == null || trip.status() == criteria.status())
                    .filter(trip -> criteria.vehicleId() == null
                            || criteria.vehicleId().equals(trip.vehicleId()))
                    .filter(trip -> criteria.driverId() == null || criteria.driverId().equals(trip.driverId()))
                    .sorted(Comparator.comparing((Trip trip) -> trip.plannedPeriod().start()).reversed())
                    .toList();
            int size = criteria.size() <= 0 ? 20 : criteria.size();
            int from = Math.min(criteria.page() * size, matching.size());
            int to = Math.min(from + size, matching.size());
            return new TripPage(matching.subList(from, to), criteria.page(), size, matching.size(),
                    Math.max((int) Math.ceil(matching.size() / (double) size), 1), "plannedStart: DESC");
        }

        @Override
        public List<Trip> findAllInScope(SiteScopeFilter scope) {
            return store.values().stream().filter(trip -> scope.permits(trip.siteCode().value())).toList();
        }

        @Override
        public List<Trip> findLiveTripsEndingBefore(Instant threshold) {
            return store.values().stream()
                    .filter(Trip::holdsAssignment)
                    .filter(trip -> trip.plannedPeriod().end().isBefore(threshold))
                    .toList();
        }

        public int size() {
            return store.size();
        }
    }

    /** In-memory inspection store. */
    public static final class InMemoryInspectionRepository implements VehicleInspectionRepository {

        private final Map<UUID, VehicleInspection> store = new LinkedHashMap<>();

        @Override
        public VehicleInspection save(VehicleInspection inspection) {
            store.put(inspection.id(), inspection);
            return inspection;
        }

        @Override
        public Optional<VehicleInspection> findById(UUID id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public Optional<VehicleInspection> findLatestByVehicle(UUID vehicleId) {
            return store.values().stream()
                    .filter(inspection -> inspection.vehicleId().equals(vehicleId))
                    .max(Comparator.comparing(VehicleInspection::performedAt));
        }

        @Override
        public List<VehicleInspection> findByVehicle(UUID vehicleId) {
            return store.values().stream()
                    .filter(inspection -> inspection.vehicleId().equals(vehicleId))
                    .sorted(Comparator.comparing(VehicleInspection::performedAt).reversed())
                    .toList();
        }

        @Override
        public List<VehicleInspection> findByTrip(UUID tripId) {
            return store.values().stream()
                    .filter(inspection -> tripId.equals(inspection.tripId()))
                    .toList();
        }

        @Override
        public List<VehicleInspection> findLatestInScope(SiteScopeFilter scope) {
            return store.values().stream()
                    .filter(inspection -> scope.permits(inspection.siteCode().value()))
                    .collect(java.util.stream.Collectors.toMap(VehicleInspection::vehicleId,
                            inspection -> inspection,
                            (first, second) -> first.performedAt().isAfter(second.performedAt())
                                    ? first : second))
                    .values().stream().toList();
        }

        @Override
        public List<VehicleInspection> findFailuresSince(SiteScopeFilter scope, Instant from) {
            return store.values().stream()
                    .filter(inspection -> scope.permits(inspection.siteCode().value()))
                    .filter(inspection -> !inspection.permitsUse())
                    .filter(inspection -> !inspection.performedAt().isBefore(from))
                    .toList();
        }

        public int size() {
            return store.size();
        }
    }

    /** In-memory workflow queue with a genuinely append-only history. */
    public static final class InMemoryFleetWorkflowRepository implements FleetWorkflowRepository {

        private final Map<UUID, FleetWorkflowItem> items = new LinkedHashMap<>();
        private final List<WorkflowTransition> transitions = new ArrayList<>();
        private final List<WorkflowComment> comments = new ArrayList<>();

        @Override
        public FleetWorkflowItem save(FleetWorkflowItem item) {
            items.put(item.id(), item);
            return item;
        }

        @Override
        public Optional<FleetWorkflowItem> findById(UUID id) {
            return Optional.ofNullable(items.get(id));
        }

        @Override
        public Optional<FleetWorkflowItem> findOpenByRelatedRecord(String relatedRecordType,
                String relatedRecordId) {
            return items.values().stream()
                    .filter(item -> relatedRecordType.equals(item.relatedRecordType()))
                    .filter(item -> relatedRecordId.equals(item.relatedRecordId()))
                    .filter(item -> !item.status().isTerminal())
                    .findFirst();
        }

        @Override
        public WorkflowPage search(WorkflowSearchCriteria criteria, SiteScopeFilter scope) {
            List<FleetWorkflowItem> matching = items.values().stream()
                    .filter(item -> scope.permits(item.siteCode().value()))
                    .filter(item -> criteria.status() == null || item.status() == criteria.status())
                    .filter(item -> criteria.workflowType() == null
                            || item.workflowType() == criteria.workflowType())
                    .filter(item -> criteria.priority() == null || item.priority() == criteria.priority())
                    .filter(item -> criteria.assignee() == null
                            || criteria.assignee().equals(item.assignee()))
                    .filter(item -> !criteria.escalatedOnly() || item.isEscalated())
                    .toList();
            int size = criteria.size() <= 0 ? 20 : criteria.size();
            int from = Math.min(criteria.page() * size, matching.size());
            int to = Math.min(from + size, matching.size());
            return new WorkflowPage(matching.subList(from, to), criteria.page(), size, matching.size(),
                    Math.max((int) Math.ceil(matching.size() / (double) size), 1), "slaDueAt: ASC");
        }

        @Override
        public List<FleetWorkflowItem> findAllInScope(SiteScopeFilter scope) {
            return items.values().stream().filter(item -> scope.permits(item.siteCode().value())).toList();
        }

        @Override
        public List<FleetWorkflowItem> findLiveBreachedAt(Instant now) {
            return items.values().stream().filter(item -> item.hasBreachedSlaAt(now)).toList();
        }

        @Override
        public WorkflowTransition appendTransition(WorkflowTransition transition) {
            transitions.add(transition);
            return transition;
        }

        @Override
        public WorkflowComment appendComment(WorkflowComment comment) {
            comments.add(comment);
            return comment;
        }

        @Override
        public List<WorkflowTransition> findTransitions(UUID workflowItemId) {
            return transitions.stream()
                    .filter(transition -> transition.workflowItemId().equals(workflowItemId))
                    .sorted(Comparator.comparingLong(WorkflowTransition::sequence))
                    .toList();
        }

        @Override
        public List<WorkflowComment> findComments(UUID workflowItemId) {
            return comments.stream()
                    .filter(comment -> comment.workflowItemId().equals(workflowItemId))
                    .toList();
        }

        @Override
        public long nextTransitionSequence(UUID workflowItemId) {
            return findTransitions(workflowItemId).size();
        }

        public int itemCount() {
            return items.size();
        }
    }

    /** SLA rules a test can rewrite between evaluations to prove the runtime-configuration rule. */
    public static final class MutableSlaRuleRepository implements SlaRuleRepository {

        private List<SlaPolicy.SlaRule> rules = new ArrayList<>();

        public MutableSlaRuleRepository withRules(SlaPolicy.SlaRule... newRules) {
            this.rules = new ArrayList<>(List.of(newRules));
            return this;
        }

        @Override
        public List<SlaPolicy.SlaRule> findEffectiveRules(Instant at) {
            return List.copyOf(rules);
        }
    }

    /** Records which workflow items the trip and sweep paths asked to be raised. */
    public static final class RecordingWorkflowRaiser implements FleetWorkflowRaiser {

        private final List<String> raised = new ArrayList<>();

        @Override
        public FleetWorkflowItem raiseInspectionDefect(VehicleInspection inspection, Vehicle vehicle,
                ActorContext actor, SourceChannel sourceChannel) {
            raised.add("INSPECTION_DEFECT|" + inspection.id());
            return null;
        }

        @Override
        public FleetWorkflowItem raiseComplianceExpiry(ComplianceDocument document, Vehicle vehicle,
                boolean expired, ActorContext actor, SourceChannel sourceChannel) {
            raised.add("COMPLIANCE|" + document.id() + "|" + (expired ? "EXPIRED" : "EXPIRING"));
            return null;
        }

        @Override
        public FleetWorkflowItem raiseServiceDue(Vehicle vehicle, boolean overdue, ActorContext actor,
                SourceChannel sourceChannel) {
            raised.add("SERVICE|" + vehicle.id() + "|" + (overdue ? "OVERDUE" : "DUE"));
            return null;
        }

        public List<String> raised() {
            return List.copyOf(raised);
        }
    }
}
