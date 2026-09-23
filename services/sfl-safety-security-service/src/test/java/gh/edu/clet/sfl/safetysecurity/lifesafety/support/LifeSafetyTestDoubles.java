package gh.edu.clet.sfl.safetysecurity.lifesafety.support;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.common.security.SiteScopedPrincipal;
import gh.edu.clet.sfl.safetysecurity.lifesafety.application.port.EmergencyFastLanePort;
import gh.edu.clet.sfl.safetysecurity.lifesafety.application.port.LifeSafetyRepository;
import gh.edu.clet.sfl.safetysecurity.lifesafety.application.port.OnSitePopulationPort;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.ComplianceExceptionKind;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.ComplianceExceptionStatus;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.DetectorCoverage;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.FastLaneTrigger;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.InspectionSchedule;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.LifeSafetyComplianceException;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.LifeSafetyEvent;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.MusterCheckIn;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.MusterSession;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.MusterStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * In-memory {@link LifeSafetyRepository} and fake outbound ports for S162a application-layer unit
 * tests - real implementations of each port's contract, not mocks, following {@code
 * IncidentTestDoubles}' idiom.
 */
public final class LifeSafetyTestDoubles {

    private LifeSafetyTestDoubles() {
    }

    public static ActorContext actor(String subject, SflRole role, String... sites) {
        return new ActorContext(new SiteScopedPrincipal(subject, subject, Set.of(role), Set.of(sites), false),
                "corr-test");
    }

    public static final class InMemoryLifeSafetyRepository implements LifeSafetyRepository {

        private final Map<UUID, LifeSafetyEvent> events = new LinkedHashMap<>();
        private final Map<UUID, FastLaneTrigger> fastLaneTriggers = new LinkedHashMap<>();
        private final Map<UUID, InspectionSchedule> inspectionSchedules = new LinkedHashMap<>();
        private final Map<UUID, LifeSafetyComplianceException> complianceExceptions = new LinkedHashMap<>();
        private final Map<UUID, DetectorCoverage> detectorCoverage = new LinkedHashMap<>();
        private final Map<UUID, MusterSession> musterSessions = new LinkedHashMap<>();
        private final Map<UUID, MusterCheckIn> musterCheckIns = new LinkedHashMap<>();

        @Override
        public List<String> activeSites() {
            Set<String> sites = new java.util.LinkedHashSet<>();
            inspectionSchedules.values().forEach(s -> sites.add(s.siteCode()));
            detectorCoverage.values().forEach(c -> sites.add(c.siteCode()));
            return List.copyOf(sites);
        }

        @Override
        public LifeSafetyEvent saveEvent(LifeSafetyEvent event) {
            events.put(event.id(), event);
            return event;
        }

        @Override
        public Optional<LifeSafetyEvent> findEvent(UUID id) {
            return Optional.ofNullable(events.get(id));
        }

        @Override
        public List<LifeSafetyEvent> findEvents(String siteCode, int limit) {
            return events.values().stream().filter(e -> e.siteCode().equals(siteCode))
                    .sorted(Comparator.comparing(LifeSafetyEvent::occurredAt).reversed()).limit(limit).toList();
        }

        @Override
        public Optional<LifeSafetyEvent> findLatestEvent(String siteCode) {
            return events.values().stream().filter(e -> e.siteCode().equals(siteCode))
                    .max(Comparator.comparing(LifeSafetyEvent::occurredAt));
        }

        @Override
        public FastLaneTrigger saveFastLaneTrigger(FastLaneTrigger trigger) {
            fastLaneTriggers.put(trigger.id(), trigger);
            return trigger;
        }

        @Override
        public List<FastLaneTrigger> findFastLaneTriggers(String siteCode, int limit) {
            return fastLaneTriggers.values().stream().filter(t -> t.siteCode().equals(siteCode)).limit(limit)
                    .toList();
        }

        @Override
        public InspectionSchedule saveInspectionSchedule(InspectionSchedule schedule) {
            inspectionSchedules.put(schedule.id(), schedule);
            return schedule;
        }

        @Override
        public Optional<InspectionSchedule> findInspectionSchedule(UUID id) {
            return Optional.ofNullable(inspectionSchedules.get(id));
        }

        @Override
        public List<InspectionSchedule> findInspectionSchedules(String siteCode) {
            return inspectionSchedules.values().stream().filter(s -> s.siteCode().equals(siteCode)).toList();
        }

        @Override
        public List<InspectionSchedule> findOverdueInspectionSchedules(String siteCode, Instant asOf) {
            return inspectionSchedules.values().stream().filter(s -> s.siteCode().equals(siteCode))
                    .filter(s -> s.overdue(asOf)).toList();
        }

        @Override
        public LifeSafetyComplianceException saveComplianceException(LifeSafetyComplianceException exception) {
            complianceExceptions.put(exception.id(), exception);
            return exception;
        }

        @Override
        public Optional<LifeSafetyComplianceException> findComplianceException(UUID id) {
            return Optional.ofNullable(complianceExceptions.get(id));
        }

        @Override
        public List<LifeSafetyComplianceException> findComplianceExceptions(String siteCode,
                ComplianceExceptionStatus status) {
            return complianceExceptions.values().stream().filter(e -> e.siteCode().equals(siteCode))
                    .filter(e -> status == null || e.status() == status).toList();
        }

        @Override
        public boolean hasOpenComplianceException(String siteCode, UUID refId, ComplianceExceptionKind kind) {
            return complianceExceptions.values().stream().anyMatch(e -> e.siteCode().equals(siteCode)
                    && kind.equals(e.kind()) && refId != null && refId.equals(e.refId())
                    && e.status() == ComplianceExceptionStatus.OPEN);
        }

        @Override
        public DetectorCoverage saveDetectorCoverage(DetectorCoverage coverage) {
            detectorCoverage.put(coverage.id(), coverage);
            return coverage;
        }

        @Override
        public Optional<DetectorCoverage> findDetectorCoverage(UUID id) {
            return Optional.ofNullable(detectorCoverage.get(id));
        }

        @Override
        public List<DetectorCoverage> findDetectorCoverages(String siteCode) {
            return detectorCoverage.values().stream().filter(c -> c.siteCode().equals(siteCode)).toList();
        }

        @Override
        public MusterSession saveMusterSession(MusterSession session) {
            musterSessions.put(session.id(), session);
            return session;
        }

        @Override
        public Optional<MusterSession> findMusterSession(UUID id) {
            return Optional.ofNullable(musterSessions.get(id));
        }

        @Override
        public Optional<MusterSession> findOpenMusterSession(String siteCode, String zoneCode) {
            return musterSessions.values().stream().filter(s -> s.siteCode().equals(siteCode)
                    && s.zoneCode().equals(zoneCode) && s.status() == MusterStatus.OPEN).findFirst();
        }

        @Override
        public MusterCheckIn saveCheckIn(MusterCheckIn checkIn) {
            musterCheckIns.put(checkIn.id(), checkIn);
            return checkIn;
        }

        @Override
        public List<MusterCheckIn> findCheckIns(UUID musterSessionId) {
            return musterCheckIns.values().stream().filter(c -> c.musterSessionId().equals(musterSessionId))
                    .toList();
        }

        public List<LifeSafetyComplianceException> allExceptions() {
            return new ArrayList<>(complianceExceptions.values());
        }
    }

    /** Configurable fake: returns a fixed activation id, or empty to simulate a degraded trigger. */
    public static final class FakeEmergencyFastLanePort implements EmergencyFastLanePort {

        private UUID nextActivationId = UUID.randomUUID();
        private int calls;

        @Override
        public Optional<UUID> triggerFastLane(String siteCode, String zoneCode, String description,
                ActorContext actor) {
            calls++;
            return Optional.ofNullable(nextActivationId);
        }

        public void degradeNextCall() {
            nextActivationId = null;
        }

        public int calls() {
            return calls;
        }
    }

    public static final class FakeOnSitePopulationPort implements OnSitePopulationPort {

        private Set<String> population = Set.of();

        public void seed(String... personRefs) {
            population = Set.of(personRefs);
        }

        @Override
        public Set<String> onSitePersons(String siteCode, String zoneCode) {
            return population;
        }
    }
}
