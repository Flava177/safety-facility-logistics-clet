package gh.edu.clet.sfl.facilities.readiness.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.facilities.masterdata.domain.FacilityRoom;
import gh.edu.clet.sfl.facilities.readiness.application.ports.ReadinessRepository;
import gh.edu.clet.sfl.facilities.readiness.domain.BlockerSource;
import gh.edu.clet.sfl.facilities.readiness.domain.ReadinessAssessment;
import gh.edu.clet.sfl.facilities.readiness.domain.ReadinessAssessmentItem;
import gh.edu.clet.sfl.facilities.readiness.domain.ReadinessBlocker;
import gh.edu.clet.sfl.facilities.readiness.domain.ReadinessChecklist;
import gh.edu.clet.sfl.facilities.readiness.domain.ReadinessOutcome;
import gh.edu.clet.sfl.facilities.readiness.domain.ReadinessPolicy;
import gh.edu.clet.sfl.facilities.shared.application.FacilitiesAuthorization;
import gh.edu.clet.sfl.facilities.shared.application.port.AuditPort;
import gh.edu.clet.sfl.facilities.shared.application.port.IdempotencyPort;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditAction;
import gh.edu.clet.sfl.facilities.shared.domain.model.OperatingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link ReadinessApplicationService#submitAssessment} and the checklist-resolution/answer-snapshot
 * logic it alone needs, split out for the same reason the rest of that class's Javadoc gives. Holds a
 * reference to {@link ReadinessApplicationService} to reuse {@code resolveChecklist}, {@code
 * answerItems}, {@code applyOutcome}, {@code requireRoom}, {@code now} and {@code publish} - see that
 * class's Javadoc on why the reference is held rather than the dependency list duplicated.
 */
final class ReadinessAssessmentCommands {

    private final ReadinessApplicationService service;
    private final ReadinessRepository readiness;
    private final FacilitiesAuthorization authorization;
    private final AuditPort audit;
    private final IdempotencyPort idempotency;

    ReadinessAssessmentCommands(ReadinessApplicationService service, ReadinessRepository readiness,
            FacilitiesAuthorization authorization, AuditPort audit, IdempotencyPort idempotency) {
        this.service = service;
        this.readiness = readiness;
        this.authorization = authorization;
        this.audit = audit;
        this.idempotency = idempotency;
    }

    /**
     * Records an inspection and re-derives the space's readiness.
     *
     * <p>The whole sequence happens in one transaction, because a space whose assessment committed and
     * whose blockers did not would report itself ready on a checklist it failed:
     * <ol>
     *   <li>resolve the checklist (explicit, or by space type and operating mode),</li>
     *   <li>snapshot each answer against the item as it is worded today,</li>
     *   <li>resolve the blockers the previous assessment raised - they are being reassessed,</li>
     *   <li>raise a blocker for each failed item, at the item's declared severity,</li>
     *   <li>evaluate every open blocker, including ones from assets and manual raises,</li>
     *   <li>write the derived status back to the space.</li>
     * </ol>
     */
    ReadinessAssessment submit(ReadinessCommands.SubmitAssessment command) {
        ActorContext actor = command.actor();
        FacilityRoom room = service.requireRoom(command.roomId());
        authorization.require(actor, SflPermission.FACILITIES_READINESS_ASSESS, room.siteCode(),
                command.channel(), "ReadinessAssessment", room.id().toString());

        if (command.idempotencyKey() != null && !command.idempotencyKey().isBlank()) {
            Optional<ReadinessAssessment> replayed = idempotency
                    .findExistingResult("submit-readiness-assessment", command.idempotencyKey(),
                            idempotency.fingerprint(command.idempotencyPayload()))
                    .flatMap(readiness::findAssessment);
            if (replayed.isPresent()) {
                return replayed.get();
            }
        }

        OperatingMode mode = service.operatingModeOf(room.siteCode());
        ReadinessChecklist checklist = service.resolveChecklist(command.checklistId(), room, mode);

        UUID assessmentId = UUID.randomUUID();
        List<ReadinessAssessmentItem> items = service.answerItems(assessmentId, checklist, command.answers());
        int score = ReadinessPolicy.score(items);

        // Close the previous assessment's checklist blockers first: this assessment supersedes it, and
        // leaving them open would double-count a fault that has just been re-inspected.
        Instant at = service.now();
        readiness.findLatestAssessment(room.id()).ifPresent(previous ->
                readiness.findOpenBlockers(room.id()).stream()
                        .filter(blocker -> blocker.source() == BlockerSource.CHECKLIST_ITEM)
                        .filter(blocker -> previous.id().equals(blocker.assessmentId()))
                        .forEach(blocker -> readiness.saveBlocker(blocker.resolve(
                                "Superseded by assessment " + assessmentId, actor.actorId(), at))));

        // Build the new blockers in memory before persisting anything. They carry a foreign key to the
        // assessment, so the assessment row has to exist first - but the assessment's own outcome is
        // derived from these very blockers. Constructing them, evaluating, saving the assessment and
        // only then saving the blockers is what satisfies both.
        List<ReadinessBlocker> raised = new ArrayList<>();
        for (ReadinessAssessmentItem item : items) {
            if (!item.passed()) {
                raised.add(ReadinessBlocker.raise(room.id(), room.siteCode(), assessmentId,
                        BlockerSource.CHECKLIST_ITEM, item.itemCode(), item.severityIfFailed(),
                        item.description(), actor.actorId(), at));
            }
        }

        // Evaluated against the blockers that will be open once this assessment lands: the ones already
        // open from other sources, plus the ones it is about to raise. `everAssessed` is true because
        // this *is* an assessment - asking the store would report a first-ever inspection as UNKNOWN,
        // so one that passed every item would come back as never inspected.
        List<ReadinessBlocker> openAfter = new ArrayList<>(readiness.findOpenBlockers(room.id()));
        openAfter.addAll(raised);
        ReadinessOutcome outcome = ReadinessPolicy.evaluate(openAfter, score, true);

        ReadinessAssessment assessment = readiness.saveAssessment(new ReadinessAssessment(assessmentId,
                room.id(), room.siteCode(), checklist == null ? null : checklist.id(),
                checklist == null ? null : checklist.checklistCode(),
                checklist == null ? 0 : checklist.version(), mode, outcome.status(), score, items,
                command.notes(), actor.actorId(), at));

        raised.forEach(blocker -> {
            readiness.saveBlocker(blocker);
            audit.record(actor, command.channel(), AuditAction.READINESS_BLOCKER_RAISED, "ReadinessBlocker",
                    blocker.id().toString(), room.siteCode(), null, blocker);
        });

        service.applyOutcome(room, outcome, actor, command.channel(), at);

        audit.record(actor, command.channel(), AuditAction.READINESS_ASSESSMENT_SUBMITTED, "ReadinessAssessment",
                assessment.id().toString(), room.siteCode(), null, assessment);
        service.publish("sfl.ifimp.readiness-assessment-submitted.v1", "ReadinessAssessment", assessment.id(),
                room.siteCode(), actor, assessment);
        raised.forEach(blocker -> service.publish("sfl.ifimp.readiness-blocker-created.v1", "ReadinessBlocker",
                blocker.id(), room.siteCode(), actor, blocker));

        idempotency.recordResult("submit-readiness-assessment", command.idempotencyKey(),
                idempotency.fingerprint(command.idempotencyPayload()), assessment.id(), room.siteCode(),
                actor.actorId());
        return assessment;
    }
}
