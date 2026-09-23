package gh.edu.clet.sfl.safetysecurity.intrusion.application.service;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.safetysecurity.intrusion.application.port.ArmedResponseCoordinationPort;
import gh.edu.clet.sfl.safetysecurity.intrusion.application.port.IntrusionRepository;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.exception.IntrusionException;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.DispatchOutcome;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.IntrusionAlarm;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.ResponseDispatch;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.SourceChannel;
import gh.edu.clet.sfl.safetysecurity.platform.application.port.AuditPort;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SRS-SFL-S162-05 (SHOULD): coordination with the external monitoring/armed-response contract for a
 * confirmed alarm - dispatch, acknowledgement, arrival and outcome, plus the false-alarm trend the
 * requirement names. Kept proportionate: one evolving record per alarm, not a contract-management
 * subsystem.
 */
@Service
public class IntrusionDispatchService {

    private final IntrusionRepository repository;
    private final ArmedResponseCoordinationPort armedResponse;
    private final AuditPort audit;
    private final IntrusionAccessPolicy access;
    private final Clock clock;

    public IntrusionDispatchService(IntrusionRepository repository, ArmedResponseCoordinationPort armedResponse,
            AuditPort audit, IntrusionAccessPolicy access, Clock clock) {
        this.repository = repository;
        this.armedResponse = armedResponse;
        this.audit = audit;
        this.access = access;
        this.clock = clock;
    }

    @Transactional
    public ResponseDispatch requestDispatch(UUID alarmId, String monitoringService, ActorContext actor) {
        IntrusionAlarm alarm = repository.findAlarm(alarmId)
                .orElseThrow(() -> IntrusionException.notFound("IntrusionAlarm", alarmId));
        access.require(actor, SflPermission.INTRUSION_DISPATCH_RECORD, alarm.siteCode(), "ResponseDispatch",
                alarmId.toString());
        Instant now = clock.instant();
        ResponseDispatch dispatch = ResponseDispatch.request(UUID.randomUUID(), alarm.siteCode(), alarmId,
                monitoringService, now, actor.actorId(), now, SourceChannel.WEB, actor.correlationId());
        ResponseDispatch saved = repository.saveDispatch(dispatch);
        armedResponse.requestDispatch(alarm, actor);
        audit.record(actor, SourceChannel.WEB.name(), saved.siteCode(), "INTRUSION_DISPATCH_REQUESTED",
                "ResponseDispatch", saved.id().toString(), null, saved, null);
        return saved;
    }

    @Transactional
    public ResponseDispatch acknowledge(UUID id, ActorContext actor) {
        ResponseDispatch dispatch = requireDispatch(id);
        access.require(actor, SflPermission.INTRUSION_DISPATCH_RECORD, dispatch.siteCode(), "ResponseDispatch",
                id.toString());
        Instant now = clock.instant();
        ResponseDispatch acknowledged = repository.saveDispatch(dispatch.acknowledge(now, actor.actorId(), now,
                SourceChannel.WEB, actor.correlationId()));
        audit.record(actor, SourceChannel.WEB.name(), acknowledged.siteCode(), "INTRUSION_DISPATCH_ACKNOWLEDGED",
                "ResponseDispatch", acknowledged.id().toString(), dispatch, acknowledged, null);
        return acknowledged;
    }

    @Transactional
    public ResponseDispatch recordOutcome(UUID id, DispatchOutcome outcome, String notes, ActorContext actor) {
        ResponseDispatch dispatch = requireDispatch(id);
        access.require(actor, SflPermission.INTRUSION_DISPATCH_RECORD, dispatch.siteCode(), "ResponseDispatch",
                id.toString());
        Instant now = clock.instant();
        ResponseDispatch recorded = repository.saveDispatch(dispatch.recordOutcome(now, outcome, notes,
                actor.actorId(), now, SourceChannel.WEB, actor.correlationId()));
        audit.record(actor, SourceChannel.WEB.name(), recorded.siteCode(), "INTRUSION_DISPATCH_OUTCOME_RECORDED",
                "ResponseDispatch", recorded.id().toString(), dispatch, recorded, null);
        return recorded;
    }

    @Transactional(readOnly = true)
    public ResponseDispatch forAlarm(UUID alarmId, ActorContext actor) {
        ResponseDispatch dispatch = repository.findDispatchByAlarm(alarmId)
                .orElseThrow(() -> IntrusionException.notFound("ResponseDispatch", alarmId));
        access.require(actor, SflPermission.INTRUSION_DISPATCH_READ, dispatch.siteCode(), "ResponseDispatch",
                alarmId.toString());
        return dispatch;
    }

    /** SRS-SFL-S162-05: "False-alarm outcomes are recorded and trended". */
    @Transactional(readOnly = true)
    public long falseAlarmCount(String siteCode, ActorContext actor) {
        access.require(actor, SflPermission.INTRUSION_REPORT_READ, siteCode, "ResponseDispatch", null);
        return repository.countByOutcome(siteCode, DispatchOutcome.FALSE_ALARM);
    }

    private ResponseDispatch requireDispatch(UUID id) {
        return repository.findDispatch(id).orElseThrow(() -> IntrusionException.notFound("ResponseDispatch", id));
    }
}
