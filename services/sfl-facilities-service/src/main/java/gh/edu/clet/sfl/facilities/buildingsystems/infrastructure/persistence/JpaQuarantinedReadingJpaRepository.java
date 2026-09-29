package gh.edu.clet.sfl.facilities.buildingsystems.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.buildingsystems.domain.QuarantineStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface JpaQuarantinedReadingJpaRepository extends JpaRepository<QuarantinedReadingRecord, UUID> {

    List<QuarantinedReadingRecord> findBySiteCodeAndStatus(String siteCode, QuarantineStatus status);

    List<QuarantinedReadingRecord> findBySiteCode(String siteCode);

    List<QuarantinedReadingRecord> findByStatus(QuarantineStatus status);

    List<QuarantinedReadingRecord> findAllByOrderBySiteCode();

    List<QuarantinedReadingRecord> findBySourceIdAndIdempotencyKeyOrderByItemIndex(String sourceId,
            String idempotencyKey);
}
