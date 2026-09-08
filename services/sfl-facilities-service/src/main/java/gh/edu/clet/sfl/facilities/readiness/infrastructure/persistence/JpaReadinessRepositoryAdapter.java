package gh.edu.clet.sfl.facilities.readiness.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.masterdata.domain.SpaceType;
import gh.edu.clet.sfl.facilities.readiness.application.ports.ReadinessRepository;
import gh.edu.clet.sfl.facilities.readiness.domain.BlockerSeverity;
import gh.edu.clet.sfl.facilities.readiness.domain.BlockerSource;
import gh.edu.clet.sfl.facilities.readiness.domain.ReadinessAssessment;
import gh.edu.clet.sfl.facilities.readiness.domain.ReadinessBlocker;
import gh.edu.clet.sfl.facilities.readiness.domain.ReadinessChecklist;
import gh.edu.clet.sfl.facilities.shared.application.port.RepositoryPage;
import gh.edu.clet.sfl.facilities.shared.domain.model.OperatingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
class JpaReadinessRepositoryAdapter implements ReadinessRepository {

    private static final int MAX_LIMIT = 500;

    private final ReadinessChecklistJpaRepository checklists;
    private final ReadinessAssessmentJpaRepository assessments;
    private final ReadinessBlockerJpaRepository blockers;

    JpaReadinessRepositoryAdapter(ReadinessChecklistJpaRepository checklists,
            ReadinessAssessmentJpaRepository assessments, ReadinessBlockerJpaRepository blockers) {
        this.checklists = checklists;
        this.assessments = assessments;
        this.blockers = blockers;
    }

    // ---- checklists ---------------------------------------------------------------------------

    @Override
    public ReadinessChecklist saveChecklist(ReadinessChecklist checklist) {
        // Load-then-apply rather than construct-then-merge: the item collection is managed, and
        // replacing it wholesale on a detached entity leaves the old rows behind.
        ReadinessChecklistEntity entity = checklists.findById(checklist.id())
                .orElseGet(() -> ReadinessChecklistEntity.from(checklist));
        entity.apply(checklist);
        return checklists.save(entity).toDomain();
    }

    @Override
    public Optional<ReadinessChecklist> findChecklist(UUID id) {
        return checklists.findById(id).map(ReadinessChecklistEntity::toDomain);
    }

    @Override
    public Optional<ReadinessChecklist> findChecklistByCode(String siteCode, String checklistCode) {
        return checklists.findBySiteCodeAndChecklistCode(normalize(siteCode), normalize(checklistCode))
                .map(ReadinessChecklistEntity::toDomain);
    }

    @Override
    public List<ReadinessChecklist> findChecklists(String siteCode) {
        return (blank(siteCode)
                ? checklists.findAllByOrderBySiteCodeAscChecklistCodeAsc()
                : checklists.findBySiteCodeOrderByChecklistCodeAsc(normalize(siteCode)))
                .stream().map(ReadinessChecklistEntity::toDomain).toList();
    }

    /**
     * Most specific wins.
     *
     * <p>A checklist naming both the space type and the operating mode scores 2, one naming a single
     * dimension scores 1, and a catch-all scores 0. Ties break on the checklist code so the choice is
     * deterministic - an assessor must get the same list twice for the same space.
     */
    @Override
    public Optional<ReadinessChecklist> findApplicableChecklist(String siteCode, SpaceType spaceType,
            OperatingMode operatingMode) {
        return checklists.findCandidates(normalize(siteCode), spaceType, operatingMode).stream()
                .map(ReadinessChecklistEntity::toDomain)
                .max(Comparator
                        .comparingInt(JpaReadinessRepositoryAdapter::specificity)
                        .thenComparing(Comparator.comparing(ReadinessChecklist::checklistCode).reversed()));
    }

    private static int specificity(ReadinessChecklist checklist) {
        return (checklist.spaceType() == null ? 0 : 1) + (checklist.operatingMode() == null ? 0 : 1);
    }

    // ---- assessments --------------------------------------------------------------------------

    @Override
    public ReadinessAssessment saveAssessment(ReadinessAssessment assessment) {
        return assessments.save(ReadinessAssessmentEntity.from(assessment, null)).toDomain();
    }

    @Override
    public Optional<ReadinessAssessment> findAssessment(UUID id) {
        return assessments.findById(id).map(ReadinessAssessmentEntity::toDomain);
    }

    @Override
    public List<ReadinessAssessment> findAssessmentsForRoom(UUID roomId, int limit) {
        List<UUID> ids = assessments.findIdsByRoomIdOrderByAssessedAtDesc(roomId, PageRequest.of(0, clamp(limit)));
        return loadAssessmentsInOrder(ids);
    }

    @Override
    public RepositoryPage<ReadinessAssessment> findAssessments(String siteCode, UUID roomId, int page, int size) {
        Page<UUID> ids = assessments.searchIds(blank(siteCode) ? null : normalize(siteCode), roomId,
                PageRequest.of(page, clampSize(size)));
        return RepositoryPage.of(loadAssessmentsInOrder(ids.getContent()), ids.getTotalElements(),
                ids.getNumber(), ids.getSize());
    }

    @Override
    public Optional<ReadinessAssessment> findLatestAssessment(UUID roomId) {
        List<UUID> ids = assessments.findIdsByRoomIdOrderByAssessedAtDesc(roomId, PageRequest.of(0, 1));
        return loadAssessmentsInOrder(ids).stream().findFirst();
    }

    /**
     * Pages the ids first, then loads the page with items joined in - a JOIN FETCH straight on the
     * paged query would make Hibernate page the collection join in memory instead of in SQL.
     * {@code findByIdIn} does not preserve order, so the id order chosen by the page query is
     * re-applied here.
     */
    private List<ReadinessAssessment> loadAssessmentsInOrder(List<UUID> orderedIds) {
        if (orderedIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, ReadinessAssessment> byId = assessments.findByIdIn(orderedIds).stream()
                .map(ReadinessAssessmentEntity::toDomain)
                .collect(Collectors.toMap(ReadinessAssessment::id, Function.identity()));
        return orderedIds.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    // ---- blockers -----------------------------------------------------------------------------

    @Override
    public ReadinessBlocker saveBlocker(ReadinessBlocker blocker) {
        return blockers.save(ReadinessBlockerEntity.from(blocker)).toDomain();
    }

    @Override
    public Optional<ReadinessBlocker> findBlocker(UUID id) {
        return blockers.findById(id).map(ReadinessBlockerEntity::toDomain);
    }

    @Override
    public List<ReadinessBlocker> findOpenBlockers(UUID roomId) {
        return blockers.findByRoomIdAndResolvedFalseOrderBySeverityAscRaisedAtAsc(roomId).stream()
                .map(ReadinessBlockerEntity::toDomain).toList();
    }

    @Override
    public RepositoryPage<ReadinessBlocker> findBlockers(String siteCode, UUID roomId, BlockerSeverity severity,
            Boolean open, int page, int size) {
        // `open` is the API's word; the column stores its inverse.
        Boolean resolved = open == null ? null : !open;
        Page<ReadinessBlockerEntity> result = blockers.search(blank(siteCode) ? null : normalize(siteCode),
                roomId, severity, resolved, PageRequest.of(page, clampSize(size)));
        return RepositoryPage.of(result.getContent().stream().map(ReadinessBlockerEntity::toDomain).toList(),
                result.getTotalElements(), result.getNumber(), result.getSize());
    }

    @Override
    public List<ReadinessBlocker> findOpenBlockersBySource(BlockerSource source, String sourceReference) {
        if (sourceReference == null || sourceReference.isBlank()) {
            return List.of();
        }
        return blockers.findBySourceAndSourceReferenceAndResolvedFalse(source, sourceReference).stream()
                .map(ReadinessBlockerEntity::toDomain).toList();
    }

    @Override
    public List<ReadinessBlocker> findOpenBlockersForSite(String siteCode) {
        return (blank(siteCode)
                ? blockers.findByResolvedFalseOrderBySeverityAscRaisedAtAsc()
                : blockers.findBySiteCodeAndResolvedFalseOrderBySeverityAscRaisedAtAsc(normalize(siteCode)))
                .stream().map(ReadinessBlockerEntity::toDomain).toList();
    }

    private static int clamp(int limit) {
        return limit <= 0 ? 50 : Math.min(limit, MAX_LIMIT);
    }

    private static int clampSize(int size) {
        return Math.max(1, Math.min(size, MAX_LIMIT));
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String normalize(String value) {
        return value == null ? null : value.strip().toUpperCase(Locale.ROOT);
    }
}
