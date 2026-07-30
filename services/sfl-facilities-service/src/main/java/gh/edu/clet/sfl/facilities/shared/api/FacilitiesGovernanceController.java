package gh.edu.clet.sfl.facilities.shared.api;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.facilities.shared.application.FacilitiesAuthorization;
import gh.edu.clet.sfl.facilities.shared.application.FacilitiesGovernanceService;
import gh.edu.clet.sfl.facilities.shared.application.port.RuntimeConfigurationPort;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditAction;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditChainVerification;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditEvent;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Audit, runtime configuration and the actor's own permissions (SRS-SFL-S152-02, -03).
 *
 * <p>Three small surfaces that belong to the platform rather than to any one module, grouped so they
 * are read together: what happened, what the rules were when it happened, and who is allowed to do
 * what. S153 and S159 inherit all three when they arrive.
 */
@RestController
@RequestMapping("/api/v1/facilities")
@Tag(name = "S152 Governance", description = "Audit trail, runtime configuration and actor permissions")
public class FacilitiesGovernanceController {

    private final FacilitiesGovernanceService governance;
    private final FacilitiesAuthorization authorization;
    private final FacilitiesActorResolver actorResolver;

    public FacilitiesGovernanceController(FacilitiesGovernanceService governance,
            FacilitiesAuthorization authorization, FacilitiesActorResolver actorResolver) {
        this.governance = governance;
        this.authorization = authorization;
        this.actorResolver = actorResolver;
    }

    // ---- audit --------------------------------------------------------------------------------

    @GetMapping("/audit")
    @Operation(summary = "Search the audit trail",
            description = "SRS-SFL-S152-03. Append-only and hash-chained; includes refused attempts, which "
                    + "are recorded as AUTHORIZATION_DENIED.")
    public List<AuditEvent> audit(
            @RequestParam(required = false) String siteCode,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String resourceId,
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "100") int limit,
            HttpServletRequest http) {
        return governance.search(siteCode, resourceType, resourceId, actorId, action, from, to, limit,
                actor(http), channel(http));
    }

    @GetMapping("/audit/integrity")
    @Operation(summary = "Replay the audit chain and report whether it is intact",
            description = "A broken result names the record it broke at, what was expected and what was "
                    + "found. SRS-SFL-S152-03: 'Audit integrity check failed. Escalate to compliance and "
                    + "security.' Running the check is itself audited.")
    public AuditChainVerification verifyChain(HttpServletRequest http) {
        return governance.verifyChain(actor(http), channel(http));
    }

    // ---- runtime configuration ----------------------------------------------------------------

    @GetMapping("/configuration")
    @Operation(summary = "Read the active runtime configuration",
            description = "NFR 23.8. Site values override platform defaults; both are returned so an "
                    + "operator can see which is in force.")
    public List<RuntimeConfigurationPort.ConfigurationValue> configuration(
            @RequestParam(required = false) String siteCode, HttpServletRequest http) {
        return governance.activeConfiguration(siteCode, actor(http), channel(http));
    }

    @PutMapping("/configuration/{key}")
    @Operation(summary = "Supersede a configuration value",
            description = "Versioned rather than overwritten: the previous value is closed with an "
                    + "effective-to date so a past escalation can be reconciled against the threshold that "
                    + "was actually active.")
    public RuntimeConfigurationPort.ConfigurationValue putConfiguration(@PathVariable String key,
            @Valid @RequestBody PutConfigurationRequest request, HttpServletRequest http) {
        return governance.putConfiguration(key, request.siteCode(), request.value(), request.valueType(),
                request.description(), actor(http), channel(http));
    }

    public record PutConfigurationRequest(
            @NotBlank @Size(max = 2000) String value,
            @Size(max = 30) String valueType,
            @Size(max = 500) String description,
            /** Null sets the platform default; a site code sets an override for that site. */
            @Size(max = 40) String siteCode) {
    }

    // ---- actor permissions --------------------------------------------------------------------

    /**
     * What this actor may do.
     *
     * <p>Read by the operations dashboard to decide which screens to offer. Mirrors the fleet and
     * emergency services' own endpoints so one client can ask every service the same question — and it
     * is the difference between a user being offered a screen and meeting a 403 on arrival.
     *
     * <p>Reads no data, so it needs no transaction and no permission of its own: the answer is derived
     * entirely from the roles the caller already presented.
     */
    @GetMapping("/actor/permissions")
    @Operation(summary = "The permissions and site scopes of the calling actor")
    public ActorPermissionsResponse actorPermissions(HttpServletRequest http) {
        ActorContext actor = actor(http);
        Set<SflPermission> permissions = authorization.permissionsOf(actor);
        return new ActorPermissionsResponse(actor.actorId(), actor.principal().displayName(),
                actor.principal().roles().stream().map(Enum::name).sorted().toList(),
                actor.principal().siteScopes().stream().sorted().toList(),
                permissions.stream().map(Enum::name).sorted().toList());
    }

    public record ActorPermissionsResponse(
            String actorId,
            String displayName,
            List<String> roles,
            List<String> siteScopes,
            List<String> permissions) {
    }

    private ActorContext actor(HttpServletRequest http) {
        return actorResolver.resolve(http);
    }

    private SourceChannel channel(HttpServletRequest http) {
        return actorResolver.resolveSourceChannel(http);
    }
}
