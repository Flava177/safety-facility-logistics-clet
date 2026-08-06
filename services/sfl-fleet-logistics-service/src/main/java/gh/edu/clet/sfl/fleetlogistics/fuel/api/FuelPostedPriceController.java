package gh.edu.clet.sfl.fleetlogistics.fuel.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.FleetActorResolver;
import gh.edu.clet.sfl.fleetlogistics.fuel.application.service.FuelApplicationService;
import gh.edu.clet.sfl.fleetlogistics.fuel.domain.model.FuelPostedPrice;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Forecourt prices, and the vendors a site allows.
 *
 * <p>Two reads and one write, serving one purpose: making the price per litre something the platform
 * knows rather than something the driver types. The capture form asks for the vendor list, then for
 * the price in force, and fills the field in. Reconciliation asks the same question again later and
 * judges what was recorded against it.
 *
 * <p>Reading needs only {@code FUEL_TRANSACTION_READ} - anyone who may capture a transaction must be
 * able to see the price they will be held to, and a control nobody can see before they are judged by
 * it is a trap rather than a control. Writing needs {@code FUEL_POLICY_MANAGE}, because a price is a
 * rule and setting your own would defeat the entire mechanism.
 */
@RestController
@RequestMapping("/api/v1/fuel")
@io.swagger.v3.oas.annotations.tags.Tag(name = "Fuel Prices")
public class FuelPostedPriceController {

    private final FuelApplicationService service;
    private final FleetActorResolver actors;

    public FuelPostedPriceController(FuelApplicationService service, FleetActorResolver actors) {
        this.service = service;
        this.actors = actors;
    }

    /**
     * The vendors the site's policy approves right now.
     *
     * <p>An empty list means the in-force policy approves any vendor, or that no policy is in force.
     * Both are worth distinguishing from "no vendors exist", and the caller renders them differently -
     * see the capture dialog.
     */
    @GetMapping("/providers")
    public ApiResponse<List<String>> providers(@RequestParam String siteCode, HttpServletRequest request) {
        return ApiResponse.ok(service.approvedVendors(siteCode, actors.resolve(request)));
    }

    @GetMapping("/posted-prices")
    public ApiResponse<List<FuelPostedPrice>> postedPrices(
            @RequestParam String siteCode,
            @RequestParam(required = false) String vendor,
            @RequestParam(required = false) String fuelProduct,
            @RequestParam(defaultValue = "true") boolean inForceOnly,
            HttpServletRequest request) {
        return ApiResponse.ok(service.postedPrices(siteCode, vendor, fuelProduct, inForceOnly,
                actors.resolve(request)));
    }

    @PostMapping("/posted-prices")
    public ApiResponse<FuelPostedPrice> record(@Valid @RequestBody PostedPriceRequest body,
            HttpServletRequest request) {
        return ApiResponse.ok(service.recordPostedPrice(new FuelApplicationService.RecordPostedPrice(
                body.siteCode(), body.vendor(), body.fuelProduct(), body.unitPrice(), body.currency(),
                body.effectiveFrom(), body.source(), body.notes(), actors.resolve(request),
                actors.resolveSourceChannel(request))));
    }

    /** {@code effectiveFrom} defaults to now, which is what "the price changed today" means. */
    public record PostedPriceRequest(
            @NotBlank String siteCode,
            @NotBlank String vendor,
            @NotBlank String fuelProduct,
            @NotNull @Positive BigDecimal unitPrice,
            @NotBlank String currency,
            Instant effectiveFrom,
            FuelPostedPrice.Source source,
            String notes) {
    }
}
