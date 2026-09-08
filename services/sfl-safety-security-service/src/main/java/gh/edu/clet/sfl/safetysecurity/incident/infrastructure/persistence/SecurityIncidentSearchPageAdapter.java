package gh.edu.clet.sfl.safetysecurity.incident.infrastructure.persistence;

import gh.edu.clet.sfl.safetysecurity.emergency.application.port.EmergencyRepository.EmergencyPage;
import gh.edu.clet.sfl.safetysecurity.emergency.application.port.EmergencyRepository.Paging;
import gh.edu.clet.sfl.safetysecurity.incident.application.port.SecurityIncidentSearchPageRepository;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.IncidentStatus;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.SecurityIncident;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.Severity;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

/**
 * Backs {@link SecurityIncidentSearchPageRepository} with {@link SecurityIncidentJpaRepository#searchPage},
 * a genuine {@code Page<T>}/{@code Pageable} query rather than the {@code List<T>} + client-{@code
 * limit} shape {@code SecurityIncidentRepositoryAdapter#search} still serves. A separate class, not a
 * new method on that adapter - see the port's Javadoc for why.
 */
@Repository
class SecurityIncidentSearchPageAdapter implements SecurityIncidentSearchPageRepository {

    /** {@code sort} is a key from this allow-list, never a raw JPA property path - see {@code Paging}. */
    private static final Map<String, String> SORTS = Map.of("createdAt", "createdAt");
    private static final String DEFAULT_SORT_KEY = "createdAt";

    private final SecurityIncidentJpaRepository incidents;

    SecurityIncidentSearchPageAdapter(SecurityIncidentJpaRepository incidents) {
        this.incidents = incidents;
    }

    @Override
    public EmergencyPage<SecurityIncident> searchPage(String siteCode, IncidentStatus status, Severity severity,
            Paging paging) {
        Sort.Order order = order(paging.sort());
        PageRequest pageable = PageRequest.of(paging.page(), paging.size(), Sort.by(order).and(Sort.by("id")));
        Page<SecurityIncidentJpaEntity> page = incidents.searchPage(siteCode, status, severity, pageable);
        return EmergencyPage.of(page.map(SecurityIncidentJpaEntity::toDomain).getContent(), paging.page(),
                paging.size(), page.getTotalElements(), order.getProperty() + ": " + order.getDirection());
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
