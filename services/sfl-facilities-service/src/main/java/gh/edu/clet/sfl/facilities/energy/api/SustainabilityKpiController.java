package gh.edu.clet.sfl.facilities.energy.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.energy.application.EnergyCommands;
import gh.edu.clet.sfl.facilities.energy.application.SustainabilityKpiService;
import gh.edu.clet.sfl.facilities.energy.application.ports.EnergyRepository;
import gh.edu.clet.sfl.facilities.energy.domain.EnergyPeriod;
import gh.edu.clet.sfl.facilities.energy.domain.SustainabilityKpi;
import gh.edu.clet.sfl.facilities.energy.domain.Utility;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

/**
 * Sustainability KPIs - the S225 Analytics read model - and emission factors (SRS-SFL-S157-03).
 *
 * <p>{@code GET /kpis} is the read half of the S225 contract; {@code sfl.ifimp.sustainability-kpi-published.v1}
 * is the event half. {@code siteCode=*} reads the cluster-wide rows, which only a cross-site caller may see.
 */
@RestController
@RequestMapping("/api/v1/facilities/energy")
@Tag(name = "S157 Sustainability KPIs", description = "KPI read model for S225 Analytics, emission factors")
public class SustainabilityKpiController {

    private final SustainabilityKpiService kpis;

    public SustainabilityKpiController(SustainabilityKpiService kpis) {
        this.kpis = kpis;
    }

    @GetMapping("/kpis")
    @Operation(summary = "Sustainability KPIs",
            description = "Every KPI carries its period, periodClosed, expected/received readings and a "
                    + "completeness flag (COMPLETE / PARTIAL / LOW). LOW is published, never withheld.")
    public ApiResponse<List<EnergyResponses.KpiResponse>> list(@RequestParam(required = false) String siteCode,
            @RequestParam(required = false) SustainabilityKpi.KpiScope scope,
            @RequestParam(required = false) Utility utility,
            @RequestParam(required = false) EnergyPeriod.PeriodType periodType,
            @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "500") int limit, ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(kpis.kpis(new EnergyRepository.KpiQuery(siteCode, scope, utility, periodType, from, to,
                limit), actor, channel).stream().map(EnergyResponses.KpiResponse::from).toList());
    }

    @PostMapping("/kpis/computation")
    @Operation(summary = "Compute and publish KPIs now",
            description = "What the scheduled sweep does. Cluster rows only for a cross-site caller.")
    public ApiResponse<List<EnergyResponses.KpiResponse>> compute(ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(kpis.computeAndPublish(actor, channel).stream().map(EnergyResponses.KpiResponse::from)
                .toList());
    }

    @PostMapping("/emission-factors")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an emission-factor version", description = "kg CO2e per canonical unit.")
    public ApiResponse<EnergyResponses.EmissionFactorResponse> createFactor(
            @Valid @RequestBody EnergyRequests.CreateEmissionFactor request, ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(EnergyResponses.EmissionFactorResponse.from(kpis.createEmissionFactor(
                new EnergyCommands.CreateEmissionFactor(request.siteCode(), request.utility(), request.kgCo2ePerUnit(),
                        request.validFrom(), request.sourceReference(), actor, channel))));
    }

    @GetMapping("/emission-factors")
    @Operation(summary = "Emission-factor versions at a site")
    public ApiResponse<List<EnergyResponses.EmissionFactorResponse>> factors(@RequestParam String siteCode,
            ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(kpis.emissionFactors(siteCode, actor, channel).stream()
                .map(EnergyResponses.EmissionFactorResponse::from).toList());
    }
}
