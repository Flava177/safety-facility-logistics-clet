package gh.edu.clet.sfl.fleetlogistics.fuel.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.FleetActorResolver;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.AuditEvent;
import gh.edu.clet.sfl.fleetlogistics.fuel.application.service.FuelApplicationService;
import gh.edu.clet.sfl.fleetlogistics.fuel.domain.model.FuelReconciliation;
import gh.edu.clet.sfl.fleetlogistics.fuel.domain.model.FuelTransaction;
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
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/fuel/transactions")
@io.swagger.v3.oas.annotations.tags.Tag(name="Fuel Transactions")
public class FuelTransactionController {
    private final FuelApplicationService service;private final FleetActorResolver actors;
    public FuelTransactionController(FuelApplicationService s,FleetActorResolver a){service=s;actors=a;}

    @PostMapping public ResponseEntity<ApiResponse<FuelTransaction>> capture(@Valid @RequestBody TransactionRequest r,HttpServletRequest h){var t=service.capture(command(r,h));return ResponseEntity.created(URI.create("/api/v1/fuel/transactions/"+t.id())).body(ApiResponse.ok(t));}

    /**
     * Paged, filtered transaction register.
     *
     * <p>{@code sourceSystem} and {@code vendorReference} are new: both were client-side filters over
     * whatever window the old unpaged endpoint returned, which meant "manual captures at this site"
     * really meant "manual captures among the first hundred". Vendor is a contains-match.
     */
    @GetMapping public ApiResponse<FuelPageResponse<FuelTransaction>> list(
            @RequestParam String siteCode,
            @RequestParam(required=false)FuelTransaction.Status status,
            @RequestParam(required=false)UUID vehicleId,
            @RequestParam(required=false)UUID driverId,
            @RequestParam(required=false)String sourceSystem,
            @RequestParam(required=false)String vendorReference,
            @RequestParam(required=false)Instant from,
            @RequestParam(required=false)Instant to,
            @RequestParam(defaultValue="0")int page,
            @RequestParam(defaultValue="25")int size,
            @RequestParam(required=false)String sort,
            HttpServletRequest h){
        return ApiResponse.ok(FuelPageResponse.of(service.transactions(siteCode,status,vehicleId,driverId,
                sourceSystem,vendorReference,from,to,FuelPageResponse.paging(page,size,sort),actors.resolve(h))));
    }

    @GetMapping("/{id}") public ApiResponse<FuelTransaction> detail(@PathVariable UUID id,HttpServletRequest h){return ApiResponse.ok(service.transaction(id,actors.resolve(h)));}

    /**
     * Every reconciliation run against the transaction, newest first.
     *
     * <p>The rows have been written since the first release and readable from none of it, so a
     * screen could say a transaction failed but never which rules it passed. Each entry carries the
     * policy version it was judged against and the full per-rule result map.
     */
    @GetMapping("/{id}/reconciliations") @io.swagger.v3.oas.annotations.tags.Tag(name="Fuel Reconciliation")
    public ApiResponse<List<FuelReconciliation>> reconciliations(@PathVariable UUID id,HttpServletRequest h){
        return ApiResponse.ok(service.reconciliations(id,actors.resolve(h)));
    }

    /** The transaction's audit trail — its own slice of the hash-chained log. */
    @GetMapping("/{id}/history") public ApiResponse<List<AuditEvent>> history(@PathVariable UUID id,HttpServletRequest h){
        return ApiResponse.ok(service.history("FuelTransaction",id,actors.resolve(h)));
    }

    @PostMapping("/{id}/reconcile") @io.swagger.v3.oas.annotations.tags.Tag(name="Fuel Reconciliation") public ApiResponse<FuelTransaction> reconcile(@PathVariable UUID id,HttpServletRequest h){return ApiResponse.ok(service.reconcile(id,actors.resolve(h),actors.resolveSourceChannel(h)));}

    @PostMapping("/{id}/void") public ApiResponse<FuelTransaction> voidTransaction(@PathVariable UUID id,@Valid @RequestBody ReasonRequest r,HttpServletRequest h){return ApiResponse.ok(service.voidTransaction(id,r.reason(),actors.resolve(h),actors.resolveSourceChannel(h)));}

    FuelApplicationService.CaptureFuel command(TransactionRequest r,HttpServletRequest h){return new FuelApplicationService.CaptureFuel(r.siteCode(),r.providerTransactionId(),r.sourceSystem(),r.vehicleId(),r.driverId(),r.tripId(),r.occurredAt(),r.vendorReference(),r.stationReference(),r.fuelProduct(),r.quantity(),r.quantityUnit(),r.unitPrice(),r.totalCost(),r.currency(),r.cardReference(),r.odometerReading(),r.receiptEvidenceId(),r.comments(),actors.resolveIdempotencyKey(h),actors.resolve(h),actors.resolveSourceChannel(h));}

    public record TransactionRequest(@NotBlank String siteCode,String providerTransactionId,@NotBlank String sourceSystem,@NotNull UUID vehicleId,@NotNull UUID driverId,UUID tripId,@NotNull Instant occurredAt,@NotBlank String vendorReference,String stationReference,@NotBlank String fuelProduct,@NotNull @Positive BigDecimal quantity,@NotBlank String quantityUnit,@NotNull @PositiveOrZero BigDecimal unitPrice,BigDecimal totalCost,@NotBlank String currency,String cardReference,@PositiveOrZero long odometerReading,UUID receiptEvidenceId,String comments){}
    public record ReasonRequest(@NotBlank String reason){}
}
