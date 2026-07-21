package gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.persistence;

import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.VehicleInspectionRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.SiteScopeFilter;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleInspection;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** JPA adapter for {@link VehicleInspectionRepository}. */
@Component
class JpaVehicleInspectionRepositoryAdapter implements VehicleInspectionRepository {

    private final VehicleInspectionJpaRepository inspections;
    private final ObjectMapper objectMapper;

    JpaVehicleInspectionRepositoryAdapter(VehicleInspectionJpaRepository inspections, ObjectMapper objectMapper) {
        this.inspections = inspections;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public VehicleInspection save(VehicleInspection inspection) {
        VehicleInspectionEntity entity = inspections.findById(inspection.id())
                .map(existing -> {
                    existing.applyFrom(inspection, objectMapper);
                    return existing;
                })
                .orElseGet(() -> VehicleInspectionEntity.from(inspection, objectMapper));
        return inspections.save(entity).toDomain(objectMapper);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<VehicleInspection> findById(UUID id) {
        return inspections.findById(id).map(entity -> entity.toDomain(objectMapper));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<VehicleInspection> findLatestByVehicle(UUID vehicleId) {
        return inspections.findFirstByVehicleIdOrderByPerformedAtDescIdDesc(vehicleId)
                .map(entity -> entity.toDomain(objectMapper));
    }

    @Override
    @Transactional(readOnly = true)
    public List<VehicleInspection> findByVehicle(UUID vehicleId) {
        return inspections.findByVehicleIdOrderByPerformedAtDescIdDesc(vehicleId).stream()
                .map(entity -> entity.toDomain(objectMapper))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<VehicleInspection> findByTrip(UUID tripId) {
        return inspections.findByTripIdOrderByPerformedAtAsc(tripId).stream()
                .map(entity -> entity.toDomain(objectMapper))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<VehicleInspection> findLatestInScope(SiteScopeFilter scope) {
        return inspections.findLatestInScope(scope.allSites(), scopeList(scope)).stream()
                .map(entity -> entity.toDomain(objectMapper))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<VehicleInspection> findFailuresSince(SiteScopeFilter scope, Instant from) {
        return inspections.findFailuresSince(scope.allSites(), scopeList(scope), from).stream()
                .map(entity -> entity.toDomain(objectMapper))
                .toList();
    }

    private static List<String> scopeList(SiteScopeFilter scope) {
        return scope.allSites() ? List.of("*") : List.copyOf(scope.sites());
    }
}
