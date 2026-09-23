package gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model;

import java.time.Instant;
import java.util.UUID;

/** SRS-SFL-S162a-05: a detector/panic-device/siren mapped to a zone, with its functional-test state. */
public record DetectorCoverage(UUID id, String siteCode, String zoneCode, String deviceRef, DetectorType deviceType,
        boolean covered, Instant lastTestAt, Instant nextTestDueAt, TestResult lastTestResult,
        RecordMetadata metadata) {

    public boolean testOverdue(Instant now) {
        return nextTestDueAt != null && nextTestDueAt.isBefore(now);
    }

    public boolean flagged(Instant now) {
        return !covered || testOverdue(now) || lastTestResult == TestResult.FAIL;
    }
}
