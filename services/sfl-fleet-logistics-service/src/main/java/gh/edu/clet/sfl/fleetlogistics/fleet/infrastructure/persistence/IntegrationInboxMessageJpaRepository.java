package gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.persistence;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.IntegrationMessageStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

interface IntegrationInboxMessageJpaRepository extends JpaRepository<IntegrationInboxMessageEntity, UUID> {

    Optional<IntegrationInboxMessageEntity> findBySourceSystemAndIdempotencyKey(String sourceSystem,
            String idempotencyKey);

    List<IntegrationInboxMessageEntity> findByOrderByReceivedAtDescIdDesc(Pageable pageable);

    long countByStatus(IntegrationMessageStatus status);

    /**
     * Inbox search.
     *
     * <p>Each filter applies only when supplied, and every one is **cast** in its {@code is null}
     * test. Hibernate expands a named parameter used twice into two JDBC placeholders, so the one
     * inside {@code is null} stands alone and Postgres cannot infer its type. That is the same
     * defect that made {@code GET /fleet/audit/records} return 500 on every call before the S168
     * round.
     */
    @Query("""
            select message from IntegrationInboxMessageEntity message
             where (cast(:sourceSystem as string) is null or message.sourceSystem = :sourceSystem)
               and (cast(:status as string) is null or message.status = :status)
               and (cast(:eventType as string) is null or message.eventType = :eventType)
             order by message.receivedAt desc, message.id desc
            """)
    List<IntegrationInboxMessageEntity> search(@Param("sourceSystem") String sourceSystem,
            @Param("status") IntegrationMessageStatus status, @Param("eventType") String eventType,
            Pageable pageable);
}
