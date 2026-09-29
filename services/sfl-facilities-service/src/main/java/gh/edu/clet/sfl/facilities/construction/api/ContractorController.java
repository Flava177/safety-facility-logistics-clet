package gh.edu.clet.sfl.facilities.construction.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.construction.application.ConstructionCommands;
import gh.edu.clet.sfl.facilities.construction.application.ContractorComplianceService;
import gh.edu.clet.sfl.facilities.construction.domain.Contractor;
import gh.edu.clet.sfl.facilities.construction.domain.ContractorCompetency;
import gh.edu.clet.sfl.facilities.construction.domain.SiteAccessGrant;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contractor compliance and site-access coordination - SRS-SFL-S176-02.
 *
 * <p>Site access is recorded, not enforced: see {@code OutboxSiteAccessAdapter} and the S176 runbook.
 * A refused or suspended grant is returned with its reason, never silently.
 */
@RestController
@RequestMapping("/api/v1/facilities/construction/contractors")
@Tag(name = "S176 Contractors", description = "Contractor compliance and site-access coordination")
public class ContractorController {

    private final ContractorComplianceService service;

    public ContractorController(ContractorComplianceService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Register a contractor",
            description = "SRS-SFL-S176-02. Insurance expiry is required; competency certifications "
                    + "and permits are added separately.")
    public ResponseEntity<ApiResponse<ConstructionResponses.ContractorResponse>> register(
            @Valid @RequestBody ConstructionRequests.RegisterContractor request, ActorContext actor,
            SourceChannel channel) {
        Contractor contractor = service.register(new ConstructionCommands.RegisterContractor(request.siteCode(),
                request.contractorCode(), request.name(), request.vendorReference(), request.insuranceProvider(),
                request.insurancePolicyReference(), request.insuranceExpiresOn(), actor, channel));
        return ResponseEntity.created(URI.create("/api/v1/facilities/construction/contractors/" + contractor.id()))
                .body(ApiResponse.ok(ConstructionResponses.ContractorResponse.from(service.find(contractor.id(),
                        actor, channel))));
    }

    @PatchMapping("/{contractorId}/insurance")
    @Operation(summary = "Update insurance cover",
            description = "An expiry already in the past suspends every active site-access grant "
                    + "immediately, in the same request.")
    public ApiResponse<ConstructionResponses.ContractorResponse> updateInsurance(@PathVariable UUID contractorId,
            @Valid @RequestBody ConstructionRequests.UpdateInsurance request, ActorContext actor,
            SourceChannel channel) {
        service.updateInsurance(new ConstructionCommands.UpdateInsurance(contractorId, request.insuranceProvider(),
                request.insurancePolicyReference(), request.insuranceExpiresOn(), request.expectedVersion(), actor,
                channel));
        return ApiResponse.ok(ConstructionResponses.ContractorResponse.from(service.find(contractorId, actor, channel)));
    }

    @PostMapping("/{contractorId}/competencies")
    @Operation(summary = "Record or renew a required competency certification")
    public ApiResponse<ConstructionResponses.CompetencyResponse> recordCompetency(@PathVariable UUID contractorId,
            @Valid @RequestBody ConstructionRequests.RecordCompetency request, ActorContext actor,
            SourceChannel channel) {
        ContractorCompetency competency = service.recordCompetency(new ConstructionCommands.RecordCompetency(
                contractorId, request.certificationCode(), request.description(), request.certificateReference(),
                request.expiresOn(), actor, channel));
        return ApiResponse.ok(ConstructionResponses.CompetencyResponse.from(competency));
    }

    @PostMapping("/{contractorId}/site-access")
    @Operation(summary = "Request site access for a contractor",
            description = "SRS-SFL-S176-02 AC: refused with CONTRACTOR_COMPLIANCE_LAPSED, reason named, "
                    + "if insurance or a required competency has expired. Recorded and published for "
                    + "S160a/S160; not enforced by them yet.")
    public ResponseEntity<ApiResponse<ConstructionResponses.SiteAccessGrantResponse>> requestAccess(
            @PathVariable UUID contractorId, @Valid @RequestBody ConstructionRequests.RequestSiteAccess request,
            ActorContext actor, SourceChannel channel) {
        SiteAccessGrant grant = service.requestAccess(new ConstructionCommands.RequestSiteAccess(contractorId,
                request.projectId(), request.accessScope(), request.validFrom(), request.validTo(), actor, channel));
        return ResponseEntity.created(URI.create("/api/v1/facilities/construction/contractors/" + contractorId
                        + "/site-access/" + grant.id()))
                .body(ApiResponse.ok(ConstructionResponses.SiteAccessGrantResponse.from(grant)));
    }

    @GetMapping
    @Operation(summary = "Search contractors, with today's compliance", description = "Contractor compliance dashboard feed.")
    public ApiResponse<List<ConstructionResponses.ContractorResponse>> search(
            @RequestParam(required = false) String siteCode, ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(service.search(siteCode, actor, channel).stream()
                .map(ConstructionResponses.ContractorResponse::from).toList());
    }

    @GetMapping("/{contractorId}")
    @Operation(summary = "Read one contractor, its competencies, current permits and compliance")
    public ApiResponse<ConstructionResponses.ContractorResponse> find(@PathVariable UUID contractorId,
            ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(ConstructionResponses.ContractorResponse.from(service.find(contractorId, actor, channel)));
    }

    @GetMapping("/{contractorId}/site-access")
    @Operation(summary = "Every site-access grant for a contractor")
    public ApiResponse<List<ConstructionResponses.SiteAccessGrantResponse>> grants(@PathVariable UUID contractorId,
            ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(service.grants(contractorId, actor, channel).stream()
                .map(ConstructionResponses.SiteAccessGrantResponse::from).toList());
    }
}
