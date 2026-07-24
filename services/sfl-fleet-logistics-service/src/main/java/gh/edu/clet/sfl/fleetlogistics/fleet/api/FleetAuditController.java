package gh.edu.clet.sfl.fleetlogistics.fleet.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.mapper.FleetEvidenceMapper;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.response.FleetEvidenceResponses.AuditChainVerificationResponse;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.AuditPort;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.FleetAccessPolicy;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.FleetAuditService;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.FleetEvidenceApplicationService;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.AuditAction;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.AuditEvent;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Audit search and chain replay endpoints (SRS-SFL-S166-03). */
@RestController
@RequestMapping("/api/v1/fleet/audit")
@io.swagger.v3.oas.annotations.tags.Tag(name = "Audit")
class FleetAuditController {

    private final FleetAuditService auditService;
    private final FleetEvidenceApplicationService evidenceService;
    private final FleetAccessPolicy accessPolicy;
    private final FleetEvidenceMapper mapper;
    private final FleetActorResolver actorResolver;

    FleetAuditController(FleetAuditService auditService, FleetEvidenceApplicationService evidenceService,
            FleetAccessPolicy accessPolicy, FleetEvidenceMapper mapper, FleetActorResolver actorResolver) {
        this.auditService = auditService;
        this.evidenceService = evidenceService;
        this.accessPolicy = accessPolicy;
        this.mapper = mapper;
        this.actorResolver = actorResolver;
    }

    @GetMapping("/records")
    ApiResponse<List<AuditEvent>> search(
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String resourceId,
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            HttpServletRequest httpRequest) {
        ActorContext actor = actorResolver.resolve(httpRequest);
        accessPolicy.requirePermission(actor, SflPermission.FLEET_AUDIT_READ, "AuditEvent");
        var scope = accessPolicy.requireSiteScopeFilter(actor);
        return ApiResponse.ok(auditService.search(new AuditPort.AuditQuery(scope.allSites()
                ? List.of()
                : List.copyOf(scope.sites()), resourceType, resourceId, actorId, action, from, to, page, size)));
    }

    @GetMapping("/chain/verification")
    ApiResponse<AuditChainVerificationResponse> verify(HttpServletRequest httpRequest) {
        return ApiResponse.ok(mapper.toResponse(evidenceService.verifyAuditChain(actorResolver.resolve(httpRequest))));
    }
}
