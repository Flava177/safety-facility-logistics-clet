package gh.edu.clet.sfl.facilities.support;

import gh.edu.clet.sfl.facilities.masterdata.domain.SpaceType;
import gh.edu.clet.sfl.facilities.readiness.application.ports.ReadinessRepository;
import gh.edu.clet.sfl.facilities.readiness.domain.BlockerSeverity;
import gh.edu.clet.sfl.facilities.readiness.domain.BlockerSource;
import gh.edu.clet.sfl.facilities.readiness.domain.ReadinessAssessment;
import gh.edu.clet.sfl.facilities.readiness.domain.ReadinessBlocker;
import gh.edu.clet.sfl.facilities.readiness.domain.ReadinessChecklist;
import gh.edu.clet.sfl.facilities.shared.domain.model.OperatingMode;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** In-memory readiness store; see {@link InMemoryFacilitiesRepository} for why. */
public class InMemoryReadinessRepository implements ReadinessRepository {

    private final Map<UUID, ReadinessChecklist> checklists = new LinkedHashMap<>();
    private final Map<UUID, ReadinessAssessment> assessments = new LinkedHashMap<>();
    private final Map<UUID, ReadinessBlocker> blockers = new LinkedHashMap<>();

    @Override
    public ReadinessChecklist saveChecklist(ReadinessChecklist checklist) {
        checklists.put(checklist.id(), checklist);
        return checklist;
    }

    @Override
    public Optional<ReadinessChecklist> findChecklist(UUID id) {
        return Optional.ofNullable(checklists.get(id));
    }

    @Override
    public Optional<ReadinessChecklist> findChecklistByCode(String siteCode, String checklistCode) {
        return checklists.values().stream()
                .filter(checklist -> checklist.siteCode().equals(upper(siteCode))
                        && checklist.checklistCode().equals(upper(checklistCode)))
                .findFirst();
    }

    @Override
    public List<ReadinessChecklist> findChecklists(String siteCode) {
        return checklists.values().stream()
                .filter(checklist -> siteCode == null || checklist.siteCode().equals(upper(siteCode)))
                .toList();
    }

    /** Mirrors the JPA adapter's rule: most specific wins, ties broken deterministically by code. */
    @Override
    public Optional<ReadinessChecklist> findApplicableChecklist(String siteCode, SpaceType spaceType,
            OperatingMode operatingMode) {
        return checklists.values().stream()
                .filter(checklist -> checklist.siteCode().equals(upper(siteCode)))
                .filter(checklist -> checklist.appliesTo(spaceType, operatingMode))
                .max(Comparator
                        .comparingInt((ReadinessChecklist checklist) ->
                                (checklist.spaceType() == null ? 0 : 1)
                                        + (checklist.operatingMode() == null ? 0 : 1))
                        .thenComparing(Comparator.comparing(ReadinessChecklist::checklistCode).reversed()));
    }

    @Override
    public ReadinessAssessment saveAssessment(ReadinessAssessment assessment) {
        assessments.put(assessment.id(), assessment);
        return assessment;
    }

    @Override
    public Optional<ReadinessAssessment> findAssessment(UUID id) {
        return Optional.ofNullable(assessments.get(id));
    }

    @Override
    public List<ReadinessAssessment> findAssessmentsForRoom(UUID roomId, int limit) {
        return assessments.values().stream()
                .filter(assessment -> assessment.roomId().equals(roomId))
                .sorted(Comparator.comparing(ReadinessAssessment::assessedAt).reversed())
                .limit(Math.max(1, limit))
                .toList();
    }

    @Override
    public List<ReadinessAssessment> findAssessments(String siteCode, UUID roomId, int limit) {
        return assessments.values().stream()
                .filter(assessment -> siteCode == null || assessment.siteCode().equals(upper(siteCode)))
                .filter(assessment -> roomId == null || assessment.roomId().equals(roomId))
                .sorted(Comparator.comparing(ReadinessAssessment::assessedAt).reversed())
                .limit(Math.max(1, limit))
                .toList();
    }

    @Override
    public Optional<ReadinessAssessment> findLatestAssessment(UUID roomId) {
        return assessments.values().stream()
                .filter(assessment -> assessment.roomId().equals(roomId))
                .max(Comparator.comparing(ReadinessAssessment::assessedAt));
    }

    @Override
    public ReadinessBlocker saveBlocker(ReadinessBlocker blocker) {
        blockers.put(blocker.id(), blocker);
        return blocker;
    }

    @Override
    public Optional<ReadinessBlocker> findBlocker(UUID id) {
        return Optional.ofNullable(blockers.get(id));
    }

    @Override
    public List<ReadinessBlocker> findOpenBlockers(UUID roomId) {
        return blockers.values().stream()
                .filter(blocker -> blocker.roomId().equals(roomId))
                .filter(ReadinessBlocker::isOpen)
                .sorted(Comparator.comparing(ReadinessBlocker::severity)
                        .thenComparing(ReadinessBlocker::raisedAt))
                .toList();
    }

    @Override
    public List<ReadinessBlocker> findBlockers(String siteCode, UUID roomId, BlockerSeverity severity,
            Boolean open, int limit) {
        return blockers.values().stream()
                .filter(blocker -> siteCode == null || blocker.siteCode().equals(upper(siteCode)))
                .filter(blocker -> roomId == null || blocker.roomId().equals(roomId))
                .filter(blocker -> severity == null || blocker.severity() == severity)
                .filter(blocker -> open == null || blocker.isOpen() == open)
                .limit(Math.max(1, limit))
                .toList();
    }

    @Override
    public List<ReadinessBlocker> findOpenBlockersBySource(BlockerSource source, String sourceReference) {
        if (sourceReference == null) {
            return List.of();
        }
        return blockers.values().stream()
                .filter(blocker -> blocker.source() == source)
                .filter(blocker -> sourceReference.equals(blocker.sourceReference()))
                .filter(ReadinessBlocker::isOpen)
                .toList();
    }

    @Override
    public List<ReadinessBlocker> findOpenBlockersForSite(String siteCode) {
        return blockers.values().stream()
                .filter(blocker -> siteCode == null || blocker.siteCode().equals(upper(siteCode)))
                .filter(ReadinessBlocker::isOpen)
                .toList();
    }

    private static String upper(String value) {
        return value == null ? null : value.strip().toUpperCase(Locale.ROOT);
    }
}
