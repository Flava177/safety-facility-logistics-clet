package gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * SRS-SFL-S162a-03: a periodic inspection/certification schedule for a life-safety system or device,
 * held by reference to S152 (siteCode/zoneCode by value, never a cross-service foreign key).
 * Certificates and inspection evidence are recorded by reference, never as a file blob here.
 */
public record InspectionSchedule(UUID id, String siteCode, String systemRef, String description,
        int frequencyDays, Instant lastPerformedAt, Instant nextDueAt, String evidenceReference,
        RecordMetadata metadata) {

    public boolean overdue(Instant now) {
        return nextDueAt != null && nextDueAt.isBefore(now);
    }

    public InspectionSchedule recordPerformed(Instant performedAt, String evidenceRef, RecordMetadata nextMeta) {
        return new InspectionSchedule(id, siteCode, systemRef, description, frequencyDays, performedAt,
                performedAt.plusSeconds(frequencyDays * 86400L), evidenceRef, nextMeta);
    }
}
