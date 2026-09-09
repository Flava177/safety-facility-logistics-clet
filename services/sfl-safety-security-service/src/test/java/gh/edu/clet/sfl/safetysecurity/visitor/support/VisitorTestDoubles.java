package gh.edu.clet.sfl.safetysecurity.visitor.support;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.common.security.SiteScopedPrincipal;
import gh.edu.clet.sfl.safetysecurity.visitor.application.port.VisitorRepository;
import gh.edu.clet.sfl.safetysecurity.visitor.domain.model.VisitStatus;
import gh.edu.clet.sfl.safetysecurity.visitor.domain.model.VisitorApproval;
import gh.edu.clet.sfl.safetysecurity.visitor.domain.model.VisitorVisit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * In-memory {@link VisitorRepository} for S160 application-layer unit tests, following the idiom
 * {@code sfl-fleet-logistics-service}'s {@code fleet.support.FleetTestDoubles} uses: a real
 * implementation of the port's contract, not a mock, so a test that passes here is testing behaviour
 * (the self-approval refusal, the version conflict) rather than interaction bookkeeping.
 */
public final class VisitorTestDoubles {

    private VisitorTestDoubles() {
    }

    public static ActorContext actor(String subject, SflRole role, String... sites) {
        return new ActorContext(new SiteScopedPrincipal(subject, subject, Set.of(role), Set.of(sites), false),
                "corr-test");
    }

    public static final class InMemoryVisitorRepository implements VisitorRepository {

        private final Map<UUID, VisitorVisit> visits = new LinkedHashMap<>();
        private final Map<UUID, VisitorApproval> approvals = new LinkedHashMap<>();

        @Override
        public VisitorVisit saveVisit(VisitorVisit visit) {
            visits.put(visit.id(), visit);
            return visit;
        }

        @Override
        public Optional<VisitorVisit> findVisit(UUID id) {
            return Optional.ofNullable(visits.get(id));
        }

        @Override
        public List<VisitorVisit> search(VisitQuery query) {
            return visits.values().stream()
                    .filter(v -> query.siteCode() == null || v.siteCode().equalsIgnoreCase(query.siteCode()))
                    .filter(v -> query.status() == null || v.status() == query.status())
                    .filter(v -> query.hostId() == null || v.hostId().equals(query.hostId()))
                    .limit(Math.max(1, query.limit()))
                    .toList();
        }

        @Override
        public List<VisitorVisit> findOnSite(String siteCode) {
            return visits.values().stream()
                    .filter(v -> v.siteCode().equalsIgnoreCase(siteCode))
                    .filter(v -> v.status() == VisitStatus.CHECKED_IN)
                    .toList();
        }

        @Override
        public VisitorApproval saveApproval(VisitorApproval approval) {
            approvals.put(approval.id(), approval);
            return approval;
        }

        @Override
        public List<VisitorApproval> findApprovals(UUID visitId) {
            return approvals.values().stream().filter(a -> a.visitId().equals(visitId)).toList();
        }
    }
}
