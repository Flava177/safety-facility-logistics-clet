package gh.edu.clet.sfl.facilities.shared.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * The audit store.
 *
 * <p>The filtered search is a {@link JpaSpecificationExecutor} rather than a null-tolerant JPQL query
 * with {@code (:param is null or column = :param)} clauses. That form reads well and does not work:
 * PostgreSQL cannot infer the type of a bound {@code null}, and the query fails at runtime with
 * "could not determine data type of parameter". A specification omits the predicate entirely when a
 * filter is absent, so no null is ever bound. Found by running the endpoint against a real database.
 */
interface AuditRecordRepository extends JpaRepository<AuditRecordEntity, UUID>,
        JpaSpecificationExecutor<AuditRecordEntity> {

    List<AuditRecordEntity> findAllByOrderBySequenceNoAsc();

    List<AuditRecordEntity> findByResourceTypeAndResourceIdOrderBySequenceNoAsc(String resourceType,
            String resourceId);
}
