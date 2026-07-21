package gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.persistence;

import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.VehicleServiceRecordRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.SiteScopeFilter;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleServiceRecord;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** JPA adapter for {@link VehicleServiceRecordRepository}. */
@Component
class JpaVehicleServiceRecordRepositoryAdapter implements VehicleServiceRecordRepository {

    private final VehicleServiceRecordJpaRepository serviceRecords;

    JpaVehicleServiceRecordRepositoryAdapter(VehicleServiceRecordJpaRepository serviceRecords) {
        this.serviceRecords = serviceRecords;
    }

    @Override
    @Transactional
    public VehicleServiceRecord save(VehicleServiceRecord record) {
        VehicleServiceRecordEntity entity = serviceRecords.findById(record.id())
                .map(existing -> {
                    existing.applyFrom(record);
                    return existing;
                })
                .orElseGet(() -> VehicleServiceRecordEntity.from(record));
        return serviceRecords.save(entity).toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<VehicleServiceRecord> findById(UUID id) {
        return serviceRecords.findById(id).map(VehicleServiceRecordEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VehicleServiceRecord> findByVehicle(UUID vehicleId) {
        return serviceRecords.findByVehicleIdOrderByPerformedOnDescIdDesc(vehicleId).stream()
                .map(VehicleServiceRecordEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<VehicleServiceRecord> findLatestByVehicle(UUID vehicleId) {
        return serviceRecords.findFirstByVehicleIdOrderByPerformedOnDescIdDesc(vehicleId)
                .map(VehicleServiceRecordEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VehicleServiceRecord> findLatestInScope(SiteScopeFilter scope) {
        return serviceRecords.findLatestInScope(scope.allSites(),
                        scope.allSites() ? List.of("*") : List.copyOf(scope.sites())).stream()
                .map(VehicleServiceRecordEntity::toDomain)
                .toList();
    }
}
