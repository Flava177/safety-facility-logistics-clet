package gh.edu.clet.sfl.facilities.energy.api;

import gh.edu.clet.sfl.facilities.energy.domain.MeterSource;
import gh.edu.clet.sfl.facilities.energy.domain.Utility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** S157 request bodies. Bean validation catches shape; the services own every business rule. */
public final class EnergyRequests {

    private EnergyRequests() {
    }

    public record RegisterMeter(
            @NotBlank @Size(max = 40) String siteCode,
            @NotBlank @Size(max = 40) String buildingCode,
            UUID roomId,
            @NotBlank @Size(max = 80) String meterCode,
            @NotBlank @Size(max = 200) String name,
            @NotNull Utility utility,
            @NotNull MeterSource source,
            @Size(max = 120) String avampAssetId,
            @Size(max = 120) String vendorMeterRef,
            @Positive Integer expectedIntervalMinutes) {
    }

    public record UpdateMeter(@Size(max = 200) String name, @Positive Integer expectedIntervalMinutes,
            MeterSource source, Long expectedVersion) {
    }

    public record RetireMeter(@NotBlank @Size(max = 1000) String reason, Long expectedVersion) {
    }

    public record ManualReading(@NotNull UUID meterId, @NotNull @PositiveOrZero BigDecimal registerValue,
            Instant readAt, @Size(max = 1000) String note) {
    }

    public record Verification(@NotNull Boolean approve, @PositiveOrZero BigDecimal consumption,
            @Size(max = 1000) String note) {
    }

    public record CreateBudget(@NotBlank String siteCode, @NotNull Utility utility, @NotNull LocalDate periodStart,
            @NotNull @Positive BigDecimal consumptionBudget, @Positive BigDecimal costBudget,
            @Size(min = 3, max = 3) String currency, @Size(max = 1000) String reason) {
    }

    public record CreateTariff(@NotBlank String siteCode, @NotNull Utility utility,
            @NotNull @Positive BigDecimal unitRate, @NotBlank @Size(min = 3, max = 3) String currency,
            @NotNull LocalDate validFrom, LocalDate validTo, @Size(max = 1000) String reason) {
    }

    public record CreateEmissionFactor(@NotBlank String siteCode, @NotNull Utility utility,
            @NotNull @PositiveOrZero BigDecimal kgCo2ePerUnit, @NotNull LocalDate validFrom,
            @Size(max = 500) String sourceReference) {
    }

    public record ClosePeriod(@NotBlank String siteCode, Utility utility, @NotNull LocalDate periodStart) {
    }

    public record EvaluateAnomalies(String siteCode, LocalDate day) {
    }
}
