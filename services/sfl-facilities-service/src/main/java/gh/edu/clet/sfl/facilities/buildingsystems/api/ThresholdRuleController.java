package gh.edu.clet.sfl.facilities.buildingsystems.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.buildingsystems.application.BuildingSystemsCommands;
import gh.edu.clet.sfl.facilities.buildingsystems.application.ThresholdRuleService;
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

/** Threshold and fault-condition rules - SRS-SFL-S156-02. */
@RestController
@RequestMapping("/api/v1/facilities/building-systems/rules")
@Tag(name = "S156 Threshold Rules", description = "Configurable, versioned threshold and fault-condition rules")
public class ThresholdRuleController {

    private final ThresholdRuleService rules;

    public ThresholdRuleController(ThresholdRuleService rules) {
        this.rules = rules;
    }

    @PostMapping
    @Operation(summary = "Create a rule", description = "SRS-SFL-S156-02. Requires FACILITIES_BMS_RULE_MANAGE.")
    public ResponseEntity<ApiResponse<BuildingSystemsResponses.RuleChangeResponse>> create(
            @Valid @RequestBody BuildingSystemsRequests.CreateRule request, ActorContext actor, SourceChannel channel) {
        ThresholdRuleService.RuleChange change = rules.create(new BuildingSystemsCommands.CreateRule(
                request.siteCode(), request.name(), request.quantity(), request.systemType(), request.deviceId(),
                request.buildingCode(), request.roomId(), request.condition(), request.lowerLimit(),
                request.upperLimit(), request.codes(), request.debounce(), request.priority(), request.reason(),
                actor, channel));
        return ResponseEntity.created(URI.create("/api/v1/facilities/building-systems/rules/"
                        + change.rule().ruleId()))
                .body(ApiResponse.ok(BuildingSystemsResponses.RuleChangeResponse.from(change)));
    }

    @PatchMapping("/{ruleId}")
    @Operation(summary = "Revise a rule", description = "Writes a new version; the previous version is kept, superseded.")
    public ApiResponse<BuildingSystemsResponses.RuleChangeResponse> revise(@PathVariable UUID ruleId,
            @Valid @RequestBody BuildingSystemsRequests.ReviseRule request, ActorContext actor, SourceChannel channel) {
        ThresholdRuleService.RuleChange change = rules.revise(new BuildingSystemsCommands.ReviseRule(ruleId,
                request.name(), request.systemType(), request.deviceId(), request.buildingCode(), request.roomId(),
                request.condition(), request.lowerLimit(), request.upperLimit(), request.codes(), request.debounce(),
                request.priority(), request.reason(), request.expectedRuleVersion(), actor, channel));
        return ApiResponse.ok(BuildingSystemsResponses.RuleChangeResponse.from(change));
    }

    @PatchMapping("/{ruleId}/disablement")
    @Operation(summary = "Disable a rule with an audited override",
            description = "Requires FACILITIES_BMS_RULE_OVERRIDE, a reason and a named accountable owner - "
                    + "the rule-authoring engineer does not hold this permission (SRS-SFL-S156-02).")
    public ApiResponse<BuildingSystemsResponses.RuleChangeResponse> disable(@PathVariable UUID ruleId,
            @Valid @RequestBody BuildingSystemsRequests.DisableRule request, ActorContext actor, SourceChannel channel) {
        ThresholdRuleService.RuleChange change = rules.disable(new BuildingSystemsCommands.DisableRule(ruleId,
                request.reason(), request.accountableOwner(), actor, channel));
        return ApiResponse.ok(BuildingSystemsResponses.RuleChangeResponse.from(change));
    }

    @PatchMapping("/{ruleId}/enablement")
    @Operation(summary = "Re-enable a disabled rule")
    public ApiResponse<BuildingSystemsResponses.RuleChangeResponse> enable(@PathVariable UUID ruleId,
            @Valid @RequestBody BuildingSystemsRequests.EnableRule request, ActorContext actor, SourceChannel channel) {
        ThresholdRuleService.RuleChange change = rules.enable(new BuildingSystemsCommands.EnableRule(ruleId,
                request.reason(), actor, channel));
        return ApiResponse.ok(BuildingSystemsResponses.RuleChangeResponse.from(change));
    }

    @GetMapping
    @Operation(summary = "List current rules for a site")
    public ApiResponse<List<BuildingSystemsResponses.RuleResponse>> list(@RequestParam String siteCode,
            ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(rules.rules(siteCode, actor, channel).stream()
                .map(BuildingSystemsResponses.RuleResponse::from).toList());
    }

    @GetMapping("/{ruleId}/history")
    @Operation(summary = "Every version of a rule, oldest first")
    public ApiResponse<List<BuildingSystemsResponses.RuleResponse>> history(@PathVariable UUID ruleId,
            ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(rules.history(ruleId, actor, channel).stream()
                .map(BuildingSystemsResponses.RuleResponse::from).toList());
    }

    @GetMapping("/conflicts")
    @Operation(summary = "Logged rule conflicts for a site", description = "SRS-SFL-S156-02 Rule Conflict error state.")
    public ApiResponse<List<BuildingSystemsResponses.ConflictResponse>> conflicts(@RequestParam String siteCode,
            ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(rules.conflicts(siteCode, actor, channel).stream()
                .map(BuildingSystemsResponses.ConflictResponse::from).toList());
    }
}
