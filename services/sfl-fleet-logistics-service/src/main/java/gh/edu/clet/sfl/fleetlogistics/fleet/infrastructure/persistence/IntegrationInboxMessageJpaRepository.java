package gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.persistence;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.IntegrationMessageStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface IntegrationInboxMessageJpaRepository extends JpaRepository<IntegrationInboxMessageEntity, UUID> {

    Optional<IntegrationInboxMessageEntity> findBySourceSystemAndIdempotencyKey(String sourceSystem,
            String idempotencyKey);

    List<IntegrationInboxMessageEntity> findByOrderByReceivedAtDescIdDesc(Pageable pageable);

    long countByStatus(IntegrationMessageStatus status);
}
