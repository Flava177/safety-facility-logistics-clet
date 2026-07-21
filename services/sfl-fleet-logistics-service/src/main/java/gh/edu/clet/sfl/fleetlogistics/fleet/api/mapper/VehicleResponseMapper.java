package gh.edu.clet.sfl.fleetlogistics.fleet.api.mapper;

import gh.edu.clet.sfl.fleetlogistics.fleet.api.response.ComplianceDocumentResponse;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.response.ServiceHistoryResponse;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.response.ServiceRecordResponse;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.response.VehicleResponse;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ComplianceDocument;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.Vehicle;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleServiceRecord;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Maps vehicle aggregates to API responses.
 *
 * <p>The sensitive-field decision lives here, in one place: {@code includeSensitive} comes from the
 * caller's permission and decides whether the VIN is returned in full or masked.
 */
@Component
public class VehicleResponseMapper {

    public VehicleResponse toResponse(Vehicle vehicle, boolean includeSensitive) {
        String vin = vehicle.vin() == null
                ? null
                : (includeSensitive ? vehicle.vin().value() : vehicle.vin().masked());
        boolean masked = vehicle.vin() != null && !includeSensitive;

        return new VehicleResponse(
                vehicle.id(),
                vehicle.registrationNumber().value(),
                vin,
                masked,
                vehicle.specification().make(),
                vehicle.specification().model(),
                vehicle.specification().manufactureYear(),
                vehicle.specification().category(),
                vehicle.specification().capacity(),
                vehicle.siteCode().value(),
                vehicle.responsibleUnit(),
                vehicle.operationalOwner(),
                vehicle.acquisitionReference(),
                vehicle.lifecycleStatus(),
                vehicle.serviceStatus(),
                vehicle.availabilityStatus(),
                vehicle.odometer().value(),
                vehicle.odometer().unit().name(),
                vehicle.odometer().source().name(),
                vehicle.odometer().recordedAt(),
                vehicle.restrictedUse().emergencyOnly(),
                vehicle.restrictedUse().allowedOperatingModes(),
                vehicle.currentTripId(),
                vehicle.metadata().createdBy(),
                vehicle.metadata().createdAt(),
                vehicle.metadata().lastModifiedBy(),
                vehicle.metadata().lastModifiedAt(),
                vehicle.metadata().version(),
                vehicle.metadata().sourceChannel().name(),
                vehicle.metadata().auditCorrelationId());
    }

    public ComplianceDocumentResponse toResponse(ComplianceDocument document, Instant now) {
        return new ComplianceDocumentResponse(
                document.id(),
                document.vehicleId(),
                document.siteCode().value(),
                document.documentType(),
                document.documentType().isMandatory(),
                document.documentReference(),
                document.issuingAuthority(),
                document.issuedOn(),
                document.expiresOn(),
                document.daysUntilExpiry(now),
                document.status(),
                document.evidenceId(),
                document.retentionClass(),
                document.metadata().createdAt(),
                document.metadata().createdBy(),
                document.metadata().version());
    }

    public ServiceRecordResponse toResponse(VehicleServiceRecord record) {
        return new ServiceRecordResponse(
                record.id(),
                record.vehicleId(),
                record.siteCode().value(),
                record.serviceType(),
                record.performedOn(),
                record.odometerAtService(),
                record.nextDueOn(),
                record.nextDueOdometer(),
                record.providerReference(),
                record.workSummary(),
                record.outcome(),
                record.evidenceId(),
                record.metadata().createdAt(),
                record.metadata().createdBy(),
                record.metadata().version());
    }

    public ServiceHistoryResponse toServiceHistory(Vehicle vehicle, List<VehicleServiceRecord> history) {
        VehicleServiceRecord latest = history.isEmpty() ? null : history.get(0);
        return new ServiceHistoryResponse(
                vehicle.id(),
                vehicle.serviceStatus(),
                latest == null ? null : latest.nextDueOn(),
                latest == null ? null : latest.nextDueOdometer(),
                vehicle.odometer().value(),
                history.stream().map(this::toResponse).toList());
    }
}
