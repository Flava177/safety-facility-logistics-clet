package gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.audit;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface AuditChainStateRepository extends JpaRepository<AuditChainStateEntity, Short> {

    /**
     * Locks the chain head for the duration of the caller's transaction so audit appends serialise.
     * Concurrent appenders queue here rather than forking the chain.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AuditChainStateEntity s where s.id = 1")
    Optional<AuditChainStateEntity> lockChainHead();
}
