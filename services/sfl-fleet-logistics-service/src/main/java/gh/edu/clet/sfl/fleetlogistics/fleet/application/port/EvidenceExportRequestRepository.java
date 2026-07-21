package gh.edu.clet.sfl.fleetlogistics.fleet.application.port;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.EvidenceExportRequest;
import java.util.Optional;
import java.util.UUID;

/** Evidence export approval persistence port (SRS-SFL-S166-03). */
public interface EvidenceExportRequestRepository {

    EvidenceExportRequest save(EvidenceExportRequest request);

    Optional<EvidenceExportRequest> findById(UUID id);
}
