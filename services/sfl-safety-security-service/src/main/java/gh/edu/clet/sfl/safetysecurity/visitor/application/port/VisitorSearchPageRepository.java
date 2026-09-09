package gh.edu.clet.sfl.safetysecurity.visitor.application.port;

import gh.edu.clet.sfl.safetysecurity.emergency.application.port.EmergencyRepository.EmergencyPage;
import gh.edu.clet.sfl.safetysecurity.emergency.application.port.EmergencyRepository.Paging;
import gh.edu.clet.sfl.safetysecurity.visitor.domain.model.VisitStatus;
import gh.edu.clet.sfl.safetysecurity.visitor.domain.model.VisitorVisit;
import java.time.Instant;

/**
 * The paginated counterpart to {@link VisitorRepository#search}, which returns a bare {@code List<T>}
 * capped at a client-supplied {@code limit} (server-clamped to 500) - no offset or cursor, no total
 * count, so a client cannot tell whether it holds the whole result set or just the first page of it.
 *
 * <p>A separate port rather than a new method on {@link VisitorRepository} itself: that interface's
 * one implementation, {@code VisitorRepositoryAdapter}, is the already-shipped, regression-tested home
 * of the S160 optimistic-locking fix and is deliberately left alone here, so a second capability needs
 * a second port with its own adapter rather than a new abstract method that adapter would have to
 * grow.
 *
 * <p>Reuses S174's {@link EmergencyPage}/{@link Paging} rather than inventing a third pagination
 * shape - see {@code EmergencyPageResponse} for why every SFL collection endpoint pages the same way.
 */
public interface VisitorSearchPageRepository {

    EmergencyPage<VisitorVisit> searchPage(String siteCode, VisitStatus status, String hostId, Instant from,
            Instant to, Paging paging);
}
