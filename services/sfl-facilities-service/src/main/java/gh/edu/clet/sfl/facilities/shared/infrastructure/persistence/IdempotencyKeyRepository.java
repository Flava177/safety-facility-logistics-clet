package gh.edu.clet.sfl.facilities.shared.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKeyEntity, UUID> {

    Optional<IdempotencyKeyEntity> findByOperationAndIdempotencyKey(String operation, String idempotencyKey);
}
