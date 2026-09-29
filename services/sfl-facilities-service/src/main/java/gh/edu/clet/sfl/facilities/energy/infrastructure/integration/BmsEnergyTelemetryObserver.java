package gh.edu.clet.sfl.facilities.energy.infrastructure.integration;

import gh.edu.clet.sfl.facilities.buildingsystems.application.contract.BuildingTelemetryObserver;
import gh.edu.clet.sfl.facilities.buildingsystems.application.contract.MeasurementKind;
import gh.edu.clet.sfl.facilities.buildingsystems.application.contract.NormalisedTelemetryReading;
import gh.edu.clet.sfl.facilities.energy.application.EnergyCommands;
import gh.edu.clet.sfl.facilities.energy.application.EnergyReadingService;
import gh.edu.clet.sfl.facilities.energy.domain.Utility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * S157 on S156's normalised stream - SRS-SFL-S157-04 "consumes the existing normalised stream rather than
 * opening a second vendor connection".
 *
 * <p>S156 calls this for every accepted reading, in S156's transaction. Non-energy kinds (temperature,
 * lift status) are dropped here without touching the database; energy kinds are translated out of S156's
 * contract types into an S157 command, so the application layer never names another module's types.
 *
 * <p>Nothing here throws back into S156 for a reason S156 could not have prevented - an unknown device, a
 * mismatched meter. Those are decided and audited inside {@link EnergyReadingService#consumeStream}. A
 * genuine persistence failure does propagate: the observer shares S156's transaction by contract, and a
 * reading that S156 accepted but S157 silently failed to record would be a gap nobody could see.
 */
@Component
public class BmsEnergyTelemetryObserver implements BuildingTelemetryObserver {

    private static final Logger log = LoggerFactory.getLogger(BmsEnergyTelemetryObserver.class);

    private final EnergyReadingService readings;

    public BmsEnergyTelemetryObserver(EnergyReadingService readings) {
        this.readings = readings;
    }

    @Override
    public void readingAccepted(NormalisedTelemetryReading reading) {
        if (reading == null || reading.kind() == null || !reading.kind().energyRelevant()) {
            return;
        }
        Utility utility = utilityOf(reading.kind());
        EnergyReadingService.StreamOutcome outcome = readings.consumeStream(new EnergyCommands.StreamReading(
                reading.readingId(), reading.deviceId(), reading.avampAssetId(), reading.siteCode(), utility,
                reading.value(), reading.observedAt()));
        if (outcome == EnergyReadingService.StreamOutcome.REFUSED) {
            log.warn("S157 refused S156 reading {} for AVAMP asset {}; see ENERGY_TELEMETRY_REJECTED in the audit trail",
                    reading.readingId(), reading.avampAssetId());
        }
    }

    /** The three energy kinds of the S156 contract, onto S157's utilities. The units already agree. */
    static Utility utilityOf(MeasurementKind kind) {
        return switch (kind) {
            case ELECTRICAL_ENERGY_KWH -> Utility.ELECTRICITY;
            case WATER_VOLUME_M3 -> Utility.WATER;
            case GENERATOR_FUEL_LITRES -> Utility.GENERATOR_FUEL;
            default -> throw new IllegalArgumentException(kind + " is not an energy kind");
        };
    }
}
