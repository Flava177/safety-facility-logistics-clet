package gh.edu.clet.sfl.facilities.energy.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.energy.application.EnergyCommands;
import gh.edu.clet.sfl.facilities.energy.application.EnergyVarianceService;
import gh.edu.clet.sfl.facilities.energy.domain.EnergyAlert;
import gh.edu.clet.sfl.facilities.energy.domain.Utility;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Budgets, tariffs, period close, variance, cost and alerts - SRS-SFL-S157-02. */
@RestController
@RequestMapping("/api/v1/facilities/energy")
@Tag(name = "S157 Energy budgets", description = "Versioned budgets and tariffs, period close, variance and anomaly alerts")
public class EnergyBudgetController {

    private final EnergyVarianceService variance;

    public EnergyBudgetController(EnergyVarianceService variance) {
        this.variance = variance;
    }

    @PostMapping("/budgets")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a budget version for a site/utility/month",
            description = "Versioned: a new version never alters a closed period's variance.")
    public ApiResponse<EnergyResponses.BudgetResponse> createBudget(
            @Valid @RequestBody EnergyRequests.CreateBudget request, ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(EnergyResponses.BudgetResponse.from(variance.createBudget(new EnergyCommands.CreateBudget(
                request.siteCode(), request.utility(), request.periodStart(), request.consumptionBudget(),
                request.costBudget(), request.currency(), request.reason(), actor, channel))));
    }

    @GetMapping("/budgets")
    @Operation(summary = "Budget versions at a site")
    public ApiResponse<List<EnergyResponses.BudgetResponse>> budgets(@RequestParam String siteCode,
            @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
            ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(variance.budgets(siteCode, from, to, actor, channel).stream()
                .map(EnergyResponses.BudgetResponse::from).toList());
    }

    @PostMapping("/tariffs")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a tariff version for a site/utility", description = "A zero rate is refused.")
    public ApiResponse<EnergyResponses.TariffResponse> createTariff(
            @Valid @RequestBody EnergyRequests.CreateTariff request, ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(EnergyResponses.TariffResponse.from(variance.createTariff(new EnergyCommands.CreateTariff(
                request.siteCode(), request.utility(), request.unitRate(), request.currency(), request.validFrom(),
                request.validTo(), request.reason(), actor, channel))));
    }

    @GetMapping("/tariffs")
    @Operation(summary = "Tariff versions at a site")
    public ApiResponse<List<EnergyResponses.TariffResponse>> tariffs(@RequestParam String siteCode,
            @RequestParam(required = false) Utility utility, ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(variance.tariffs(siteCode, utility, actor, channel).stream()
                .map(EnergyResponses.TariffResponse::from).toList());
    }

    @PostMapping("/periods/close")
    @Operation(summary = "Close an ended month",
            description = "Freezes budget-versus-actual with the budget and tariff versions used, and raises a "
                    + "variance alert naming site and utility beyond the threshold. Idempotent.")
    public ApiResponse<List<EnergyResponses.VarianceResponse>> close(
            @Valid @RequestBody EnergyRequests.ClosePeriod request, ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(variance.closePeriod(new EnergyCommands.ClosePeriod(request.siteCode(), request.utility(),
                request.periodStart(), actor, channel)).stream()
                .map(result -> EnergyResponses.VarianceResponse.from(result, true)).toList());
    }

    @GetMapping("/periods")
    @Operation(summary = "Closed periods at a site")
    public ApiResponse<List<EnergyResponses.VarianceResponse>> closedPeriods(@RequestParam String siteCode,
            @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
            ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(variance.closedPeriods(siteCode, from, to, actor, channel).stream()
                .map(result -> EnergyResponses.VarianceResponse.from(result, true)).toList());
    }

    @GetMapping("/variance")
    @Operation(summary = "Budget versus actual for a month",
            description = "The frozen result once closed; otherwise a provisional figure (closed=false).")
    public ApiResponse<EnergyResponses.VarianceResponse> variance(@RequestParam String siteCode,
            @RequestParam Utility utility, @RequestParam(required = false) LocalDate periodStart, ActorContext actor,
            SourceChannel channel) {
        return ApiResponse.ok(EnergyResponses.VarianceResponse.from(variance.variance(siteCode, utility, periodStart,
                actor, channel)));
    }

    @GetMapping("/cost")
    @Operation(summary = "Cost of a month's consumption at its tariff",
            description = "Consumption with no tariff is 422 ENERGY_TARIFF_MISSING - never a zero cost.")
    public ApiResponse<EnergyResponses.CostResponse> cost(@RequestParam String siteCode, @RequestParam Utility utility,
            @RequestParam(required = false) LocalDate periodStart, ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(EnergyResponses.CostResponse.from(variance.cost(siteCode, utility, periodStart, actor,
                channel)));
    }

    @GetMapping("/alerts")
    @Operation(summary = "Variance, anomaly and missing-tariff alerts", description = "Newest first, with drill-down.")
    public ApiResponse<List<EnergyResponses.AlertResponse>> alerts(@RequestParam(required = false) String siteCode,
            @RequestParam(required = false) EnergyAlert.EnergyAlertType type,
            @RequestParam(required = false) Instant since, @RequestParam(defaultValue = "100") int limit,
            ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(variance.alerts(siteCode, type, since, limit, actor, channel).stream()
                .map(EnergyResponses.AlertResponse::from).toList());
    }

    @PostMapping("/anomalies/evaluation")
    @Operation(summary = "Judge a completed day for consumption spikes",
            description = "What the hourly sweep does, on demand. Raised independently of budget.")
    public ApiResponse<List<EnergyResponses.AlertResponse>> evaluate(
            @RequestBody(required = false) EnergyRequests.EvaluateAnomalies request, ActorContext actor,
            SourceChannel channel) {
        return ApiResponse.ok(variance.evaluateAnomalies(request == null ? null : request.day(),
                request == null ? null : request.siteCode(), actor, channel).stream()
                .map(EnergyResponses.AlertResponse::from).toList());
    }
}
