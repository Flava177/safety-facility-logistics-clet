package gh.edu.clet.sfl.facilities.readiness.application.ports;

import gh.edu.clet.sfl.facilities.masterdata.domain.SpaceType;
import gh.edu.clet.sfl.facilities.readiness.domain.BlockerSeverity;
import gh.edu.clet.sfl.facilities.readiness.domain.BlockerSource;
import gh.edu.clet.sfl.facilities.readiness.domain.ReadinessAssessment;
import gh.edu.clet.sfl.facilities.readiness.domain.ReadinessBlocker;
import gh.edu.clet.sfl.facilities.readiness.domain.ReadinessChecklist;
import gh.edu.clet.sfl.facilities.shared.domain.model.OperatingMode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Outbound persistence port for the readiness module. */
public interface ReadinessRepository {

    // ---- checklists ---------------------------------------------------------------------------

    ReadinessChecklist saveChecklist(ReadinessChecklist checklist);

    Optional<ReadinessChecklist> findChecklist(UUID id);

    Optional<ReadinessChecklist> findChecklistByCode(String siteCode, String checklistCode);

    List<ReadinessChecklist> findChecklists(String siteCode);

    /**
     * The checklist that applies to a space of this type in this mode.
     *
     * <p>Most specific wins: a checklist naming both the space type and the mode beats one naming
     * only the mode, which beats one naming neither. Without that ordering an examination hall would
     * be assessed against whichever generic list happened to be returned first.
     */
    Optional<ReadinessChecklist> findApplicableChecklist(String siteCode, SpaceType spaceType,
            OperatingMode operatingMode);

    // ---- assessments --------------------------------------------------------------------------

    ReadinessAssessment saveAssessment(ReadinessAssessment assessment);

    Optional<ReadinessAssessment> findAssessment(UUID id);

    List<ReadinessAssessment> findAssessmentsForRoom(UUID roomId, int limit);

    List<ReadinessAssessment> findAssessments(String siteCode, UUID roomId, int limit);

    /** The most recent assessment of a space, which is what its current readiness was derived from. */
    Optional<ReadinessAssessment> findLatestAssessment(UUID roomId);

    // ---- blockers -----------------------------------------------------------------------------

    ReadinessBlocker saveBlocker(ReadinessBlocker blocker);

    Optional<ReadinessBlocker> findBlocker(UUID id);

    /** Every unresolved blocker for a space. The input to {@code ReadinessPolicy.evaluate}. */
    List<ReadinessBlocker> findOpenBlockers(UUID roomId);

    List<ReadinessBlocker> findBlockers(String siteCode, UUID roomId, BlockerSeverity severity, Boolean open,
            int limit);

    /** Open blockers raised from one source reference — how an asset's recovery clears its own blockers. */
    List<ReadinessBlocker> findOpenBlockersBySource(BlockerSource source, String sourceReference);

    /** Open blockers across a site, for the dashboard's severity breakdown. */
    List<ReadinessBlocker> findOpenBlockersForSite(String siteCode);
}
