package gh.edu.clet.sfl.facilities.eventlogistics.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * The record that an unresolved resource request was escalated to the coordinator - SRS-SFL-S173-04.
 *
 * <p>This row is both halves of the requirement at once: the evidence that the escalation happened
 * (which S173-04's completion rule checks for) and the notification intent - who was to be told, and
 * that nothing has confirmed delivery to them. There is no notification provider for IFIMP, so
 * {@code notificationStatus} is {@code RECORDED} and never claims more.
 *
 * @param notifiedTo the task's coordinator, or {@code ROLE:EVENT_LOGISTICS_COORDINATOR} when nobody
 *        has taken the task on yet
 * @param beforeEventStart whether the escalation fell before the event began - the acceptance
 *        criterion's "before the event, not after". False only if the sweep was down through the whole
 *        window, which is itself worth seeing
 */
public record ReadinessEscalation(
        UUID id,
        String siteCode,
        UUID setupTaskId,
        UUID resourceRequestId,
        ResourceRequestStatus requestStatus,
        Instant eventStartsAt,
        Instant escalatedAt,
        long windowMinutes,
        String notifiedTo,
        String notificationStatus,
        boolean beforeEventStart,
        String recordedBy,
        String correlationId) {

    public static final String NOTIFICATION_RECORDED = "RECORDED";
    public static final String COORDINATOR_DESK = "ROLE:EVENT_LOGISTICS_COORDINATOR";
}
