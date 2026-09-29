package gh.edu.clet.sfl.facilities.construction.application.ports;

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
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Everything S176 persists, behind one port.
 *
 * <p>One port rather than one per aggregate because the aggregates are never used apart: a start
 * reads the project, its approval, its links and the permit projection together, and a handover
 * writes a project, a handover and its register changes in one transaction. Splitting them would
 * give each test five doubles to wire and each service five constructor arguments with no boundary
 * behind the split.
 *
 * <p>Every list query that takes a {@code siteCode} treats {@code null} as "every site"; the service
 * filters the result through {@code FacilitiesAuthorization.filterBySite} as well, so a caller's site
 * scope is enforced in SQL and again in Java (and by RLS beneath both).
 */
public interface ConstructionRepository {

    // ---- projects ------------------------------------------------------------------------------

    ConstructionProject saveProject(ConstructionProject project);

    Optional<ConstructionProject> findProject(UUID id);

    Optional<ConstructionProject> findProjectBySpaceChange(UUID spaceChangeRequestId);

    record ProjectQuery(String siteCode, ProjectStatus status, int page, int size) {
    }

    RepositoryPage<ConstructionProject> searchProjects(ProjectQuery query);

    List<ConstructionProject> findProjectsForSite(String siteCode);

    List<ConstructionProject> findProjectsInStatus(ProjectStatus status, int limit);

    String nextProjectReference(String siteCode);

    ProjectApproval saveApproval(ProjectApproval approval);

    List<ProjectApproval> findApprovals(UUID projectId);

    // ---- milestones and history ----------------------------------------------------------------

    Milestone saveMilestone(Milestone milestone);

    Optional<Milestone> findMilestone(UUID id);

    List<Milestone> findMilestones(UUID projectId);

    /** Insert only. There is deliberately no update: see V21's append-only trigger. */
    ProjectRevision appendRevision(ProjectRevision revision);

    List<ProjectRevision> findRevisions(UUID projectId);

    // ---- contractors ---------------------------------------------------------------------------

    Contractor saveContractor(Contractor contractor);

    Optional<Contractor> findContractor(UUID id);

    Optional<Contractor> findContractorByCode(String siteCode, String contractorCode);

    List<Contractor> findContractors(String siteCode);

    ContractorCompetency saveCompetency(ContractorCompetency competency);

    List<ContractorCompetency> findCompetencies(UUID contractorId);

    ProjectContractor saveAssignment(ProjectContractor assignment);

    List<ProjectContractor> findAssignments(UUID projectId);

    SiteAccessGrant saveGrant(SiteAccessGrant grant);

    Optional<SiteAccessGrant> findGrant(UUID id);

    List<SiteAccessGrant> findGrantsForContractor(UUID contractorId);

    /** Every ACTIVE grant, oldest first, up to {@code limit} - the compliance sweep's work list. */
    List<SiteAccessGrant> findActiveGrants(int limit);

    // ---- permits -------------------------------------------------------------------------------

    PermitRecord savePermit(PermitRecord permit);

    Optional<PermitRecord> findPermit(String permitId);

    List<PermitRecord> findPermits(Collection<String> permitIds);

    List<PermitRecord> findPermitsForContractor(Collection<String> contractorReferences);

    ProjectPermitLink saveLink(ProjectPermitLink link);

    List<ProjectPermitLink> findLinks(UUID projectId);

    // ---- variations ----------------------------------------------------------------------------

    VariationOrder saveVariation(VariationOrder variation);

    Optional<VariationOrder> findVariation(UUID id);

    List<VariationOrder> findVariations(UUID projectId);

    String nextVariationReference(String siteCode);

    // ---- handover and defects ------------------------------------------------------------------

    Handover saveHandover(Handover handover);

    List<Handover> findHandovers(UUID projectId);

    RegisterChange saveRegisterChange(RegisterChange change);

    List<RegisterChange> findRegisterChanges(UUID projectId);

    DefectItem saveDefect(DefectItem defect);

    Optional<DefectItem> findDefect(UUID id);

    List<DefectItem> findDefects(UUID projectId);

    /** Every OPEN defect with a work order, up to {@code limit} - the defect-sync sweep's work list. */
    List<DefectItem> findOpenDefects(int limit);

    String nextDefectReference(String siteCode);
}
