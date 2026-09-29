package gh.edu.clet.sfl.facilities.support;

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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * An in-memory {@link ConstructionRepository} for the S176 application tests.
 *
 * <p>Reproduces every query the module makes, including per-project and per-contractor filtering,
 * so a rule that reads the wrong rows fails here. Does not reproduce the RLS policies or the
 * append-only trigger on revisions - both are proved against a real PostgreSQL in
 * {@code ConstructionRowLevelSecurityTest} and {@code ConstructionMigrationIntegrationTest}.
 */
public class InMemoryConstructionRepository implements ConstructionRepository {

    private final Map<UUID, ConstructionProject> projects = new LinkedHashMap<>();
    private final Map<UUID, ProjectApproval> approvals = new LinkedHashMap<>();
    private final Map<UUID, Milestone> milestones = new LinkedHashMap<>();
    private final List<ProjectRevision> revisions = new ArrayList<>();
    private final Map<UUID, Contractor> contractors = new LinkedHashMap<>();
    private final Map<UUID, ContractorCompetency> competencies = new LinkedHashMap<>();
    private final Map<UUID, ProjectContractor> assignments = new LinkedHashMap<>();
    private final Map<UUID, SiteAccessGrant> grants = new LinkedHashMap<>();
    private final Map<String, PermitRecord> permits = new LinkedHashMap<>();
    private final Map<UUID, ProjectPermitLink> links = new LinkedHashMap<>();
    private final Map<UUID, VariationOrder> variations = new LinkedHashMap<>();
    private final Map<UUID, Handover> handovers = new LinkedHashMap<>();
    private final Map<UUID, RegisterChange> registerChanges = new LinkedHashMap<>();
    private final Map<UUID, DefectItem> defects = new LinkedHashMap<>();

    private final AtomicLong projectSequence = new AtomicLong();
    private final AtomicLong variationSequence = new AtomicLong();
    private final AtomicLong defectSequence = new AtomicLong();

    // ---- projects ------------------------------------------------------------------------------

    @Override
    public ConstructionProject saveProject(ConstructionProject project) {
        projects.put(project.id(), project);
        return project;
    }

    @Override
    public Optional<ConstructionProject> findProject(UUID id) {
        return Optional.ofNullable(projects.get(id));
    }

    @Override
    public Optional<ConstructionProject> findProjectBySpaceChange(UUID spaceChangeRequestId) {
        return projects.values().stream()
                .filter(p -> spaceChangeRequestId.equals(p.spaceChangeRequestId())).findFirst();
    }

    @Override
    public RepositoryPage<ConstructionProject> searchProjects(ProjectQuery query) {
        List<ConstructionProject> matching = projects.values().stream()
                .filter(p -> query.siteCode() == null || p.siteCode().equals(query.siteCode()))
                .filter(p -> query.status() == null || p.status() == query.status())
                .sorted(Comparator.comparing(ConstructionProject::registeredAt).reversed())
                .toList();
        int from = Math.min(query.page() * query.size(), matching.size());
        int to = Math.min(from + query.size(), matching.size());
        return RepositoryPage.of(matching.subList(from, to), matching.size(), query.page(), query.size());
    }

    @Override
    public List<ConstructionProject> findProjectsForSite(String siteCode) {
        return projects.values().stream().filter(p -> siteCode == null || p.siteCode().equals(siteCode)).toList();
    }

    @Override
    public List<ConstructionProject> findProjectsInStatus(ProjectStatus status, int limit) {
        return projects.values().stream().filter(p -> p.status() == status).limit(limit).toList();
    }

    @Override
    public String nextProjectReference(String siteCode) {
        return "CP-" + normalize(siteCode) + "-" + String.format("%05d", projectSequence.incrementAndGet());
    }

    @Override
    public ProjectApproval saveApproval(ProjectApproval approval) {
        approvals.put(approval.id(), approval);
        return approval;
    }

    @Override
    public List<ProjectApproval> findApprovals(UUID projectId) {
        return approvals.values().stream().filter(a -> a.projectId().equals(projectId))
                .sorted(Comparator.comparing(ProjectApproval::approvedAt)).toList();
    }

    // ---- milestones and history ------------------------------------------------------------------

    @Override
    public Milestone saveMilestone(Milestone milestone) {
        milestones.put(milestone.id(), milestone);
        return milestone;
    }

    @Override
    public Optional<Milestone> findMilestone(UUID id) {
        return Optional.ofNullable(milestones.get(id));
    }

    @Override
    public List<Milestone> findMilestones(UUID projectId) {
        return milestones.values().stream().filter(m -> m.projectId().equals(projectId))
                .sorted(Comparator.comparing(Milestone::targetDate)).toList();
    }

    @Override
    public ProjectRevision appendRevision(ProjectRevision revision) {
        boolean duplicate = revisions.stream().anyMatch(r -> r.projectId().equals(revision.projectId())
                && r.subject() == revision.subject() && r.subjectCode().equals(revision.subjectCode())
                && r.revision() == revision.revision());
        if (duplicate) {
            throw new IllegalStateException("Revision " + revision.revision() + " of " + revision.subjectCode()
                    + " already recorded - revisions are append-only.");
        }
        revisions.add(revision);
        return revision;
    }

    @Override
    public List<ProjectRevision> findRevisions(UUID projectId) {
        return revisions.stream().filter(r -> r.projectId().equals(projectId))
                .sorted(Comparator.comparing(ProjectRevision::revisedAt)).toList();
    }

    // ---- contractors ---------------------------------------------------------------------------

    @Override
    public Contractor saveContractor(Contractor contractor) {
        contractors.put(contractor.id(), contractor);
        return contractor;
    }

    @Override
    public Optional<Contractor> findContractor(UUID id) {
        return Optional.ofNullable(contractors.get(id));
    }

    @Override
    public Optional<Contractor> findContractorByCode(String siteCode, String contractorCode) {
        return contractors.values().stream()
                .filter(c -> c.siteCode().equals(siteCode) && c.contractorCode().equals(contractorCode)).findFirst();
    }

    @Override
    public List<Contractor> findContractors(String siteCode) {
        return contractors.values().stream().filter(c -> siteCode == null || c.siteCode().equals(siteCode)).toList();
    }

    @Override
    public ContractorCompetency saveCompetency(ContractorCompetency competency) {
        competencies.put(competency.id(), competency);
        return competency;
    }

    @Override
    public List<ContractorCompetency> findCompetencies(UUID contractorId) {
        return competencies.values().stream().filter(c -> c.contractorId().equals(contractorId)).toList();
    }

    @Override
    public ProjectContractor saveAssignment(ProjectContractor assignment) {
        assignments.put(assignment.id(), assignment);
        return assignment;
    }

    @Override
    public List<ProjectContractor> findAssignments(UUID projectId) {
        return assignments.values().stream().filter(a -> a.projectId().equals(projectId)).toList();
    }

    @Override
    public SiteAccessGrant saveGrant(SiteAccessGrant grant) {
        grants.put(grant.id(), grant);
        return grant;
    }

    @Override
    public Optional<SiteAccessGrant> findGrant(UUID id) {
        return Optional.ofNullable(grants.get(id));
    }

    @Override
    public List<SiteAccessGrant> findGrantsForContractor(UUID contractorId) {
        return grants.values().stream().filter(g -> g.contractorId().equals(contractorId)).toList();
    }

    @Override
    public List<SiteAccessGrant> findActiveGrants(int limit) {
        return grants.values().stream().filter(g -> g.status() == SiteAccessGrant.Status.ACTIVE)
                .sorted(Comparator.comparing(SiteAccessGrant::requestedAt)).limit(limit).toList();
    }

    // ---- permits -------------------------------------------------------------------------------

    @Override
    public PermitRecord savePermit(PermitRecord permit) {
        permits.put(permit.permitId(), permit);
        return permit;
    }

    @Override
    public Optional<PermitRecord> findPermit(String permitId) {
        return Optional.ofNullable(permits.get(permitId));
    }

    @Override
    public List<PermitRecord> findPermits(Collection<String> permitIds) {
        return permitIds.stream().map(permits::get).filter(java.util.Objects::nonNull).toList();
    }

    @Override
    public List<PermitRecord> findPermitsForContractor(Collection<String> contractorReferences) {
        return permits.values().stream()
                .filter(p -> p.contractorReference() != null && contractorReferences.contains(p.contractorReference()))
                .toList();
    }

    @Override
    public ProjectPermitLink saveLink(ProjectPermitLink link) {
        links.put(link.id(), link);
        return link;
    }

    @Override
    public List<ProjectPermitLink> findLinks(UUID projectId) {
        return links.values().stream().filter(l -> l.projectId().equals(projectId)).toList();
    }

    // ---- variations ----------------------------------------------------------------------------

    @Override
    public VariationOrder saveVariation(VariationOrder variation) {
        variations.put(variation.id(), variation);
        return variation;
    }

    @Override
    public Optional<VariationOrder> findVariation(UUID id) {
        return Optional.ofNullable(variations.get(id));
    }

    @Override
    public List<VariationOrder> findVariations(UUID projectId) {
        return variations.values().stream().filter(v -> v.projectId().equals(projectId))
                .sorted(Comparator.comparing(VariationOrder::submittedAt)).toList();
    }

    @Override
    public String nextVariationReference(String siteCode) {
        return "VO-" + normalize(siteCode) + "-" + String.format("%05d", variationSequence.incrementAndGet());
    }

    // ---- handover and defects ------------------------------------------------------------------

    @Override
    public Handover saveHandover(Handover handover) {
        handovers.put(handover.id(), handover);
        return handover;
    }

    @Override
    public List<Handover> findHandovers(UUID projectId) {
        return handovers.values().stream().filter(h -> h.projectId().equals(projectId))
                .sorted(Comparator.comparing(Handover::recordedAt)).toList();
    }

    @Override
    public RegisterChange saveRegisterChange(RegisterChange change) {
        registerChanges.put(change.id(), change);
        return change;
    }

    @Override
    public List<RegisterChange> findRegisterChanges(UUID projectId) {
        return registerChanges.values().stream().filter(c -> c.projectId().equals(projectId)).toList();
    }

    @Override
    public DefectItem saveDefect(DefectItem defect) {
        defects.put(defect.id(), defect);
        return defect;
    }

    @Override
    public Optional<DefectItem> findDefect(UUID id) {
        return Optional.ofNullable(defects.get(id));
    }

    @Override
    public List<DefectItem> findDefects(UUID projectId) {
        return defects.values().stream().filter(d -> d.projectId().equals(projectId))
                .sorted(Comparator.comparing(DefectItem::raisedAt)).toList();
    }

    @Override
    public List<DefectItem> findOpenDefects(int limit) {
        return defects.values().stream().filter(d -> d.status() == DefectItem.Status.OPEN)
                .sorted(Comparator.comparing(DefectItem::raisedAt)).limit(limit).toList();
    }

    @Override
    public String nextDefectReference(String siteCode) {
        return "DL-" + normalize(siteCode) + "-" + String.format("%05d", defectSequence.incrementAndGet());
    }

    private static String normalize(String value) {
        return value == null ? null : value.strip().toUpperCase(Locale.ROOT);
    }
}
