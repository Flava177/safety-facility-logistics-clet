package gh.edu.clet.sfl.facilities.energy.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.energy.domain.MeterSource;
import gh.edu.clet.sfl.facilities.energy.domain.Utility;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** The S157 commands. Each carries its actor and channel, so the audit record needs nothing else. */
public final class EnergyCommands {

    private EnergyCommands() {
    }

    /**
     * @param expectedIntervalMinutes {@code null} for the configured default of the source
     */
    public record RegisterMeter(String siteCode, String buildingCode, UUID roomId, String meterCode, String name,
            Utility utility, MeterSource source, String avampAssetId, String vendorMeterRef,
            Integer expectedIntervalMinutes, ActorContext actor, SourceChannel channel, String idempotencyKey,
            Object idempotencyPayload) {
    }

    /** @param source {@code null} to keep; only {@code BMS_STREAM} is accepted as a change */
    public record UpdateMeter(UUID meterId, String name, Integer expectedIntervalMinutes, MeterSource source,
            Long expectedVersion, ActorContext actor, SourceChannel channel) {
    }

    public record RetireMeter(UUID meterId, String reason, Long expectedVersion, ActorContext actor,
            SourceChannel channel) {
    }

    /** A register value read off the meter by a person (S157-01 manual entry). */
    public record EnterManualReading(UUID meterId, BigDecimal registerValue, Instant readAt, String note,
            ActorContext actor, SourceChannel channel, String idempotencyKey, Object idempotencyPayload) {
    }

    /**
     * @param consumption optional; required only when the register went backwards and the verifier is
     *        posting a replaced or rolled-over meter's consumption
     */
    public record DecideHeldReading(UUID readingId, boolean approve, BigDecimal consumption, String note,
            ActorContext actor, SourceChannel channel) {
    }

    /** One energy-relevant S156 reading, already translated out of S156's contract by the observer. */
    public record StreamReading(UUID streamReadingId, UUID deviceId, String avampAssetId, String siteCode,
            Utility utility, BigDecimal value, Instant observedAt) {
    }

    public record CreateBudget(String siteCode, Utility utility, LocalDate periodStart, BigDecimal consumptionBudget,
            BigDecimal costBudget, String currency, String reason, ActorContext actor, SourceChannel channel) {
    }

    public record CreateTariff(String siteCode, Utility utility, BigDecimal unitRate, String currency,
            LocalDate validFrom, LocalDate validTo, String reason, ActorContext actor, SourceChannel channel) {
    }

    public record CreateEmissionFactor(String siteCode, Utility utility, BigDecimal kgCo2ePerUnit,
            LocalDate validFrom, String sourceReference, ActorContext actor, SourceChannel channel) {
    }

    /** @param utility {@code null} to close every utility with consumption or a budget at the site */
    public record ClosePeriod(String siteCode, Utility utility, LocalDate periodStart, ActorContext actor,
            SourceChannel channel) {
    }
}
