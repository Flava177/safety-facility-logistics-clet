package gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.persistence;

import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.ComplianceDocumentRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.SiteScopeFilter;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ComplianceDocument;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ComplianceDocumentType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** JPA adapter for {@link ComplianceDocumentRepository}. */
@Component
class JpaComplianceDocumentRepositoryAdapter implements ComplianceDocumentRepository {

    private final ComplianceDocumentJpaRepository complianceDocuments;

    JpaComplianceDocumentRepositoryAdapter(ComplianceDocumentJpaRepository complianceDocuments) {
        this.complianceDocuments = complianceDocuments;
    }

    @Override
    @Transactional
    public ComplianceDocument save(ComplianceDocument document) {
        ComplianceDocumentEntity entity = complianceDocuments.findById(document.id())
                .map(existing -> {
                    existing.applyFrom(document);
                    return existing;
                })
                .orElseGet(() -> ComplianceDocumentEntity.from(document));
        return complianceDocuments.save(entity).toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ComplianceDocument> findById(UUID id) {
        return complianceDocuments.findById(id).map(ComplianceDocumentEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ComplianceDocument> findByVehicle(UUID vehicleId) {
        return complianceDocuments.findByVehicleIdOrderByExpiresOnDesc(vehicleId).stream()
                .map(ComplianceDocumentEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ComplianceDocument> findCurrentByVehicle(UUID vehicleId) {
        return complianceDocuments.findCurrentByVehicle(vehicleId).stream()
                .map(ComplianceDocumentEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ComplianceDocument> findCurrentByVehicleAndType(UUID vehicleId,
            ComplianceDocumentType documentType) {
        return complianceDocuments.findCurrentByVehicleAndType(vehicleId, documentType)
                .map(ComplianceDocumentEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ComplianceDocument> findCurrentExpiringOnOrBefore(LocalDate threshold) {
        return complianceDocuments.findCurrentExpiringOnOrBefore(threshold).stream()
                .map(ComplianceDocumentEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ComplianceDocument> findInScope(SiteScopeFilter scope) {
        return complianceDocuments.findInScope(scope.allSites(),
                        scope.allSites() ? List.of("*") : List.copyOf(scope.sites())).stream()
                .map(ComplianceDocumentEntity::toDomain)
                .toList();
    }
}
