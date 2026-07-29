package gh.edu.clet.sfl.fleetlogistics.fuel.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.FleetActorResolver;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.AuditEvent;
import gh.edu.clet.sfl.fleetlogistics.fuel.application.service.FuelApplicationService;
import gh.edu.clet.sfl.fleetlogistics.fuel.domain.model.FuelPolicy;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController @RequestMapping("/api/v1/fuel/policies")
@io.swagger.v3.oas.annotations.tags.Tag(name="Fuel Policies")
public class FuelPolicyController {
    private final FuelApplicationService service; private final FleetActorResolver actors;
    public FuelPolicyController(FuelApplicationService s,FleetActorResolver a){service=s;actors=a;}

    /**
     * Creates a policy, refused with {@code FUEL_POLICY_PERIOD_OVERLAP} when an active policy for
     * the site already covers part of the same period.
     */
    @PostMapping public ResponseEntity<ApiResponse<FuelPolicy>> create(@Valid @RequestBody PolicyRequest r,HttpServletRequest h){var p=service.createPolicy(new FuelApplicationService.CreatePolicy(r.siteCode(),r.name(),r.effectiveFrom(),r.effectiveTo(),r.policyVersion(),r.maxPerTransaction(),r.dailyLimit(),r.monthlyLimit(),r.tankCapacity(),r.minConsumption(),r.maxConsumption(),r.odometerJumpTolerance(),r.receiptRequired(),r.receiptGraceHours(),r.materialityAmount(),r.anomalySlaHours(),r.allowedFuelProducts(),r.approvedVendors(),actors.resolve(h),actors.resolveSourceChannel(h)));return ResponseEntity.created(URI.create("/api/v1/fuel/policies/"+p.id())).body(ApiResponse.ok(p));}

    /**
     * Paged policy register.
     *
     * <p>{@code inForceOnly} is an interval test rather than a status filter: an ACTIVE policy whose
     * period has not started is not in force, and one with no end date runs until superseded. That
     * distinction is the whole point of an effective-dated policy and it was not expressible before.
     */
    @GetMapping public ApiResponse<FuelPageResponse<FuelPolicy>> list(
            @RequestParam String siteCode,
            @RequestParam(required=false)FuelPolicy.Status status,
            @RequestParam(defaultValue="false")boolean inForceOnly,
            @RequestParam(defaultValue="0")int page,
            @RequestParam(defaultValue="25")int size,
            @RequestParam(required=false)String sort,
            HttpServletRequest h){
        return ApiResponse.ok(FuelPageResponse.of(service.policies(siteCode,status,inForceOnly,
                FuelPageResponse.paging(page,size,sort),actors.resolve(h))));
    }

    @GetMapping("/{id}") public ApiResponse<FuelPolicy> detail(@PathVariable UUID id,HttpServletRequest h){
        return ApiResponse.ok(service.policy(id,actors.resolve(h)));
    }

    @GetMapping("/{id}/history") public ApiResponse<List<AuditEvent>> history(@PathVariable UUID id,HttpServletRequest h){
        return ApiResponse.ok(service.history("FuelPolicy",id,actors.resolve(h)));
    }

    public record PolicyRequest(@NotBlank String siteCode,@NotBlank String name,@NotNull Instant effectiveFrom,Instant effectiveTo,@Positive int policyVersion,@NotNull @Positive BigDecimal maxPerTransaction,BigDecimal dailyLimit,BigDecimal monthlyLimit,BigDecimal tankCapacity,BigDecimal minConsumption,BigDecimal maxConsumption,@PositiveOrZero long odometerJumpTolerance,boolean receiptRequired,@PositiveOrZero int receiptGraceHours,@NotNull @PositiveOrZero BigDecimal materialityAmount,@Positive int anomalySlaHours,Set<String> allowedFuelProducts,Set<String> approvedVendors){}
}
