package gh.edu.clet.sfl.fleetlogistics.fuel.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.FleetActorResolver;
import gh.edu.clet.sfl.fleetlogistics.fuel.application.port.FuelRepository;
import gh.edu.clet.sfl.fleetlogistics.fuel.application.service.FuelCardService;
import gh.edu.clet.sfl.fleetlogistics.fuel.domain.model.FuelCard;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The fuel-card register — SRS-SFL-S168fuel-04.
 *
 * <p>Only the masked reference the provider already sends is accepted or returned. A full card number
 * is payment data, this platform has no business holding one, and the C9 mapping puts the card platform
 * outside SFL.
 */
@RestController
@RequestMapping("/api/v1/fuel/cards")
@io.swagger.v3.oas.annotations.tags.Tag(name = "Fuel Cards")
public class FuelCardController {

    private final FuelCardService service;
    private final FleetActorResolver actors;

    public FuelCardController(FuelCardService service, FleetActorResolver actors) {
        this.service = service;
        this.actors = actors;
    }

    /** Issue a card. Manager-only: a fuel card is a payment instrument. */
    @PostMapping
    public ResponseEntity<ApiResponse<FuelCard>> issue(@Valid @RequestBody IssueRequest request,
            HttpServletRequest http) {
        FuelCard card = service.issue(new FuelCardService.IssueCard(request.siteCode(),
                request.maskedReference(), request.provider(), request.vehicleId(), request.driverId(),
                request.issuedOn(), request.expiresOn(), request.dailyLimit(), request.monthlyLimit(),
                request.perTransactionLimit(), request.notes(), actors.resolve(http),
                actors.resolveSourceChannel(http)));
        return ResponseEntity.created(URI.create("/api/v1/fuel/cards/" + card.id()))
                .body(ApiResponse.ok(card));
    }

    /** assign · suspend · reinstate · cancel. */
    @PostMapping("/{id}/{action:assign|suspend|reinstate|cancel}")
    public ApiResponse<FuelCard> transition(@PathVariable UUID id, @PathVariable String action,
            @RequestBody(required = false) TransitionRequest request, HttpServletRequest http) {
        return ApiResponse.ok(service.transition(new FuelCardService.TransitionCard(id, action,
                request == null ? null : request.reason(),
                request == null ? null : request.vehicleId(),
                request == null ? null : request.driverId(),
                actors.resolve(http), actors.resolveSourceChannel(http))));
    }

    @GetMapping("/{id}")
    public ApiResponse<FuelCard> detail(@PathVariable UUID id, HttpServletRequest http) {
        return ApiResponse.ok(service.card(id, actors.resolve(http)));
    }

    @GetMapping
    public ApiResponse<FuelPageResponse<FuelCard>> list(
            @RequestParam String siteCode,
            @RequestParam(required = false) FuelCard.Status status,
            @RequestParam(required = false) UUID vehicleId,
            @RequestParam(required = false) UUID driverId,
            @RequestParam(required = false) String maskedReference,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String sort,
            HttpServletRequest http) {
        return ApiResponse.ok(FuelPageResponse.of(service.cards(siteCode, status, vehicleId, driverId,
                maskedReference, new FuelRepository.Paging(page, size, sort), actors.resolve(http))));
    }

    public record IssueRequest(
            @NotBlank String siteCode,
            /** The masked form the provider sends, e.g. {@code ****1234}. Never the full number. */
            @NotBlank String maskedReference,
            @NotBlank String provider,
            UUID vehicleId,
            UUID driverId,
            LocalDate issuedOn,
            LocalDate expiresOn,
            @Positive BigDecimal dailyLimit,
            @Positive BigDecimal monthlyLimit,
            @Positive BigDecimal perTransactionLimit,
            String notes) {
    }

    public record TransitionRequest(String reason, UUID vehicleId, UUID driverId) {
    }
}
