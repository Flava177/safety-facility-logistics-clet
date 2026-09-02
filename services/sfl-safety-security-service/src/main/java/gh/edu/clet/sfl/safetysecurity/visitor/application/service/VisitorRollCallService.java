package gh.edu.clet.sfl.safetysecurity.visitor.application.service;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.safetysecurity.visitor.application.port.VisitorRepository;
import gh.edu.clet.sfl.safetysecurity.visitor.domain.model.VisitorVisit;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SRS-SFL-S160-01: "visitor roll-call views" - who is currently on site for a given CLET site, for
 * emergency muster and routine reception oversight.
 */
@Service
public class VisitorRollCallService {

    private final VisitorRepository repository;
    private final VisitorAccessPolicy access;

    public VisitorRollCallService(VisitorRepository repository, VisitorAccessPolicy access) {
        this.repository = repository;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public List<VisitorVisit> rollCall(String siteCode, ActorContext actor) {
        access.require(actor, SflPermission.VISITOR_ROLLCALL_READ, siteCode, "VisitorVisit", null);
        return repository.findOnSite(siteCode);
    }
}
