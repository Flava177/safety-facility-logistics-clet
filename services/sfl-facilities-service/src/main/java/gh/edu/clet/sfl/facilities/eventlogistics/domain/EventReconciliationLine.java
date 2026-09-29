package gh.edu.clet.sfl.facilities.eventlogistics.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * One line of post-event reconciliation: what was requested against what was delivered -
 * SRS-SFL-S173-04 ("a gap - AV that never arrived, security that was under-resourced - is caught and
 * recorded, not just discovered on the day").
 *
 * <p>Carries the request's status at the time as well as the outcome, because the interesting gaps are
 * the ones where the two disagree: a CONFIRMED request that was NOT_DELIVERED is an owning system that
 * said yes and did not turn up.
 */
public record EventReconciliationLine(
        UUID id,
        String siteCode,
        UUID setupTaskId,
        UUID resourceRequestId,
        EventResourceType resourceType,
        ResourceRequestStatus requestStatusAtReconciliation,
        int requestedQuantity,
        Integer deliveredQuantity,
        DeliveryOutcome outcome,
        String notes,
        String recordedBy,
        Instant recordedAt,
        String correlationId) {
}
