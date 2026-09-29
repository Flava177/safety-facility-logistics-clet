package gh.edu.clet.sfl.facilities.cleaning.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.shared.application.port.AuditPort;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditAction;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Audits an S169 refusal in a transaction of its own.
 *
 * <p>Two S169 refusals must leave evidence: a task claiming a booking S159 cannot resolve
 * ({@code CLEANING_BOOKING_TASK_REJECTED}, SRS-SFL-S169-01) and a completion refused for an incomplete
 * checklist ({@code CLEANING_TASK_COMPLETION_REFUSED}, SRS-SFL-S169-02). Both end in an exception, and
 * an exception rolls back the transaction it is thrown in - so an audit written there would vanish with
 * the refusal it records. {@code REQUIRES_NEW} commits the record first, for the reason
 * {@code VendorRejectionRecorder} gives. A separate bean, because Spring's proxy does not intercept a
 * bean calling itself.
 *
 * <h2>One ordering rule callers must keep</h2>
 *
 * A refusal is recorded before the calling transaction has written any audit record of its own. The
 * audit chain serialises writers; a new transaction waiting on a chain position its own caller already
 * holds would wait for ever. Both S169 refusals are, and must stay, the first write of their command -
 * the same position {@code FacilitiesAuthorization} relies on for audited denials.
 */
@Service
public class CleaningRefusalRecorder {

    private final AuditPort audit;

    public CleaningRefusalRecorder(AuditPort audit) {
        this.audit = audit;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(ActorContext actor, SourceChannel channel, AuditAction action, String resourceType,
            String resourceId, String siteCode, Map<String, Object> facts) {
        audit.record(actor, channel, action, resourceType, resourceId, siteCode, null, facts);
    }
}
