package gh.edu.clet.sfl.fleetlogistics.fleet.application.port;

import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.SiteScopeFilter;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ComplianceDocument;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ComplianceDocumentType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Outbound port for vehicle compliance documents (SRS-SFL-S166-01). */
public interface ComplianceDocumentRepository {

    ComplianceDocument save(ComplianceDocument document);

    Optional<ComplianceDocument> findById(UUID id);

    /** Every document for a vehicle, newest expiry first. */
    List<ComplianceDocument> findByVehicle(UUID vehicleId);

    /** Documents that still provide cover: status ACTIVE or EXPIRING. */
    List<ComplianceDocument> findCurrentByVehicle(UUID vehicleId);

    /** The current document of a given type, used to supersede it when a replacement is registered. */
    Optional<ComplianceDocument> findCurrentByVehicleAndType(UUID vehicleId, ComplianceDocumentType documentType);

    /** Documents expiring on or before {@code threshold}, for the scheduled expiry sweep. */
    List<ComplianceDocument> findCurrentExpiringOnOrBefore(LocalDate threshold);

    /** Site-filtered documents for dashboard indicators and drilldowns. */
    List<ComplianceDocument> findInScope(SiteScopeFilter scope);
}
