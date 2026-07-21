package gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.persistence;

import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.DriverProfileRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.SiteScopeFilter;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.DriverProfileReference;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** JPA adapter for {@link DriverProfileRepository}. */
@Component
class JpaDriverProfileRepositoryAdapter implements DriverProfileRepository {

    private final DriverProfileJpaRepository drivers;

    JpaDriverProfileRepositoryAdapter(DriverProfileJpaRepository drivers) {
        this.drivers = drivers;
    }

    @Override
    @Transactional
    public DriverProfileReference save(DriverProfileReference driver) {
        DriverProfileEntity entity = drivers.findById(driver.id())
                .map(existing -> {
                    existing.applyFrom(driver);
                    return existing;
                })
                .orElseGet(() -> DriverProfileEntity.from(driver));
        return drivers.save(entity).toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DriverProfileReference> findById(UUID id) {
        return drivers.findById(id).map(DriverProfileEntity::toDomain);
    }

    @Override
    @Transactional
    public Optional<DriverProfileReference> findByIdForUpdate(UUID id) {
        return drivers.findByIdForUpdate(id).map(DriverProfileEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DriverProfileReference> findActiveByStaffReference(SiteCode siteCode, String staffReference) {
        return drivers.findActiveByStaffReference(siteCode.value(), staffReference)
                .map(DriverProfileEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DriverProfileReference> findActiveByLicenceNumber(SiteCode siteCode, String licenceNumber) {
        return drivers.findActiveByLicenceNumber(siteCode.value(), licenceNumber)
                .map(DriverProfileEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public DriverPage search(DriverSearchCriteria criteria, SiteScopeFilter scope) {
        Page<DriverProfileEntity> page = drivers.search(
                scope.allSites(),
                scopeList(scope),
                normalise(criteria.siteCode()),
                criteria.lifecycleStatus(),
                criteria.eligibilityStatus(),
                criteria.responsibleUnit(),
                criteria.licenceExpiringBefore(),
                criteria.nameOrReferenceContains(),
                JpaVehicleRepositoryAdapter.pageRequest(criteria.page(), criteria.size(), criteria.sort()));

        return new DriverPage(page.getContent().stream().map(DriverProfileEntity::toDomain).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(),
                page.getSort().toString());
    }

    @Override
    @Transactional(readOnly = true)
    public List<DriverProfileReference> findAllInScope(SiteScopeFilter scope) {
        return drivers.findAllInScope(scope.allSites(), scopeList(scope)).stream()
                .map(DriverProfileEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DriverProfileReference> findExpiringOnOrBefore(LocalDate threshold) {
        return drivers.findExpiringOnOrBefore(threshold).stream()
                .map(DriverProfileEntity::toDomain)
                .toList();
    }

    private static List<String> scopeList(SiteScopeFilter scope) {
        return scope.allSites() ? List.of("*") : List.copyOf(scope.sites());
    }

    private static String normalise(String siteCode) {
        return siteCode == null || siteCode.isBlank() ? null : siteCode.strip().toUpperCase(Locale.ROOT);
    }
}
