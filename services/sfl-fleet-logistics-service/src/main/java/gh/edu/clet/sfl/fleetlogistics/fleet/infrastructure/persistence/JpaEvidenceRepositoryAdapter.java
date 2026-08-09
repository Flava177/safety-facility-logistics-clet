package gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.persistence;

import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.EvidenceRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.SiteScopeFilter;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.EvidenceReference;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** JPA adapter for evidence metadata. */
@Component
class JpaEvidenceRepositoryAdapter implements EvidenceRepository {

    private final EvidenceReferenceJpaRepository evidence;

    JpaEvidenceRepositoryAdapter(EvidenceReferenceJpaRepository evidence) {
        this.evidence = evidence;
    }

    @Override
    @Transactional
    public EvidenceReference save(EvidenceReference reference) {
        EvidenceReferenceEntity entity = evidence.findById(reference.id())
                .map(existing -> {
                    existing.applyFrom(reference);
                    return existing;
                })
                .orElseGet(() -> EvidenceReferenceEntity.from(reference));
        return evidence.save(entity).toDomain();
    }

    @Override
    @Transactional
    public EvidenceReference saveAndFlush(EvidenceReference reference) {
        EvidenceReference saved = save(reference);
        // The row has to exist in the database, not merely in the persistence context, before the
        // JDBC insert into fleet_evidence_files can satisfy its foreign key. See the port for why.
        evidence.flush();
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EvidenceReference> findById(UUID id) {
        return evidence.findById(id).map(EvidenceReferenceEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EvidenceReference> findByRelatedRecord(String relatedRecordType, String relatedRecordId,
            SiteScopeFilter scope) {
        return evidence.findByRelatedRecordInScope(scope.allSites(),
                        scope.allSites() ? List.of("*") : List.copyOf(scope.sites()),
                        relatedRecordType, relatedRecordId)
                .stream()
                .map(EvidenceReferenceEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EvidenceReference> findBySha256(String siteCode, String sha256Hash, UUID excludingId) {
        return evidence.findBySha256(siteCode, sha256Hash, excludingId).stream()
                .map(EvidenceReferenceEntity::toDomain)
                .toList();
    }
}
