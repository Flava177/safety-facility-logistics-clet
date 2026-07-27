package gh.edu.clet.sfl.emergencynotification.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.emergencynotification.application.service.EmergencyRecordsService;
import gh.edu.clet.sfl.emergencynotification.domain.model.AudienceGroup;
import gh.edu.clet.sfl.emergencynotification.domain.model.ChannelType;
import gh.edu.clet.sfl.emergencynotification.domain.model.EmergencyScenario;
import gh.edu.clet.sfl.emergencynotification.domain.model.NotificationTemplate;
import gh.edu.clet.sfl.emergencynotification.domain.model.Priority;
import gh.edu.clet.sfl.emergencynotification.domain.model.RecipientZone;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** SRS-SFL-S174-01: operational records — templates, scenarios, audience groups and recipient zones. */
@RestController
@RequestMapping("/api/v1/emergency")
public class EmergencyRecordsController {

    private final EmergencyRecordsService service;
    private final EmergencyActorResolver actors;

    public EmergencyRecordsController(EmergencyRecordsService service, EmergencyActorResolver actors) {
        this.service = service;
        this.actors = actors;
    }

    @PostMapping("/templates")
    @Tag(name = "Emergency Templates")
    public ResponseEntity<ApiResponse<NotificationTemplate>> createTemplate(@Valid @RequestBody TemplateRequest r,
            HttpServletRequest h) {
        var t = service.createTemplate(new EmergencyRecordsService.CreateTemplate(r.siteCode(), r.templateCode(),
                r.title(), r.body(), r.channels(), r.breakGlassEligible(), actors.resolve(h),
                actors.resolveSourceChannel(h)));
        return ResponseEntity.created(URI.create("/api/v1/emergency/templates/" + t.id())).body(ApiResponse.ok(t));
    }

    @GetMapping("/templates")
    @Tag(name = "Emergency Templates")
    public ApiResponse<List<NotificationTemplate>> templates(@RequestParam String siteCode, HttpServletRequest h) {
        return ApiResponse.ok(service.templates(siteCode, actors.resolve(h)));
    }

    @GetMapping("/templates/{id}")
    @Tag(name = "Emergency Templates")
    public ApiResponse<NotificationTemplate> template(@PathVariable UUID id, HttpServletRequest h) {
        return ApiResponse.ok(service.template(id, actors.resolve(h)));
    }

    @PostMapping("/scenarios")
    @Tag(name = "Emergency Scenarios")
    public ResponseEntity<ApiResponse<EmergencyScenario>> createScenario(@Valid @RequestBody ScenarioRequest r,
            HttpServletRequest h) {
        var s = service.createScenario(new EmergencyRecordsService.CreateScenario(r.siteCode(), r.scenarioCode(),
                r.name(), r.priority(), r.defaultTemplateId(), r.breakGlassEligible(), actors.resolve(h),
                actors.resolveSourceChannel(h)));
        return ResponseEntity.created(URI.create("/api/v1/emergency/scenarios/" + s.id())).body(ApiResponse.ok(s));
    }

    @GetMapping("/scenarios")
    @Tag(name = "Emergency Scenarios")
    public ApiResponse<List<EmergencyScenario>> scenarios(@RequestParam String siteCode, HttpServletRequest h) {
        return ApiResponse.ok(service.scenarios(siteCode, actors.resolve(h)));
    }

    @PostMapping("/audience-groups")
    @Tag(name = "Audience Groups")
    public ResponseEntity<ApiResponse<AudienceGroup>> createAudience(@Valid @RequestBody AudienceRequest r,
            HttpServletRequest h) {
        var a = service.createAudienceGroup(new EmergencyRecordsService.CreateAudienceGroup(r.siteCode(), r.groupCode(),
                r.name(), r.directoryReference(), r.recipientCount(), actors.resolve(h), actors.resolveSourceChannel(h)));
        return ResponseEntity.created(URI.create("/api/v1/emergency/audience-groups/" + a.id()))
                .body(ApiResponse.ok(a));
    }

    @GetMapping("/audience-groups")
    @Tag(name = "Audience Groups")
    public ApiResponse<List<AudienceGroup>> audiences(@RequestParam String siteCode, HttpServletRequest h) {
        return ApiResponse.ok(service.audienceGroups(siteCode, actors.resolve(h)));
    }

    @PostMapping("/recipient-zones")
    @Tag(name = "Recipient Zones")
    public ResponseEntity<ApiResponse<RecipientZone>> createZone(@Valid @RequestBody ZoneRequest r,
            HttpServletRequest h) {
        var z = service.createRecipientZone(new EmergencyRecordsService.CreateRecipientZone(r.siteCode(), r.zoneCode(),
                r.name(), r.locationReference(), actors.resolve(h), actors.resolveSourceChannel(h)));
        return ResponseEntity.created(URI.create("/api/v1/emergency/recipient-zones/" + z.id()))
                .body(ApiResponse.ok(z));
    }

    @GetMapping("/recipient-zones")
    @Tag(name = "Recipient Zones")
    public ApiResponse<List<RecipientZone>> zones(@RequestParam String siteCode, HttpServletRequest h) {
        return ApiResponse.ok(service.recipientZones(siteCode, actors.resolve(h)));
    }

    public record TemplateRequest(@NotBlank String siteCode, String templateCode, @NotBlank String title,
            @NotBlank String body, @NotNull List<ChannelType> channels, boolean breakGlassEligible) {}

    public record ScenarioRequest(@NotBlank String siteCode, String scenarioCode, @NotBlank String name,
            @NotNull Priority priority, UUID defaultTemplateId, boolean breakGlassEligible) {}

    public record AudienceRequest(@NotBlank String siteCode, String groupCode, @NotBlank String name,
            String directoryReference, int recipientCount) {}

    public record ZoneRequest(@NotBlank String siteCode, String zoneCode, @NotBlank String name,
            String locationReference) {}
}
