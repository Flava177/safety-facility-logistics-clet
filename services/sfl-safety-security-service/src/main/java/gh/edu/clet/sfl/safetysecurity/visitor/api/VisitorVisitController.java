package gh.edu.clet.sfl.safetysecurity.visitor.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.safetysecurity.visitor.application.port.VisitorRepository;
import gh.edu.clet.sfl.safetysecurity.visitor.application.service.VisitorCheckInOutService;
import gh.edu.clet.sfl.safetysecurity.visitor.application.service.VisitorDecisionService;
import gh.edu.clet.sfl.safetysecurity.visitor.application.service.VisitorRegistrationService;
import gh.edu.clet.sfl.safetysecurity.visitor.application.service.VisitorRollCallService;
import gh.edu.clet.sfl.safetysecurity.visitor.domain.model.VisitPurpose;
import gh.edu.clet.sfl.safetysecurity.visitor.domain.model.VisitStatus;
import gh.edu.clet.sfl.safetysecurity.visitor.domain.model.VisitorVisit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Instant;
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
 * SRS-SFL-S160-01: visitor pre-registration, host approval, badge/access-zone assignment, check-in,
 * check-out and roll-call.
 *
 * <p>{@code Idempotency-Key} is honoured on the one state-creating POST and nowhere else - every
 * other operation is a PATCH guarded by the record's version and its state machine, the same
 * reasoning {@code BookingController} documents.
 */
@RestController
@RequestMapping("/api/v1/visitors")
@Tag(name = "S160 Visitor Visits")
public class VisitorVisitController {

    private final VisitorRegistrationService registration;
    private final VisitorDecisionService decisions;
    private final VisitorCheckInOutService checkInOut;
    private final VisitorRollCallService rollCall;
    private final VisitorActorResolver actors;

    public VisitorVisitController(VisitorRegistrationService registration, VisitorDecisionService decisions,
            VisitorCheckInOutService checkInOut, VisitorRollCallService rollCall, VisitorActorResolver actors) {
        this.registration = registration;
        this.decisions = decisions;
        this.checkInOut = checkInOut;
        this.rollCall = rollCall;
        this.actors = actors;
    }

    @PostMapping("/visits")
    @Operation(summary = "Pre-register a visit",
            description = "Already holds a badge slot for the expected window, so a second host is not "
                    + "handed a clash to arbitrate. Confirmed at once for visit purposes that need no "
                    + "host approval.")
    public ResponseEntity<ApiResponse<VisitorVisit>> preRegister(
            @Valid @RequestBody PreRegisterRequest request, HttpServletRequest http) {
        VisitorVisit visit = registration.preRegister(new VisitorRegistrationService.PreRegisterVisit(
                request.siteCode(), request.visitorName(), request.visitorOrganization(),
                request.visitorContact(), request.hostId(), request.hostName(), request.purpose(),
                request.expectedArrival(), request.expectedDeparture(), actors.resolve(http),
                actors.resolveSourceChannel(http)));
        return ResponseEntity.created(URI.create("/api/v1/visitors/visits/" + visit.id()))
                .body(ApiResponse.ok(visit));
    }

    @PatchMapping("/visits/{visitId}/decision")
    @Operation(summary = "Approve or reject a visit request",
            description = "A rejection must carry a reason. Whoever registered the visit may not also "
                    + "decide on it. An approval requires a watchlist override reason if the visitor "
                    + "was flagged at pre-registration.")
    public ApiResponse<VisitorVisit> decide(@PathVariable UUID visitId,
            @Valid @RequestBody DecideRequest request, HttpServletRequest http) {
        return ApiResponse.ok(decisions.decide(new VisitorDecisionService.DecideVisit(visitId, request.approve(),
                request.reason(), request.watchlistOverrideReason(), request.expectedVersion(), actors.resolve(http),
                actors.resolveSourceChannel(http))));
    }

    @PatchMapping("/visits/{visitId}/badge")
    @Operation(summary = "Assign a badge and access zones",
            description = "Refused unless the visit is confirmed. Syncs to the badge/access-control "
                    + "gateway, which is a recorded stub until a vendor is chosen.")
    public ApiResponse<VisitorVisit> assignBadge(@PathVariable UUID visitId,
            @Valid @RequestBody AssignBadgeRequest request, HttpServletRequest http) {
        return ApiResponse.ok(checkInOut.assignBadge(new VisitorCheckInOutService.AssignBadge(visitId,
                request.badgeNumber(), request.accessZones() == null ? List.of() : request.accessZones(),
                request.expectedVersion(), actors.resolve(http), actors.resolveSourceChannel(http))));
    }

    @PatchMapping("/visits/{visitId}/check-in")
    @Operation(summary = "The visitor has arrived", description = "Requires a badge to already be assigned.")
    public ApiResponse<VisitorVisit> checkIn(@PathVariable UUID visitId,
            @Valid @RequestBody TransitionRequest request, HttpServletRequest http) {
        return ApiResponse.ok(checkInOut.checkIn(new VisitorCheckInOutService.Transition(visitId,
                request.expectedVersion(), actors.resolve(http), actors.resolveSourceChannel(http))));
    }

    @PatchMapping("/visits/{visitId}/check-out")
    @Operation(summary = "The visitor has left")
    public ApiResponse<VisitorVisit> checkOut(@PathVariable UUID visitId,
            @Valid @RequestBody TransitionRequest request, HttpServletRequest http) {
        return ApiResponse.ok(checkInOut.checkOut(new VisitorCheckInOutService.Transition(visitId,
                request.expectedVersion(), actors.resolve(http), actors.resolveSourceChannel(http))));
    }

    @PatchMapping("/visits/{visitId}/cancellation")
    @Operation(summary = "Withdraw a visit before arrival, with a reason")
    public ApiResponse<VisitorVisit> cancel(@PathVariable UUID visitId,
            @Valid @RequestBody CancelRequest request, HttpServletRequest http) {
        return ApiResponse.ok(checkInOut.cancel(new VisitorCheckInOutService.CancelVisit(visitId, request.reason(),
                request.expectedVersion(), actors.resolve(http), actors.resolveSourceChannel(http))));
    }

    @GetMapping("/visits")
    @Operation(summary = "Search visits")
    public ApiResponse<List<VisitorVisit>> search(@RequestParam(required = false) String siteCode,
            @RequestParam(required = false) VisitStatus status, @RequestParam(required = false) String hostId,
            @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "100") int limit, HttpServletRequest http) {
        return ApiResponse.ok(registration.search(
                new VisitorRepository.VisitQuery(siteCode, status, hostId, from, to, limit), actors.resolve(http)));
    }

    @GetMapping("/visits/{visitId}")
    @Operation(summary = "Read one visit")
    public ApiResponse<VisitorVisit> findById(@PathVariable UUID visitId, HttpServletRequest http) {
        return ApiResponse.ok(registration.get(visitId, actors.resolve(http)));
    }

    @GetMapping("/roll-call")
    @Operation(summary = "Who is currently on site", description = "SRS-SFL-S160-01 roll-call view.")
    public ApiResponse<List<VisitorVisit>> rollCall(@RequestParam String siteCode, HttpServletRequest http) {
        return ApiResponse.ok(rollCall.rollCall(siteCode, actors.resolve(http)));
    }

    public record PreRegisterRequest(@NotBlank String siteCode, @NotBlank String visitorName,
            String visitorOrganization, String visitorContact, @NotBlank String hostId, String hostName,
            @NotNull VisitPurpose purpose, @NotNull Instant expectedArrival, Instant expectedDeparture) {
    }

    public record DecideRequest(boolean approve, String reason, String watchlistOverrideReason,
            Long expectedVersion) {
    }

    public record AssignBadgeRequest(@NotBlank String badgeNumber, List<String> accessZones, Long expectedVersion) {
    }

    public record TransitionRequest(Long expectedVersion) {
    }

    public record CancelRequest(@NotBlank String reason, Long expectedVersion) {
    }
}
