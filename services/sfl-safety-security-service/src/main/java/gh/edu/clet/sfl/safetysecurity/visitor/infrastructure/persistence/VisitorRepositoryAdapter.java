package gh.edu.clet.sfl.safetysecurity.visitor.infrastructure.persistence;

import gh.edu.clet.sfl.safetysecurity.visitor.application.port.VisitorRepository;
import gh.edu.clet.sfl.safetysecurity.visitor.domain.model.VisitStatus;
import gh.edu.clet.sfl.safetysecurity.visitor.domain.model.VisitorApproval;
import gh.edu.clet.sfl.safetysecurity.visitor.domain.model.VisitorVisit;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

/** The one adapter behind {@link VisitorRepository}, following {@code JpaBookingRepositoryAdapter}'s shape. */
@Repository
public class VisitorRepositoryAdapter implements VisitorRepository {

    /**
     * Stand-ins for "no lower bound"/"no upper bound" on a time-ranged search - see {@code
     * JpaBookingRepositoryAdapter} for why a bare null {@code Instant} parameter cannot be used here.
     */
    private static final Instant UNBOUNDED_FROM = Instant.parse("1900-01-01T00:00:00Z");
    private static final Instant UNBOUNDED_TO = Instant.parse("9999-12-31T00:00:00Z");

    private final VisitorVisitJpaRepository visits;
    private final VisitorApprovalJpaRepository approvals;

    public VisitorRepositoryAdapter(VisitorVisitJpaRepository visits, VisitorApprovalJpaRepository approvals) {
        this.visits = visits;
        this.approvals = approvals;
    }

    @Override
    public VisitorVisit saveVisit(VisitorVisit visit) {
        VisitorVisitJpaEntity entity = visits.findById(visit.id()).orElseGet(VisitorVisitJpaEntity::new);
        entity.apply(visit);
        // saveAndFlush, not save: recordVersion is a JPA @Version field Hibernate only increments at
        // flush time. A plain save() defers the flush to transaction commit, so toDomain() below would
        // read the pre-increment value and hand the caller a version number the database has already
        // moved past - the next command's requireVersion check would then fail against its own,
        // correctly-persisted change. Flushing here makes the returned aggregate's version match what
        // is actually committed.
        return visits.saveAndFlush(entity).toDomain();
    }

    @Override
    public Optional<VisitorVisit> findVisit(UUID id) {
        return visits.findById(id).map(VisitorVisitJpaEntity::toDomain);
    }

    @Override
    public List<VisitorVisit> search(VisitQuery query) {
        return visits.search(query.siteCode(), query.status(), query.hostId(), from(query.from()), to(query.to()),
                        page(query.limit())).stream()
                .map(VisitorVisitJpaEntity::toDomain)
                .toList();
    }

    @Override
    public List<VisitorVisit> findOnSite(String siteCode) {
        return visits.findBySiteCodeAndStatus(siteCode, VisitStatus.CHECKED_IN).stream()
                .map(VisitorVisitJpaEntity::toDomain)
                .toList();
    }

    @Override
    public VisitorApproval saveApproval(VisitorApproval approval) {
        VisitorApprovalJpaEntity entity = approvals.findById(approval.id())
                .orElseGet(VisitorApprovalJpaEntity::new);
        entity.apply(approval);
        return approvals.save(entity).toDomain();
    }

    @Override
    public List<VisitorApproval> findApprovals(UUID visitId) {
        return approvals.findByVisitIdOrderByDecidedAtAsc(visitId).stream()
                .map(VisitorApprovalJpaEntity::toDomain)
                .toList();
    }

    private static Instant from(Instant value) {
        return value == null ? UNBOUNDED_FROM : value;
    }

    private static Instant to(Instant value) {
        return value == null ? UNBOUNDED_TO : value;
    }

    private static PageRequest page(int limit) {
        return PageRequest.of(0, Math.max(1, Math.min(limit, 500)));
    }
}
