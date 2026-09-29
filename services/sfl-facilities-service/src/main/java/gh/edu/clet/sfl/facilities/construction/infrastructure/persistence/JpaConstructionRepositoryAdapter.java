package gh.edu.clet.sfl.facilities.construction.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.construction.application.ports.ConstructionRepository;
import gh.edu.clet.sfl.facilities.construction.domain.ConstructionProject;
import gh.edu.clet.sfl.facilities.construction.domain.Contractor;
import gh.edu.clet.sfl.facilities.construction.domain.ContractorCompetency;
import gh.edu.clet.sfl.facilities.construction.domain.DefectItem;
import gh.edu.clet.sfl.facilities.construction.domain.Handover;
import gh.edu.clet.sfl.facilities.construction.domain.Milestone;
import gh.edu.clet.sfl.facilities.construction.domain.PermitRecord;
import gh.edu.clet.sfl.facilities.construction.domain.ProjectApproval;
import gh.edu.clet.sfl.facilities.construction.domain.ProjectContractor;
import gh.edu.clet.sfl.facilities.construction.domain.ProjectPermitLink;
import gh.edu.clet.sfl.facilities.construction.domain.ProjectRevision;
import gh.edu.clet.sfl.facilities.construction.domain.ProjectStatus;
import gh.edu.clet.sfl.facilities.construction.domain.RegisterChange;
import gh.edu.clet.sfl.facilities.construction.domain.SiteAccessGrant;
import gh.edu.clet.sfl.facilities.construction.domain.VariationOrder;
import gh.edu.clet.sfl.facilities.shared.application.port.RepositoryPage;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

/**
 * The one adapter behind {@link ConstructionRepository}.
 *
 * <p>Every {@code save*} method fetches the managed row it already has, calls
 * {@code requireNotStale} on it and applies the domain object's fields onto that same instance -
 * {@code JpaBookingRepositoryAdapter}'s pattern, for the reason {@code VersionedRecord}'s Javadoc
 * gives: handing a detached instance whose version the domain layer already incremented straight to
 * {@code save} fails every write, not only a genuinely stale one.
 *
 * <p>{@link #appendRevision} is the one exception: {@code ProjectRevisionRecord} is not versioned
 * (V21's trigger refuses an update to the table outright), so it is a plain insert, and a
 * {@link DataIntegrityViolationException} from the append-only trigger is translated to a clear
 * refusal rather than a raw constraint message.
 */
@Repository
public class JpaConstructionRepositoryAdapter implements ConstructionRepository {

    private final JpaConstructionProjectJpaRepository projects;
    private final JpaProjectApprovalJpaRepository approvals;
    private final JpaMilestoneJpaRepository milestones;
    private final JpaProjectRevisionJpaRepository revisions;
    private final JpaContractorJpaRepository contractors;
    private final JpaContractorCompetencyJpaRepository competencies;
    private final JpaProjectContractorJpaRepository assignments;
    private final JpaSiteAccessGrantJpaRepository grants;
    private final JpaPermitProjectionJpaRepository permits;
    private final JpaProjectPermitLinkJpaRepository links;
    private final JpaVariationOrderJpaRepository variations;
    private final JpaHandoverJpaRepository handovers;
    private final JpaRegisterChangeJpaRepository registerChanges;
    private final JpaDefectItemJpaRepository defects;

    public JpaConstructionRepositoryAdapter(JpaConstructionProjectJpaRepository projects,
            JpaProjectApprovalJpaRepository approvals, JpaMilestoneJpaRepository milestones,
            JpaProjectRevisionJpaRepository revisions, JpaContractorJpaRepository contractors,
            JpaContractorCompetencyJpaRepository competencies, JpaProjectContractorJpaRepository assignments,
            JpaSiteAccessGrantJpaRepository grants, JpaPermitProjectionJpaRepository permits,
            JpaProjectPermitLinkJpaRepository links, JpaVariationOrderJpaRepository variations,
            JpaHandoverJpaRepository handovers, JpaRegisterChangeJpaRepository registerChanges,
            JpaDefectItemJpaRepository defects) {
        this.projects = projects;
        this.approvals = approvals;
        this.milestones = milestones;
        this.revisions = revisions;
        this.contractors = contractors;
        this.competencies = competencies;
        this.assignments = assignments;
        this.grants = grants;
        this.permits = permits;
        this.links = links;
        this.variations = variations;
        this.handovers = handovers;
        this.registerChanges = registerChanges;
        this.defects = defects;
    }

    // ---- projects ------------------------------------------------------------------------------

    @Override
    public ConstructionProject saveProject(ConstructionProject project) {
        ConstructionProjectRecord record = projects.findById(project.id())
                .map(existing -> {
                    existing.requireNotStale(project.metadata().version());
                    return existing;
                })
                .orElseGet(ConstructionProjectRecord::empty);
        record.apply(project);
        return projects.save(record).toDomain();
    }

    @Override
    public Optional<ConstructionProject> findProject(UUID id) {
        return projects.findById(id).map(ConstructionProjectRecord::toDomain);
    }

    @Override
    public Optional<ConstructionProject> findProjectBySpaceChange(UUID spaceChangeRequestId) {
        return projects.findBySpaceChangeRequestId(spaceChangeRequestId).map(ConstructionProjectRecord::toDomain);
    }

    @Override
    public RepositoryPage<ConstructionProject> searchProjects(ProjectQuery query) {
        Pageable pageable = PageRequest.of(Math.max(0, query.page()), Math.max(1, query.size()));
        Page<ConstructionProjectRecord> page = projects.search(query.siteCode(), query.status(), pageable);
        return RepositoryPage.of(page.getContent().stream().map(ConstructionProjectRecord::toDomain).toList(),
                page.getTotalElements(), query.page(), query.size());
    }

    @Override
    public List<ConstructionProject> findProjectsForSite(String siteCode) {
        return projects.findForSite(siteCode).stream().map(ConstructionProjectRecord::toDomain).toList();
    }

    @Override
    public List<ConstructionProject> findProjectsInStatus(ProjectStatus status, int limit) {
        return projects.findByStatus(status).stream().limit(limit).map(ConstructionProjectRecord::toDomain).toList();
    }

    @Override
    public String nextProjectReference(String siteCode) {
        return "CP-" + normalize(siteCode) + "-" + String.format("%05d", projects.nextProjectSequence());
    }

    @Override
    public ProjectApproval saveApproval(ProjectApproval approval) {
        ProjectApprovalRecord record = approvals.findById(approval.id())
                .map(existing -> {
                    existing.requireNotStale(approval.metadata().version());
                    return existing;
                })
                .orElseGet(ProjectApprovalRecord::empty);
        record.apply(approval);
        return approvals.save(record).toDomain();
    }

    @Override
    public List<ProjectApproval> findApprovals(UUID projectId) {
        return approvals.findByProjectIdOrderByApprovedAtAsc(projectId).stream()
                .map(ProjectApprovalRecord::toDomain).toList();
    }

    // ---- milestones and history ------------------------------------------------------------------

    @Override
    public Milestone saveMilestone(Milestone milestone) {
        MilestoneRecord record = milestones.findById(milestone.id())
                .map(existing -> {
                    existing.requireNotStale(milestone.metadata().version());
                    return existing;
                })
                .orElseGet(MilestoneRecord::empty);
        record.apply(milestone);
        return milestones.save(record).toDomain();
    }

    @Override
    public Optional<Milestone> findMilestone(UUID id) {
        return milestones.findById(id).map(MilestoneRecord::toDomain);
    }

    @Override
    public List<Milestone> findMilestones(UUID projectId) {
        return milestones.findByProjectIdOrderByTargetDateAsc(projectId).stream().map(MilestoneRecord::toDomain)
                .toList();
    }

    @Override
    public ProjectRevision appendRevision(ProjectRevision revision) {
        try {
            return revisions.save(ProjectRevisionRecord.of(revision)).toDomain();
        } catch (DataIntegrityViolationException failure) {
            throw new FacilitiesException.ValidationFailedException(
                    "That revision could not be recorded: " + rootMessage(failure));
        }
    }

    @Override
    public List<ProjectRevision> findRevisions(UUID projectId) {
        return revisions.findByProjectIdOrderByRevisedAtAsc(projectId).stream().map(ProjectRevisionRecord::toDomain)
                .toList();
    }

    // ---- contractors ---------------------------------------------------------------------------

    @Override
    public Contractor saveContractor(Contractor contractor) {
        ContractorRecord record = contractors.findById(contractor.id())
                .map(existing -> {
                    existing.requireNotStale(contractor.metadata().version());
                    return existing;
                })
                .orElseGet(ContractorRecord::empty);
        record.apply(contractor);
        return contractors.save(record).toDomain();
    }

    @Override
    public Optional<Contractor> findContractor(UUID id) {
        return contractors.findById(id).map(ContractorRecord::toDomain);
    }

    @Override
    public Optional<Contractor> findContractorByCode(String siteCode, String contractorCode) {
        return contractors.findBySiteCodeAndContractorCode(normalize(siteCode), normalize(contractorCode))
                .map(ContractorRecord::toDomain);
    }

    @Override
    public List<Contractor> findContractors(String siteCode) {
        return contractors.findForSite(siteCode).stream().map(ContractorRecord::toDomain).toList();
    }

    @Override
    public ContractorCompetency saveCompetency(ContractorCompetency competency) {
        ContractorCompetencyRecord record = competencies.findById(competency.id())
                .map(existing -> {
                    existing.requireNotStale(competency.metadata().version());
                    return existing;
                })
                .orElseGet(ContractorCompetencyRecord::empty);
        record.apply(competency);
        return competencies.save(record).toDomain();
    }

    @Override
    public List<ContractorCompetency> findCompetencies(UUID contractorId) {
        return competencies.findByContractorId(contractorId).stream().map(ContractorCompetencyRecord::toDomain)
                .toList();
    }

    @Override
    public ProjectContractor saveAssignment(ProjectContractor assignment) {
        ProjectContractorRecord record = assignments.findById(assignment.id()).orElseGet(ProjectContractorRecord::empty);
        record.apply(assignment);
        return assignments.save(record).toDomain();
    }

    @Override
    public List<ProjectContractor> findAssignments(UUID projectId) {
        return assignments.findByProjectId(projectId).stream().map(ProjectContractorRecord::toDomain).toList();
    }

    @Override
    public SiteAccessGrant saveGrant(SiteAccessGrant grant) {
        SiteAccessGrantRecord record = grants.findById(grant.id())
                .map(existing -> {
                    existing.requireNotStale(grant.metadata().version());
                    return existing;
                })
                .orElseGet(SiteAccessGrantRecord::empty);
        record.apply(grant);
        return grants.save(record).toDomain();
    }

    @Override
    public Optional<SiteAccessGrant> findGrant(UUID id) {
        return grants.findById(id).map(SiteAccessGrantRecord::toDomain);
    }

    @Override
    public List<SiteAccessGrant> findGrantsForContractor(UUID contractorId) {
        return grants.findByContractorId(contractorId).stream().map(SiteAccessGrantRecord::toDomain).toList();
    }

    @Override
    public List<SiteAccessGrant> findActiveGrants(int limit) {
        return grants.findByStatusOrderByRequestedAtAsc(SiteAccessGrant.Status.ACTIVE, PageRequest.of(0, limit))
                .stream().map(SiteAccessGrantRecord::toDomain).toList();
    }

    // ---- permits -------------------------------------------------------------------------------

    @Override
    public PermitRecord savePermit(PermitRecord permit) {
        PermitProjectionRecord record = permits.findById(permit.id())
                .map(existing -> {
                    existing.requireNotStale(permit.metadata().version());
                    return existing;
                })
                .orElseGet(PermitProjectionRecord::empty);
        record.apply(permit);
        return permits.save(record).toDomain();
    }

    @Override
    public Optional<PermitRecord> findPermit(String permitId) {
        return permits.findByPermitId(permitId).map(PermitProjectionRecord::toDomain);
    }

    @Override
    public List<PermitRecord> findPermits(Collection<String> permitIds) {
        return permits.findByPermitIdIn(permitIds).stream().map(PermitProjectionRecord::toDomain).toList();
    }

    @Override
    public List<PermitRecord> findPermitsForContractor(Collection<String> contractorReferences) {
        return permits.findByContractorReferenceIn(contractorReferences).stream()
                .map(PermitProjectionRecord::toDomain).toList();
    }

    @Override
    public ProjectPermitLink saveLink(ProjectPermitLink link) {
        ProjectPermitLinkRecord record = links.findById(link.id()).orElseGet(ProjectPermitLinkRecord::empty);
        record.apply(link);
        return links.save(record).toDomain();
    }

    @Override
    public List<ProjectPermitLink> findLinks(UUID projectId) {
        return links.findByProjectId(projectId).stream().map(ProjectPermitLinkRecord::toDomain).toList();
    }

    // ---- variations ----------------------------------------------------------------------------

    @Override
    public VariationOrder saveVariation(VariationOrder variation) {
        VariationOrderRecord record = variations.findById(variation.id())
                .map(existing -> {
                    existing.requireNotStale(variation.metadata().version());
                    return existing;
                })
                .orElseGet(VariationOrderRecord::empty);
        record.apply(variation);
        return variations.save(record).toDomain();
    }

    @Override
    public Optional<VariationOrder> findVariation(UUID id) {
        return variations.findById(id).map(VariationOrderRecord::toDomain);
    }

    @Override
    public List<VariationOrder> findVariations(UUID projectId) {
        return variations.findByProjectId(projectId).stream().map(VariationOrderRecord::toDomain).toList();
    }

    @Override
    public String nextVariationReference(String siteCode) {
        return "VO-" + normalize(siteCode) + "-" + String.format("%05d", variations.nextVariationSequence());
    }

    // ---- handover and defects ------------------------------------------------------------------

    @Override
    public Handover saveHandover(Handover handover) {
        HandoverRecord record = handovers.findById(handover.id())
                .map(existing -> {
                    existing.requireNotStale(handover.metadata().version());
                    return existing;
                })
                .orElseGet(HandoverRecord::empty);
        record.apply(handover);
        return handovers.save(record).toDomain();
    }

    @Override
    public List<Handover> findHandovers(UUID projectId) {
        return handovers.findByProjectIdOrderByRecordedAtAsc(projectId).stream().map(HandoverRecord::toDomain)
                .toList();
    }

    @Override
    public RegisterChange saveRegisterChange(RegisterChange change) {
        RegisterChangeRecord record = registerChanges.findById(change.id()).orElseGet(RegisterChangeRecord::empty);
        record.apply(change);
        return registerChanges.save(record).toDomain();
    }

    @Override
    public List<RegisterChange> findRegisterChanges(UUID projectId) {
        return registerChanges.findByProjectId(projectId).stream().map(RegisterChangeRecord::toDomain).toList();
    }

    @Override
    public DefectItem saveDefect(DefectItem defect) {
        DefectItemRecord record = defects.findById(defect.id())
                .map(existing -> {
                    existing.requireNotStale(defect.metadata().version());
                    return existing;
                })
                .orElseGet(DefectItemRecord::empty);
        record.apply(defect);
        return defects.save(record).toDomain();
    }

    @Override
    public Optional<DefectItem> findDefect(UUID id) {
        return defects.findById(id).map(DefectItemRecord::toDomain);
    }

    @Override
    public List<DefectItem> findDefects(UUID projectId) {
        return defects.findByProjectId(projectId).stream().map(DefectItemRecord::toDomain).toList();
    }

    @Override
    public List<DefectItem> findOpenDefects(int limit) {
        return defects.findByStatusOrderByRaisedAtAsc(DefectItem.Status.OPEN, PageRequest.of(0, limit)).stream()
                .map(DefectItemRecord::toDomain).toList();
    }

    @Override
    public String nextDefectReference(String siteCode) {
        return "DL-" + normalize(siteCode) + "-" + String.format("%05d", defects.nextDefectSequence());
    }

    private static String normalize(String value) {
        return value == null ? null : value.strip().toUpperCase(Locale.ROOT);
    }

    private static String rootMessage(DataAccessException failure) {
        Throwable cause = failure.getMostSpecificCause();
        return cause.getMessage() == null ? failure.getMessage() : cause.getMessage();
    }
}
