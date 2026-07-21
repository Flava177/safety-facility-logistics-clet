package gh.edu.clet.sfl.fleetlogistics.fleet.application.port;

import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.SiteScopeFilter;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.DriverEligibilityStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.DriverLifecycleStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.DriverProfileReference;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Outbound port for the driver profile register (SRS-SFL-S166-01). No delete: history is retained. */
public interface DriverProfileRepository {

    DriverProfileReference save(DriverProfileReference driver);

    Optional<DriverProfileReference> findById(UUID id);

    /** Row-locked load, used when an assignment must serialise against a concurrent booking. */
    Optional<DriverProfileReference> findByIdForUpdate(UUID id);

    Optional<DriverProfileReference> findActiveByStaffReference(SiteCode siteCode, String staffReference);

    Optional<DriverProfileReference> findActiveByLicenceNumber(SiteCode siteCode, String licenceNumber);

    DriverPage search(DriverSearchCriteria criteria, SiteScopeFilter scope);

    List<DriverProfileReference> findAllInScope(SiteScopeFilter scope);

    /** Drivers whose licence or medical clearance expires on or before {@code threshold}. */
    List<DriverProfileReference> findExpiringOnOrBefore(LocalDate threshold);

    record DriverSearchCriteria(
            String siteCode,
            DriverLifecycleStatus lifecycleStatus,
            DriverEligibilityStatus eligibilityStatus,
            String responsibleUnit,
            LocalDate licenceExpiringBefore,
            String nameOrReferenceContains,
            int page,
            int size,
            String sort) {
    }

    record DriverPage(List<DriverProfileReference> content, int page, int size, long totalElements, int totalPages,
            String sort) {
    }
}
