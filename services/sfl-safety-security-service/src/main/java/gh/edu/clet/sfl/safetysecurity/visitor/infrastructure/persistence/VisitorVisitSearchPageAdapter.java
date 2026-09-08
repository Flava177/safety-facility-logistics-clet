package gh.edu.clet.sfl.safetysecurity.visitor.infrastructure.persistence;

import gh.edu.clet.sfl.safetysecurity.emergency.application.port.EmergencyRepository.EmergencyPage;
import gh.edu.clet.sfl.safetysecurity.emergency.application.port.EmergencyRepository.Paging;
import gh.edu.clet.sfl.safetysecurity.visitor.application.port.VisitorSearchPageRepository;
import gh.edu.clet.sfl.safetysecurity.visitor.domain.model.VisitStatus;
import gh.edu.clet.sfl.safetysecurity.visitor.domain.model.VisitorVisit;
import java.time.Instant;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

/**
 * Backs {@link VisitorSearchPageRepository} with {@link VisitorVisitJpaRepository#searchPage}, a
 * genuine {@code Page<T>}/{@code Pageable} query rather than the {@code List<T>} + client-{@code
 * limit} shape {@code VisitorRepositoryAdapter#search} still serves. A separate class, not a new
 * method on that adapter - see the port's Javadoc for why.
 */
@Repository
class VisitorVisitSearchPageAdapter implements VisitorSearchPageRepository {

    /**
     * Same unbounded-range sentinels as {@code VisitorRepositoryAdapter} - duplicated rather than
     * shared because that class is off-limits here (see this package's other {@code SearchPageAdapter}
     * classes for why), and a bare null {@code Instant} parameter leaves PostgreSQL unable to infer
     * the comparison's data type.
     */
    private static final Instant UNBOUNDED_FROM = Instant.parse("1900-01-01T00:00:00Z");
    private static final Instant UNBOUNDED_TO = Instant.parse("9999-12-31T00:00:00Z");

    /** {@code sort} is a key from this allow-list, never a raw JPA property path - see {@code Paging}. */
    private static final Map<String, String> SORTS = Map.of("expectedArrival", "expectedArrival");
    private static final String DEFAULT_SORT_KEY = "expectedArrival";

    private final VisitorVisitJpaRepository visits;

    VisitorVisitSearchPageAdapter(VisitorVisitJpaRepository visits) {
        this.visits = visits;
    }

    @Override
    public EmergencyPage<VisitorVisit> searchPage(String siteCode, VisitStatus status, String hostId, Instant from,
            Instant to, Paging paging) {
        Sort.Order order = order(paging.sort());
        PageRequest pageable = PageRequest.of(paging.page(), paging.size(), Sort.by(order).and(Sort.by("id")));
        Page<VisitorVisitJpaEntity> page = visits.searchPage(siteCode, status, hostId, from(from), to(to), pageable);
        return EmergencyPage.of(page.map(VisitorVisitJpaEntity::toDomain).getContent(), paging.page(), paging.size(),
                page.getTotalElements(), order.getProperty() + ": " + order.getDirection());
    }

    private static Instant from(Instant value) {
        return value == null ? UNBOUNDED_FROM : value;
    }

    private static Instant to(Instant value) {
        return value == null ? UNBOUNDED_TO : value;
    }

    private static Sort.Order order(String requested) {
        String key = DEFAULT_SORT_KEY;
        boolean descending = true;
        if (requested != null && !requested.isBlank()) {
            String[] parts = requested.split(",");
            String candidate = parts[0].trim();
            if (SORTS.containsKey(candidate)) {
                key = candidate;
                descending = parts.length <= 1 || parts[1].trim().equalsIgnoreCase("desc");
            }
        }
        String property = SORTS.get(key);
        return descending ? Sort.Order.desc(property) : Sort.Order.asc(property);
    }
}
