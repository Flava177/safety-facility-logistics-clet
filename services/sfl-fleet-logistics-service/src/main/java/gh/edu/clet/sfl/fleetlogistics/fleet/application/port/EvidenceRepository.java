package gh.edu.clet.sfl.fleetlogistics.fleet.application.port;

import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.SiteScopeFilter;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.EvidenceReference;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Evidence metadata persistence port (SRS-SFL-S166-03). */
public interface EvidenceRepository {

    EvidenceReference save(EvidenceReference evidence);

    Optional<EvidenceReference> findById(UUID id);

    List<EvidenceReference> findByRelatedRecord(String relatedRecordType, String relatedRecordId,
            SiteScopeFilter scope);
}
