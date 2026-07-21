package gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.persistence;

import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.EvidenceExportRequestRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.EvidenceExportRequest;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** JPA adapter for export approval requests. */
@Component
class JpaEvidenceExportRequestRepositoryAdapter implements EvidenceExportRequestRepository {

    private final EvidenceExportRequestJpaRepository requests;

    JpaEvidenceExportRequestRepositoryAdapter(EvidenceExportRequestJpaRepository requests) {
        this.requests = requests;
    }

    @Override
    @Transactional
    public EvidenceExportRequest save(EvidenceExportRequest request) {
        EvidenceExportRequestEntity entity = requests.findById(request.id())
                .map(existing -> {
                    existing.applyFrom(request);
                    return existing;
                })
                .orElseGet(() -> EvidenceExportRequestEntity.from(request));
        return requests.save(entity).toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EvidenceExportRequest> findById(UUID id) {
        return requests.findById(id).map(EvidenceExportRequestEntity::toDomain);
    }
}
