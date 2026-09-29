package gh.edu.clet.sfl.facilities.energy.domain;

import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One meter's consumption on one UTC day - the consumption record SRS-SFL-S157-01's workflow ends in
 * ("aggregated into consumption record -> available to budget and dashboard").
 *
 * <p>A reading is attributed wholly to the day it was observed. A monthly manual read therefore lands a
 * month's consumption on one day rather than spreading it: pro-rating would invent a daily profile
 * nobody measured, and the monthly totals budgets are set against come out the same either way. The
 * consequence - a manual meter's daily figures are lumpy, and the anomaly check skips manual meters for
 * that reason - is recorded in the gap report.
 *
 * @param readingCount readings received for the day, baseline reads included - the "received" half of
 *        S157-03's completeness indicator
 */
public record DailyConsumption(
        UUID id,
        String siteCode,
        String buildingCode,
        UUID meterId,
        Utility utility,
        LocalDate day,
        BigDecimal consumption,
        int readingCount,
        RecordMetadata metadata) {

    public static DailyConsumption start(UUID id, EnergyMeter meter, LocalDate day, String actorId, Instant at,
            SourceChannel channel, String correlationId) {
        return new DailyConsumption(id, meter.siteCode(), meter.buildingCode(), meter.id(), meter.utility(), day,
                BigDecimal.ZERO, 0, RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    /** Adds one posted reading. A {@code null} consumption (a baseline read) counts as received and adds nothing. */
    public DailyConsumption add(BigDecimal readingConsumption, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        return new DailyConsumption(id, siteCode, buildingCode, meterId, utility, day,
                readingConsumption == null ? consumption : consumption.add(readingConsumption), readingCount + 1,
                metadata.version() == 0 && readingCount == 0 ? metadata
                        : metadata.modifiedBy(actorId, at, channel, correlationId));
    }
}
