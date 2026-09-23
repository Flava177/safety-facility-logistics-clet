package gh.edu.clet.sfl.safetysecurity.lifesafety.application.port;

import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.ComplianceExceptionStatus;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.DetectorCoverage;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.FastLaneTrigger;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.InspectionSchedule;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.LifeSafetyComplianceException;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.LifeSafetyEvent;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.MusterCheckIn;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.MusterSession;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for every S162a aggregate. One consolidated port rather than one per aggregate
 * (contrast S163's split {@code SecurityIncidentRepository}/{@code SecurityIncidentSearchPageRepository}):
 * S162a's aggregates are simpler event/exception/schedule records with no comparable search-paging need.
 */
public interface LifeSafetyRepository {

    /** Distinct site codes with at least one schedule/coverage record - backs the sweep scheduler's fan-out. */
    List<String> activeSites();

    LifeSafetyEvent saveEvent(LifeSafetyEvent event);

    Optional<LifeSafetyEvent> findEvent(UUID id);

    List<LifeSafetyEvent> findEvents(String siteCode, int limit);

    /** Backs the S174 {@code LifeSafetyEventPort} seam - the latest observed event at a site, if any. */
    Optional<LifeSafetyEvent> findLatestEvent(String siteCode);

    FastLaneTrigger saveFastLaneTrigger(FastLaneTrigger trigger);

    List<FastLaneTrigger> findFastLaneTriggers(String siteCode, int limit);

    InspectionSchedule saveInspectionSchedule(InspectionSchedule schedule);

    Optional<InspectionSchedule> findInspectionSchedule(UUID id);

    List<InspectionSchedule> findInspectionSchedules(String siteCode);

    List<InspectionSchedule> findOverdueInspectionSchedules(String siteCode, Instant asOf);

    LifeSafetyComplianceException saveComplianceException(LifeSafetyComplianceException exception);

    Optional<LifeSafetyComplianceException> findComplianceException(UUID id);

    List<LifeSafetyComplianceException> findComplianceExceptions(String siteCode, ComplianceExceptionStatus status);

    boolean hasOpenComplianceException(String siteCode, UUID refId, gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.ComplianceExceptionKind kind);

    DetectorCoverage saveDetectorCoverage(DetectorCoverage coverage);

    Optional<DetectorCoverage> findDetectorCoverage(UUID id);

    List<DetectorCoverage> findDetectorCoverages(String siteCode);

    MusterSession saveMusterSession(MusterSession session);

    Optional<MusterSession> findMusterSession(UUID id);

    Optional<MusterSession> findOpenMusterSession(String siteCode, String zoneCode);

    MusterCheckIn saveCheckIn(MusterCheckIn checkIn);

    List<MusterCheckIn> findCheckIns(UUID musterSessionId);
}
