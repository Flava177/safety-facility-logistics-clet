package gh.edu.clet.sfl.safetysecurity.visitor.application.port;

import gh.edu.clet.sfl.safetysecurity.visitor.domain.model.VisitStatus;
import gh.edu.clet.sfl.safetysecurity.visitor.domain.model.VisitorApproval;
import gh.edu.clet.sfl.safetysecurity.visitor.domain.model.VisitorVisit;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VisitorRepository {

    VisitorVisit saveVisit(VisitorVisit visit);

    Optional<VisitorVisit> findVisit(UUID id);

    List<VisitorVisit> search(VisitQuery query);

    /** Visits currently checked in for a site - the roll-call view. */
    List<VisitorVisit> findOnSite(String siteCode);

    VisitorApproval saveApproval(VisitorApproval approval);

    List<VisitorApproval> findApprovals(UUID visitId);

    record VisitQuery(String siteCode, VisitStatus status, String hostId, Instant from, Instant to, int limit) {
    }
}
