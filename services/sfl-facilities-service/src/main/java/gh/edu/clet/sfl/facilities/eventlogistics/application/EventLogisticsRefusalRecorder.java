package gh.edu.clet.sfl.facilities.eventlogistics.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.eventlogistics.application.ports.EventLogisticsRepository;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.EventHandoff;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.EventSetupTask;
import gh.edu.clet.sfl.facilities.shared.application.port.AuditPort;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditAction;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Makes S173's two refusals permanent before they are thrown - the reasoning of
 * {@code VendorRejectionRecorder}, applied to this module's own refusals.
 *
 * <p>A refused hand-off (S173-01 "Unresolvable Event Reference") and a refused confirmation (S173-03 "No
 * Current Risk Assessment") are both exceptions, and an exception rolls back the transaction it is
 * thrown in. Written there, the audit record of the refusal would be rolled back with it and the
 * refusal would leave no trace - the one thing an HSE review of "why was this event not confirmed"
 * needs. {@code REQUIRES_NEW} commits the record first.
 *
 * <p>A separate bean because Spring's transaction proxy does not intercept a call a bean makes to itself.
 */
@Service
public class EventLogisticsRefusalRecorder {

    private final EventLogisticsRepository repository;
    private final AuditPort audit;

    public EventLogisticsRefusalRecorder(EventLogisticsRepository repository, AuditPort audit) {
        this.repository = repository;
        this.audit = audit;
    }

    /** The rejected hand-off joins the register, so a replayed reference can be seen to have failed before. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public EventHandoff handoffRejected(EventHandoff rejected, ActorContext actor) {
        EventHandoff saved = repository.saveHandoff(rejected);
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("handoffId", saved.id().toString());
        facts.put("s078EventReference", saved.s078EventReference());
        facts.put("s078Status", saved.s078Status());
        facts.put("sourceSystem", saved.sourceSystem());
        facts.put("reason", saved.rejectionDetail());
        audit.record(actor, SourceChannel.INTEGRATION, AuditAction.EVENT_HANDOFF_REJECTED, "EventHandoff",
                saved.id().toString(), saved.siteCode(), null, facts);
        return saved;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void confirmationRefused(EventSetupTask task, String reason, String detail, ActorContext actor,
            SourceChannel channel) {
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("taskReference", task.taskReference());
        facts.put("s078EventReference", task.s078EventReference());
        facts.put("reason", reason);
        facts.put("riskAssessmentId", task.riskAssessmentId());
        facts.put("detail", detail);
        audit.record(actor, channel, AuditAction.EVENT_SETUP_CONFIRMATION_REFUSED, "EventSetupTask",
                task.id().toString(), task.siteCode(), null, facts);
    }
}
