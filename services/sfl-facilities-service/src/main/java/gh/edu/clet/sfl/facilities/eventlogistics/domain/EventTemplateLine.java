package gh.edu.clet.sfl.facilities.eventlogistics.domain;

import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import java.time.Instant;
import java.util.UUID;

/**
 * A lesson carried forward to future events of the same category - SRS-SFL-S173-04 ("feeding lessons
 * back into future event templates"; workflow: "template updated where a persistent gap is found").
 *
 * <p>One line per site, event category and resource type. A reconciliation gap creates or strengthens
 * it; once {@link #gapCount} reaches the configured persistence threshold
 * ({@code event-logistics.template.gap-threshold}, default 2) the line pre-populates every later
 * decomposition of that category, carrying its lesson, so the next graduation starts with the second
 * PA system the last two graduations were short of. Below the threshold a gap is recorded but does not
 * yet change what the coordinator is offered - one bad afternoon is not a pattern.
 *
 * @param bookableResourceId the S159 resource last found short, for the bookable types that need one
 */
public record EventTemplateLine(
        UUID id,
        String siteCode,
        String eventCategory,
        EventResourceType resourceType,
        String description,
        int quantity,
        UUID bookableResourceId,
        String lesson,
        int gapCount,
        Instant lastGapAt,
        UUID lastGapTaskId,
        RecordMetadata metadata) {

    public static EventTemplateLine firstGap(UUID id, String siteCode, String eventCategory,
            EventResourceRequest gap, String lesson, UUID taskId, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        return new EventTemplateLine(id, siteCode, eventCategory, gap.resourceType(), gap.description(),
                gap.quantity(), gap.bookableResourceId(), lessonOf(lesson, gap), 1, at, taskId,
                RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    /** Another gap of the same kind. The larger quantity wins: a template should not shrink after a shortfall. */
    public EventTemplateLine anotherGap(EventResourceRequest gap, String lesson, UUID taskId, String actorId,
            Instant at, SourceChannel channel, String correlationId) {
        return new EventTemplateLine(id, siteCode, eventCategory, resourceType, gap.description(),
                Math.max(quantity, gap.quantity()),
                gap.bookableResourceId() != null ? gap.bookableResourceId() : bookableResourceId,
                lessonOf(lesson, gap), gapCount + 1, at, taskId, metadata.modifiedBy(actorId, at, channel,
                        correlationId));
    }

    public boolean isPersistent(int threshold) {
        return gapCount >= Math.max(1, threshold);
    }

    private static String lessonOf(String lesson, EventResourceRequest gap) {
        return lesson == null || lesson.isBlank()
                ? gap.resourceType() + " was short at a previous event of this category."
                : lesson.strip();
    }
}
