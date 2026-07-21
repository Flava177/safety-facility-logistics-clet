package gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.persistence;

import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.VehicleRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.SiteScopeFilter;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.RegistrationNumber;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.Vehicle;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** JPA adapter for {@link VehicleRepository}. */
@Component
class JpaVehicleRepositoryAdapter implements VehicleRepository {

    /** Sort applied when the caller does not choose one; the id keeps paging stable. */
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
    private static final int MAX_PAGE_SIZE = 200;

    private final VehicleJpaRepository vehicles;

    JpaVehicleRepositoryAdapter(VehicleJpaRepository vehicles) {
        this.vehicles = vehicles;
    }

    @Override
    @Transactional
    public Vehicle save(Vehicle vehicle) {
        VehicleEntity entity = vehicles.findById(vehicle.id())
                .map(existing -> {
                    existing.applyFrom(vehicle);
                    return existing;
                })
                .orElseGet(() -> VehicleEntity.from(vehicle));
        return vehicles.save(entity).toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Vehicle> findById(UUID id) {
        return vehicles.findById(id).map(VehicleEntity::toDomain);
    }

    @Override
    @Transactional
    public Optional<Vehicle> findByIdForUpdate(UUID id) {
        return vehicles.findByIdForUpdate(id).map(VehicleEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Vehicle> findActiveByRegistration(SiteCode siteCode, RegistrationNumber registrationNumber) {
        return vehicles.findActiveByRegistration(siteCode.value(), registrationNumber.value())
                .map(VehicleEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Vehicle> findActiveByVin(SiteCode siteCode, String vin) {
        if (vin == null || vin.isBlank()) {
            return Optional.empty();
        }
        return vehicles.findActiveByVin(siteCode.value(), vin).map(VehicleEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public VehiclePage search(VehicleSearchCriteria criteria, SiteScopeFilter scope) {
        Page<VehicleEntity> page = vehicles.search(
                scope.allSites(),
                scope.allSites() ? List.of("*") : List.copyOf(scope.sites()),
                normalise(criteria.siteCode()),
                criteria.lifecycleStatus(),
                criteria.serviceStatus(),
                criteria.availabilityStatus(),
                criteria.category(),
                criteria.responsibleUnit(),
                criteria.registrationNumberContains(),
                pageRequest(criteria.page(), criteria.size(), criteria.sort()));

        return new VehiclePage(
                page.getContent().stream().map(VehicleEntity::toDomain).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(),
                page.getSort().toString());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Vehicle> findAllInScope(SiteScopeFilter scope) {
        return vehicles.findAllInScope(scope.allSites(),
                        scope.allSites() ? List.of("*") : List.copyOf(scope.sites())).stream()
                .map(VehicleEntity::toDomain)
                .toList();
    }

    /**
     * Builds the page request, always appending the id as a tiebreak so a page boundary that falls
     * between two rows with the same sort value cannot skip or repeat a record.
     */
    static PageRequest pageRequest(int page, int size, String sort) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 20 : Math.min(size, MAX_PAGE_SIZE);
        if (sort == null || sort.isBlank()) {
            return PageRequest.of(safePage, safeSize, DEFAULT_SORT);
        }
        String[] parts = sort.split(",");
        Sort.Direction direction = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].strip())
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;
        Sort requested = Sort.by(direction, parts[0].strip()).and(Sort.by(Sort.Order.desc("id")));
        return PageRequest.of(safePage, safeSize, requested);
    }

    private static String normalise(String siteCode) {
        return siteCode == null || siteCode.isBlank()
                ? null
                : siteCode.strip().toUpperCase(java.util.Locale.ROOT);
    }
}
