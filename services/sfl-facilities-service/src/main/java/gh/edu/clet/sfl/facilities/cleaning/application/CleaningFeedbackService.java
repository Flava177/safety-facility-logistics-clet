package gh.edu.clet.sfl.facilities.cleaning.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.facilities.cleaning.application.ports.CleaningRepository;
import gh.edu.clet.sfl.facilities.cleaning.domain.CleaningFeedback;
import gh.edu.clet.sfl.facilities.cleaning.domain.CleaningTask;
import gh.edu.clet.sfl.facilities.cleaning.domain.CleaningVendor;
import gh.edu.clet.sfl.facilities.cleaning.domain.FlagSubjectType;
import gh.edu.clet.sfl.facilities.cleaning.domain.LowRatingFlag;
import gh.edu.clet.sfl.facilities.cleaning.domain.TaskStatus;
import gh.edu.clet.sfl.facilities.cleaning.domain.policy.LowRatingPolicy;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditAction;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.model.EstateCodes;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Occupant feedback and the low-rating review queue - SRS-SFL-S169-02: "Occupants can submit feedback
 * against a completed task (rating plus optional comment); repeated low ratings for the same space or
 * vendor are surfaced for supervisor review."
 *
 * <h2>Who may rate</h2>
 *
 * Anyone holding {@code FACILITIES_CLEANING_FEEDBACK_SUBMIT} at the task's site, on a completed task,
 * once. Not the person who did the clean: a cleaner rating their own work five stars would make the
 * quality floor on the vendor scorecard (SRS-SFL-S169-03) self-reported, which is exactly what it must
 * not be. Feedback does not require being able to read the task - an occupant rates the room they are
 * standing in, typically from a code posted in it, without being shown the site's rota.
 */
@Service
public class CleaningFeedbackService {

    private final CleaningSupport support;
    private final CleaningRepository repository;
    private final SlaEvaluator sla;

    public CleaningFeedbackService(CleaningSupport support, SlaEvaluator sla) {
        this.support = support;
        this.repository = support.repository();
        this.sla = sla;
    }

    @Transactional
    public CleaningFeedback submit(CleaningCommands.SubmitFeedback command) {
        ActorContext actor = command.actor();
        CleaningTask task = support.requireTask(command.taskId());
        support.authorization().require(actor, SflPermission.FACILITIES_CLEANING_FEEDBACK_SUBMIT, task.siteCode(),
                command.channel(), "CleaningFeedback", task.id().toString());
        if (task.status() != TaskStatus.COMPLETED) {
            throw new FacilitiesException.InvalidStateTransitionException(
                    "Feedback can be given only on a completed clean; " + task.taskNumber() + " is " + task.status()
                            + ".");
        }
        if (task.isAssignedTo(actor.actorId()) || actor.actorId().equals(task.completedBy())) {
            support.audit().recordDenial(actor, command.channel(), "CleaningFeedback", task.id().toString(),
                    task.siteCode(), "The person who did the clean may not rate it");
            throw new FacilitiesException.UnauthorizedScopeException(
                    "Feedback on a clean is given by occupants, not by whoever carried it out.");
        }
        if (repository.existsFeedback(task.id(), actor.actorId())) {
            throw new FacilitiesException.ValidationFailedException(
                    "You have already given feedback on " + task.taskNumber() + ".");
        }
        Instant at = support.now();
        CleaningFeedback feedback = repository.saveFeedback(CleaningFeedback.submit(UUID.randomUUID(), task,
                command.rating(), command.comment(), actor.actorId(), at, command.channel(), actor.correlationId()));
        support.audit().record(actor, command.channel(), AuditAction.CLEANING_FEEDBACK_SUBMITTED, "CleaningFeedback",
                feedback.id().toString(), feedback.siteCode(), null, feedback);

        sla.evaluateRating(task, feedback.rating(), actor, command.channel());

        int lowMax = support.configuration().lowRatingMax(task.siteCode());
        if (LowRatingPolicy.isLow(feedback.rating(), lowMax)) {
            Instant since = at.minus(support.configuration().lowRatingWindow(task.siteCode()));
            int repeat = support.configuration().lowRatingRepeatCount(task.siteCode());
            long forRoom = repository.countLowRatingsForRoom(task.roomId(), lowMax, since);
            if (LowRatingPolicy.isRepeated(forRoom, repeat)) {
                flag(task.siteCode(), FlagSubjectType.SPACE, task.roomId(), task.roomCode(), forRoom, since, feedback,
                        actor, command.channel());
            }
            if (task.vendorId() != null) {
                long forVendor = repository.countLowRatingsForVendor(task.vendorId(), lowMax, since);
                if (LowRatingPolicy.isRepeated(forVendor, repeat)) {
                    String label = repository.findVendor(task.vendorId()).map(CleaningVendor::name)
                            .orElse(task.vendorId().toString());
                    flag(task.siteCode(), FlagSubjectType.VENDOR, task.vendorId(), label, forVendor, since, feedback,
                            actor, command.channel());
                }
            }
        }
        return feedback;
    }

    /**
     * Opens a flag, or brings the open one up to date. Only an opening publishes: the event means "a
     * supervisor now has something to look at", and a count going from four to five is not news.
     */
    private LowRatingFlag flag(String siteCode, FlagSubjectType type, UUID subjectId, String label, long count,
            Instant since, CleaningFeedback feedback, ActorContext actor, SourceChannel channel) {
        Optional<LowRatingFlag> open = repository.findOpenFlag(siteCode, type, subjectId);
        Instant at = support.now();
        if (open.isPresent()) {
            return repository.saveFlag(open.get().refresh((int) count, since, feedback.id(), actor.actorId(), at,
                    channel, actor.correlationId()));
        }
        LowRatingFlag raised = repository.saveFlag(LowRatingFlag.raise(UUID.randomUUID(), siteCode, type, subjectId,
                label, (int) count, since, feedback.id(), actor.actorId(), at, channel, actor.correlationId()));
        support.audit().record(actor, channel, AuditAction.CLEANING_LOW_RATING_FLAGGED, "CleaningLowRatingFlag",
                raised.id().toString(), raised.siteCode(), null, raised);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("flagId", raised.id().toString());
        payload.put("siteCode", raised.siteCode());
        payload.put("subjectType", raised.subjectType().name());
        payload.put("subjectId", raised.subjectId().toString());
        payload.put("lowRatingCount", raised.lowRatingCount());
        payload.put("windowStart", raised.windowStart().toString());
        payload.put("lastFeedbackId", raised.lastFeedbackId().toString());
        support.publish("sfl.ifimp.cleaning-low-rating-flagged.v1", "CleaningLowRatingFlag", raised.id(),
                raised.siteCode(), payload, actor);
        return raised;
    }

    /** The supervisor review queue. */
    @Transactional(readOnly = true)
    public List<LowRatingFlag> flags(String siteCode, Boolean open, ActorContext actor, SourceChannel channel) {
        String site = EstateCodes.normalize(siteCode);
        support.authorization().require(actor, SflPermission.FACILITIES_CLEANING_TASK_SUPERVISE, site, channel,
                "CleaningLowRatingFlag", "list");
        return support.authorization().filterBySite(actor, repository.findFlags(site, open), LowRatingFlag::siteCode);
    }

    @Transactional
    public LowRatingFlag review(CleaningCommands.ReviewFlag command) {
        ActorContext actor = command.actor();
        LowRatingFlag flag = repository.findFlag(command.flagId())
                .orElseThrow(() -> new FacilitiesException.RecordNotFoundException("Low-rating flag", command.flagId()));
        support.authorization().require(actor, SflPermission.FACILITIES_CLEANING_TASK_SUPERVISE, flag.siteCode(),
                command.channel(), "CleaningLowRatingFlag", flag.id().toString());
        LowRatingFlag reviewed = repository.saveFlag(flag.review(command.notes(), actor.actorId(), support.now(),
                command.channel(), actor.correlationId()));
        support.audit().record(actor, command.channel(), AuditAction.CLEANING_LOW_RATING_REVIEWED,
                "CleaningLowRatingFlag", reviewed.id().toString(), reviewed.siteCode(), flag, reviewed);
        return reviewed;
    }

    /** Feedback on one task, for whoever may read the task. */
    @Transactional(readOnly = true)
    public List<CleaningFeedback> forTask(UUID taskId, ActorContext actor, SourceChannel channel) {
        CleaningTask task = support.requireTask(taskId);
        support.requireUnnarrowedRead(actor, task.siteCode(), channel, "CleaningFeedback");
        support.authorization().requireSite(actor, task.siteCode(), channel, "CleaningFeedback", taskId.toString());
        return repository.findFeedbackForTask(taskId);
    }
}
