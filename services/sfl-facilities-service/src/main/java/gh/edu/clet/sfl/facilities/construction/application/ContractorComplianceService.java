package gh.edu.clet.sfl.facilities.construction.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.facilities.construction.application.ports.ConstructionRepository;
import gh.edu.clet.sfl.facilities.construction.application.ports.SiteAccessPort;
import gh.edu.clet.sfl.facilities.construction.domain.ConstructionProject;
import gh.edu.clet.sfl.facilities.construction.domain.ConstructionRefusal;
import gh.edu.clet.sfl.facilities.construction.domain.Contractor;
import gh.edu.clet.sfl.facilities.construction.domain.ContractorCompetency;
import gh.edu.clet.sfl.facilities.construction.domain.PermitRecord;
import gh.edu.clet.sfl.facilities.construction.domain.SiteAccessGrant;
import gh.edu.clet.sfl.facilities.construction.domain.policy.ContractorCompliancePolicy;
import gh.edu.clet.sfl.facilities.shared.application.port.AuditPort;
import gh.edu.clet.sfl.facilities.shared.application.port.SecurityEventForwarderPort;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditAction;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesErrorCode;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.model.EstateCodes;
import gh.edu.clet.sfl.facilities.shared.domain.security.SecurityEvent;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Contractor compliance and site-access coordination - SRS-SFL-S176-02.
 *
 * <h2>The two paths, and the one rule under both</h2>
 *
 * A site-access request is checked against {@link ContractorCompliancePolicy} and refused with
 * {@code CONTRACTOR_COMPLIANCE_LAPSED} - every lapse named - if insurance or any required competency
 * has expired. The refused request is kept, with its reason.
 *
 * <p>Later lapses are caught without anybody checking: {@link #suspendLapsedGrants} runs on a timer
 * ({@code ConstructionScheduledJobs}) and suspends every active grant of a contractor the same policy
 * now finds lapsed, publishing the suspension, forwarding it to the SIEM and auditing
 * {@code CONTRACTOR_ACCESS_SUSPENDED}. Recording an expiry date already in the past suspends
 * immediately, through the same method, rather than waiting for the next run.
 *
 * <h2>What a suspension here does not do</h2>
 *
 * It does not close a door. S160a (Access Control) and S160 (Visitor Management) are in SSEMP and no
 * consumer there reads {@code contractor-site-access-requested} or {@code -suspended} yet. The grant
 * says {@code RECORDED_NOT_ENFORCED}; the runbook and gap report say it louder. Until SSEMP consumes the
 * event, a suspended contractor is stopped by a person reading the dashboard, not by the turnstile.
 */
@Service
public class ContractorComplianceService {

    private final ConstructionContext context;
    private final ConstructionRepository repository;
    private final AuditPort audit;
    private final SiteAccessPort siteAccess;
    private final SecurityEventForwarderPort siem;

    public ContractorComplianceService(ConstructionContext context, SiteAccessPort siteAccess,
            SecurityEventForwarderPort siem) {
        this.context = context;
        this.repository = context.repository();
        this.audit = context.audit();
        this.siteAccess = siteAccess;
        this.siem = siem;
    }

    // =============================================================================================
    // Contractor records
    // =============================================================================================

    @Transactional
    public Contractor register(ConstructionCommands.RegisterContractor command) {
        ActorContext actor = command.actor();
        String siteCode = EstateCodes.normalize(command.siteCode());
        context.authorization().require(actor, SflPermission.FACILITIES_CONTRACTOR_MANAGE, siteCode,
                command.channel(), "Contractor", "new");
        if (command.insuranceExpiresOn() == null) {
            throw new FacilitiesException.ValidationFailedException("A contractor record must carry its insurance expiry.");
        }
        String code = EstateCodes.normalize(command.contractorCode());
        repository.findContractorByCode(siteCode, code).ifPresent(existing -> {
            throw new FacilitiesException.DuplicateIdentifierException("contractor", code, siteCode);
        });
        Contractor contractor = repository.saveContractor(Contractor.register(UUID.randomUUID(), siteCode, code,
                command.name(), command.vendorReference(), command.insuranceProvider(),
                command.insurancePolicyReference(), command.insuranceExpiresOn(), actor.actorId(), context.now(),
                command.channel(), actor.correlationId()));
        audit.record(actor, command.channel(), AuditAction.CONTRACTOR_REGISTERED, "Contractor",
                contractor.id().toString(), contractor.siteCode(), null, contractor);
        return contractor;
    }

    @Transactional
    public Contractor updateInsurance(ConstructionCommands.UpdateInsurance command) {
        ActorContext actor = command.actor();
        Contractor contractor = context.requireContractor(command.contractorId());
        requireManage(actor, contractor, command.channel());
        contractor.metadata().requireVersion(command.expectedVersion(), "Contractor", contractor.id());
        Contractor updated = repository.saveContractor(contractor.updateInsurance(command.insuranceProvider(),
                command.insurancePolicyReference(), command.insuranceExpiresOn(), actor.actorId(), context.now(),
                command.channel(), actor.correlationId()));
        audit.record(actor, command.channel(), AuditAction.CONTRACTOR_COMPLIANCE_UPDATED, "Contractor",
                updated.id().toString(), updated.siteCode(), contractor, updated);
        suspendIfLapsed(updated, actor, command.channel());
        return updated;
    }

    /** Records a required certification, or renews one already recorded under the same code. */
    @Transactional
    public ContractorCompetency recordCompetency(ConstructionCommands.RecordCompetency command) {
        ActorContext actor = command.actor();
        Contractor contractor = context.requireContractor(command.contractorId());
        requireManage(actor, contractor, command.channel());
        if (command.expiresOn() == null) {
            throw new FacilitiesException.ValidationFailedException("A competency certification must carry its expiry.");
        }
        String code = EstateCodes.normalize(command.certificationCode());
        Instant at = context.now();
        ContractorCompetency existing = repository.findCompetencies(contractor.id()).stream()
                .filter(competency -> competency.certificationCode().equals(code)).findFirst().orElse(null);
        ContractorCompetency saved = repository.saveCompetency(existing == null
                ? ContractorCompetency.record(UUID.randomUUID(), contractor, code, command.description(),
                        command.certificateReference(), command.expiresOn(), actor.actorId(), at, command.channel(),
                        actor.correlationId())
                : existing.renew(command.description(), command.certificateReference(), command.expiresOn(),
                        actor.actorId(), at, command.channel(), actor.correlationId()));
        audit.record(actor, command.channel(), AuditAction.CONTRACTOR_COMPLIANCE_UPDATED, "Contractor",
                contractor.id().toString(), contractor.siteCode(), existing, saved);
        suspendIfLapsed(contractor, actor, command.channel());
        return saved;
    }

    // =============================================================================================
    // Site access
    // =============================================================================================

    @Transactional(noRollbackFor = ConstructionRefusal.class)
    public SiteAccessGrant requestAccess(ConstructionCommands.RequestSiteAccess command) {
        ActorContext actor = command.actor();
        Contractor contractor = context.requireContractor(command.contractorId());
        requireManage(actor, contractor, command.channel());
        if (command.projectId() != null) {
            ConstructionProject project = context.requireProject(command.projectId());
            boolean assigned = repository.findAssignments(project.id()).stream()
                    .anyMatch(assignment -> assignment.contractorId().equals(contractor.id()));
            if (!assigned) {
                throw new FacilitiesException.ValidationFailedException("Contractor " + contractor.contractorCode()
                        + " is not assigned to project " + project.projectReference() + ".");
            }
        }
        Instant at = context.now();
        Instant from = command.validFrom() == null ? at : command.validFrom();
        if (command.validTo() == null) {
            throw new FacilitiesException.ValidationFailedException("An access request must say when it ends.");
        }
        ContractorCompliancePolicy.Compliance compliance = compliance(contractor);
        if (!compliance.compliant()) {
            SiteAccessGrant refused = repository.saveGrant(SiteAccessGrant.refuse(UUID.randomUUID(), contractor,
                    command.projectId(), command.accessScope(), from, command.validTo(), compliance.reason(),
                    actor.actorId(), at, command.channel(), actor.correlationId()));
            audit.record(actor, command.channel(), AuditAction.CONTRACTOR_ACCESS_REFUSED, "SiteAccessGrant",
                    refused.id().toString(), refused.siteCode(), null, refused);
            throw new ConstructionRefusal(FacilitiesErrorCode.CONTRACTOR_COMPLIANCE_LAPSED, compliance.reason());
        }
        SiteAccessGrant grant = repository.saveGrant(SiteAccessGrant.grant(UUID.randomUUID(), contractor,
                command.projectId(), command.accessScope(), from, command.validTo(), actor.actorId(), at,
                command.channel(), actor.correlationId()));
        SiteAccessPort.Dispatch dispatch = siteAccess.requestAccess(grant, contractor, actor);
        grant = repository.saveGrant(grant.dispatchedVia(dispatch.provider(), dispatch.enforced()
                ? SiteAccessGrant.Enforcement.ENFORCED : SiteAccessGrant.Enforcement.RECORDED_NOT_ENFORCED));
        audit.record(actor, command.channel(), AuditAction.CONTRACTOR_ACCESS_REQUESTED, "SiteAccessGrant",
                grant.id().toString(), grant.siteCode(), null, grant);
        return grant;
    }

    /**
     * The automatic suspension - SRS-SFL-S176-02 "an expired insurance or competency record
     * automatically suspends associated site-access grants rather than requiring a manual check".
     *
     * <p>Idempotent: a suspended grant is no longer ACTIVE and is not revisited, so two instances
     * sweeping at once suspend each grant once (the second loses the optimistic lock and retries next
     * run with nothing to do).
     */
    @Transactional
    public SuspensionSweep suspendLapsedGrants(ActorContext actor) {
        Instant at = context.now();
        List<SiteAccessGrant> active = repository.findActiveGrants(context.configuration().sweepBatchSize());
        int suspended = 0;
        java.util.Map<UUID, ContractorCompliancePolicy.Compliance> evaluated = new java.util.HashMap<>();
        for (SiteAccessGrant grant : active) {
            ContractorCompliancePolicy.Compliance compliance = evaluated.computeIfAbsent(grant.contractorId(),
                    id -> compliance(context.requireContractor(id)));
            if (!compliance.compliant()) {
                suspend(grant, context.requireContractor(grant.contractorId()), compliance.reason(), actor,
                        SourceChannel.SCHEDULER);
                suspended++;
            }
        }
        return new SuspensionSweep(active.size(), suspended, at);
    }

    public record SuspensionSweep(int examined, int suspended, Instant evaluatedAt) {
    }

    // =============================================================================================
    // Queries
    // =============================================================================================

    /** A contractor with its certifications, its compliance today and the permits authorising it now. */
    public record ContractorView(Contractor contractor, List<ContractorCompetency> competencies,
            ContractorCompliancePolicy.Compliance compliance, List<PermitRecord> currentPermits,
            List<SiteAccessGrant> grants) {
    }

    @Transactional(readOnly = true)
    public ContractorView find(UUID contractorId, ActorContext actor, SourceChannel channel) {
        Contractor contractor = context.requireContractor(contractorId);
        context.requireRead(actor, contractor.siteCode(), channel, "Contractor", contractorId.toString());
        return view(contractor);
    }

    @Transactional(readOnly = true)
    public List<ContractorView> search(String siteCode, ActorContext actor, SourceChannel channel) {
        context.authorization().require(actor, SflPermission.FACILITIES_PROJECT_READ, channel, "Contractor", "list",
                siteCode);
        context.authorization().requireRequestedSite(actor, siteCode, channel, "Contractor");
        List<Contractor> found = repository.findContractors(siteCode == null || siteCode.isBlank() ? null
                : EstateCodes.normalize(siteCode));
        return context.authorization().filterBySite(actor, found, Contractor::siteCode).stream().map(this::view)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SiteAccessGrant> grants(UUID contractorId, ActorContext actor, SourceChannel channel) {
        Contractor contractor = context.requireContractor(contractorId);
        context.requireRead(actor, contractor.siteCode(), channel, "Contractor", contractorId.toString());
        return repository.findGrantsForContractor(contractor.id());
    }

    // =============================================================================================
    // Internals
    // =============================================================================================

    ContractorView view(Contractor contractor) {
        List<ContractorCompetency> competencies = repository.findCompetencies(contractor.id());
        Instant now = context.now();
        List<String> references = new ArrayList<>();
        references.add(contractor.id().toString());
        if (contractor.vendorReference() != null) {
            references.add(contractor.vendorReference());
        }
        List<PermitRecord> permits = repository.findPermitsForContractor(references).stream()
                .filter(permit -> permit.siteCode().equals(contractor.siteCode()) && permit.isCurrentAt(now))
                .toList();
        return new ContractorView(contractor, competencies, ContractorCompliancePolicy.evaluate(contractor,
                competencies, context.today(), context.configuration().expiryWarningDays(contractor.siteCode())),
                permits, repository.findGrantsForContractor(contractor.id()));
    }

    ContractorCompliancePolicy.Compliance compliance(Contractor contractor) {
        LocalDate today = context.today();
        return ContractorCompliancePolicy.evaluate(contractor, repository.findCompetencies(contractor.id()), today,
                context.configuration().expiryWarningDays(contractor.siteCode()));
    }

    private void suspendIfLapsed(Contractor contractor, ActorContext actor, SourceChannel channel) {
        ContractorCompliancePolicy.Compliance compliance = compliance(contractor);
        if (compliance.compliant()) {
            return;
        }
        for (SiteAccessGrant grant : repository.findGrantsForContractor(contractor.id())) {
            if (grant.status() == SiteAccessGrant.Status.ACTIVE) {
                suspend(grant, contractor, compliance.reason(), actor, channel);
            }
        }
    }

    private void suspend(SiteAccessGrant grant, Contractor contractor, String reason, ActorContext actor,
            SourceChannel channel) {
        Instant at = context.now();
        SiteAccessGrant suspended = repository.saveGrant(grant.suspend(
                gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesErrorCode.CONTRACTOR_COMPLIANCE_LAPSED
                        .defaultMessage() + " " + reason, actor.actorId(), at, channel, actor.correlationId()));
        siteAccess.suspendAccess(suspended, contractor, reason, actor);
        siem.forward(new SecurityEvent("S176", "CONTRACTOR_ACCESS_SUSPENDED", SecurityEvent.Severity.WARNING,
                suspended.siteCode(), "Contractor " + contractor.contractorCode()
                        + " site access suspended automatically: compliance lapsed. Recorded, not enforced at the door.",
                suspended.id().toString(), at));
        audit.record(actor, channel, AuditAction.CONTRACTOR_ACCESS_SUSPENDED, "SiteAccessGrant",
                suspended.id().toString(), suspended.siteCode(), grant, Map.of("grant", suspended, "reason", reason));
    }

    private void requireManage(ActorContext actor, Contractor contractor, SourceChannel channel) {
        context.authorization().require(actor, SflPermission.FACILITIES_CONTRACTOR_MANAGE, contractor.siteCode(),
                channel, "Contractor", contractor.id().toString());
    }
}
