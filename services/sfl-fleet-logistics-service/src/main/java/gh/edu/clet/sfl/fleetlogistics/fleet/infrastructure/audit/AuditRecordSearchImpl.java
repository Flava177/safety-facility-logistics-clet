package gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.audit;

import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.AuditPort.AuditQuery;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;

/**
 * Criteria implementation of {@link AuditRecordSearch}.
 *
 * <p>Each supplied filter adds one predicate; an omitted filter adds nothing at all. That is the
 * whole fix — see the interface for why the previous JPQL could not run.
 *
 * <p>Ordering stays {@code sequenceNo} descending, which is the append order of the hash chain and
 * therefore both stable and meaningful: the chain defines the sequence, so paging over it cannot
 * skip or repeat a record.
 */
class AuditRecordSearchImpl implements AuditRecordSearch {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<AuditRecordEntity> searchRecords(boolean allSites, List<String> siteScopes, AuditQuery query) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<AuditRecordEntity> criteria = builder.createQuery(AuditRecordEntity.class);
        Root<AuditRecordEntity> record = criteria.from(AuditRecordEntity.class);

        List<Predicate> predicates = new ArrayList<>();
        // An actor with no site restriction sees everything; otherwise the scope is a hard filter,
        // never an optional one, so it is applied before any of the caller's own criteria.
        if (!allSites) {
            predicates.add(siteScopes.isEmpty()
                    ? builder.disjunction()
                    : record.get("siteScope").in(siteScopes));
        }
        if (query.resourceType() != null && !query.resourceType().isBlank()) {
            predicates.add(builder.equal(record.get("resourceType"), query.resourceType()));
        }
        if (query.resourceId() != null && !query.resourceId().isBlank()) {
            predicates.add(builder.equal(record.get("resourceId"), query.resourceId()));
        }
        if (query.actorId() != null && !query.actorId().isBlank()) {
            predicates.add(builder.equal(record.get("actorId"), query.actorId()));
        }
        if (query.action() != null) {
            predicates.add(builder.equal(record.get("action"), query.action()));
        }
        if (query.from() != null) {
            predicates.add(builder.greaterThanOrEqualTo(record.get("occurredAt"), query.from()));
        }
        if (query.to() != null) {
            predicates.add(builder.lessThanOrEqualTo(record.get("occurredAt"), query.to()));
        }

        criteria.select(record)
                .where(predicates.toArray(Predicate[]::new))
                .orderBy(builder.desc(record.get("sequenceNo")));

        int size = query.size() <= 0 ? 50 : query.size();
        int page = Math.max(query.page(), 0);
        return entityManager.createQuery(criteria)
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList();
    }
}
