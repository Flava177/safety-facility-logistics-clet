package gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.messaging;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface OutboxMessageRepository extends JpaRepository<OutboxMessageEntity, UUID> {

    /**
     * Claims a batch of due messages.
     *
     * <p>{@code SKIP LOCKED} lets several service instances drain the same outbox concurrently without
     * blocking each other or delivering the same message twice in one pass.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@jakarta.persistence.QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            select m from OutboxMessageEntity m
            where m.status = 'PENDING'
              and (m.nextAttemptAt is null or m.nextAttemptAt <= :now)
            order by m.createdAt asc
            """)
    List<OutboxMessageEntity> claimDue(@Param("now") Instant now, Pageable pageable);

    long countByStatus(String status);

    List<OutboxMessageEntity> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    List<OutboxMessageEntity> findByAggregateIdOrderByCreatedAtAsc(String aggregateId);

    List<OutboxMessageEntity> findByEventTypeOrderByCreatedAtAsc(String eventType);
}
