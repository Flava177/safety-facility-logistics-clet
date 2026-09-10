package gh.edu.clet.sfl.facilities.shared.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
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

    /**
     * One bounded, ascending page of the chain starting just after {@code afterSequenceNo}.
     *
     * <p>The keyset ({@code sequenceNo > cursor}, not an offset) is what makes each page O(page size)
     * regardless of how far into the table it starts - an {@code OFFSET} that grows with every page
     * would make replaying the tail of a large chain cost as much as replaying the whole thing twice
     * over. See {@code JpaAuditAdapter.verifyChain} for why this replaces
     * {@code findAllByOrderBySequenceNoAsc()}, which loaded the entire append-only table into memory on
     * every call.
     */
    List<AuditRecordEntity> findBySequenceNoGreaterThanOrderBySequenceNoAsc(long afterSequenceNo,
            Pageable pageable);

    List<AuditRecordEntity> findByResourceTypeAndResourceIdOrderBySequenceNoAsc(String resourceType,
            String resourceId);
}
