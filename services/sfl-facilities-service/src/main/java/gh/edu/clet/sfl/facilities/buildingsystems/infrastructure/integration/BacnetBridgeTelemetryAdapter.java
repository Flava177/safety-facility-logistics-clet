package gh.edu.clet.sfl.facilities.buildingsystems.infrastructure.integration;

import static gh.edu.clet.sfl.facilities.buildingsystems.infrastructure.integration.TelemetryPayloadFields.epochMillis;
import static gh.edu.clet.sfl.facilities.buildingsystems.infrastructure.integration.TelemetryPayloadFields.items;
import static gh.edu.clet.sfl.facilities.buildingsystems.infrastructure.integration.TelemetryPayloadFields.number;
import static gh.edu.clet.sfl.facilities.buildingsystems.infrastructure.integration.TelemetryPayloadFields.text;

import gh.edu.clet.sfl.facilities.buildingsystems.application.contract.MeasurementKind;
import gh.edu.clet.sfl.facilities.buildingsystems.application.ports.BmsTelemetryTranslatorPort;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * A second, differently shaped vendor: a BACnet/IP gateway publishing change-of-value notifications over an
 * MQTT bridge, relayed to SFL as JSON - format {@code bacnet-mqtt-bridge/v2}. SRS-SFL-S156-05.
 *
 * <p>Simulated, like {@link SimulatedBmsTelemetryAdapter}: no product is procured. It exists to prove the
 * S156-05 acceptance criterion with a shape that genuinely differs from SFL's model - different field names,
 * epoch-millisecond timestamps, imperial and non-SI units, BACnet's 1-based multistate numbering - and to
 * show that every one of those differences is absorbed here, with nothing changed in S156, S152 or S153.
 * No BACnet or MQTT library is used or needed: the bridge has already decoded the protocol, which is the
 * integration posture S156 takes ("SFL integrates rather than builds the sensing and control layer").
 *
 * <pre>{@code
 * {
 *   "messageType": "bacnet.cov", "idempotencyKey": "gw7-1727510400-42", "siteCode": "KSI",
 *   "format": "bacnet-mqtt-bridge/v2", "gateway": "KSI-GW-01",
 *   "points": [
 *     {"deviceInstance": "AHU-K1", "objectName": "ZN-T", "engineeringUnits": "degrees-fahrenheit",
 *      "presentValue": 71.6, "timestamp": 1727510340000},
 *     {"deviceInstance": "LIFT-K1", "objectName": "CAR-STATE", "pointClass": "lift-state",
 *      "presentValue": 4, "timestamp": 1727510340000}
 *   ]
 * }
 * }</pre>
 *
 * <h2>Conversions</h2>
 *
 * <ul>
 *   <li>Analog points by {@code engineeringUnits}: degrees-fahrenheit → °C, degrees-celsius, degrees-kelvin →
 *       °C, percent-relative-humidity, parts-per-million, watt-hours → kWh, kilowatt-hours, us-gallons → m³,
 *       cubic-meters.</li>
 *   <li>Binary and multistate points by the gateway's {@code pointClass} tag, because a BACnet unit does not
 *       say what a state means: {@code mains-supply} (active/inactive → 1/0), {@code lift-state}
 *       (multistate 1 in-service, 2 out-of-service, 3 fault, 4 passenger-trapped → SFL codes 0-3),
 *       {@code genset-state} (1 stopped, 2 running, 3 fail-to-start → 0, 1, -1), {@code alarm-code},
 *       {@code tank-level} (percent), {@code fuel-used} (liters).</li>
 * </ul>
 *
 * <p>An unknown unit or point class is a schema rejection, never a guess: a Fahrenheit value stored as
 * Celsius would breach every server-room rule in the estate.
 */
@Component
public class BacnetBridgeTelemetryAdapter implements BmsTelemetryTranslatorPort {

    public static final String FORMAT = "bacnet-mqtt-bridge/v2";

    private static final BigDecimal THIRTY_TWO = BigDecimal.valueOf(32);
    private static final BigDecimal FIVE_NINTHS = BigDecimal.valueOf(5).divide(BigDecimal.valueOf(9), 12,
            RoundingMode.HALF_UP);
    private static final BigDecimal KELVIN_OFFSET = new BigDecimal("273.15");
    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);
    private static final BigDecimal US_GALLON_M3 = new BigDecimal("0.003785411784");

    @Override
    public String format() {
        return FORMAT;
    }

    @Override
    public List<TranslatedReading> translate(Map<String, Object> payload) {
        List<TranslatedReading> readings = new ArrayList<>();
        List<Map<String, Object>> points = items(payload, "points");
        for (int index = 0; index < points.size(); index++) {
            Map<String, Object> point = points.get(index);
            String device = text(point, "deviceInstance", index);
            String channel = text(point, "objectName", index);
            Instant observedAt = epochMillis(point, "timestamp", index);
            Object pointClass = point.get("pointClass");
            readings.add(pointClass == null || String.valueOf(pointClass).isBlank()
                    ? analog(device, channel, observedAt, point, index)
                    : classified(device, channel, observedAt, String.valueOf(pointClass).strip(), point, index));
        }
        return readings;
    }

    private static TranslatedReading analog(String device, String channel, Instant observedAt,
            Map<String, Object> point, int index) {
        String units = text(point, "engineeringUnits", index).toLowerCase(Locale.ROOT);
        BigDecimal value = number(point, "presentValue", index);
        return switch (units) {
            case "degrees-fahrenheit" -> reading(device, channel, MeasurementKind.TEMPERATURE_C,
                    value.subtract(THIRTY_TWO).multiply(FIVE_NINTHS).setScale(2, RoundingMode.HALF_UP), observedAt);
            case "degrees-celsius" -> reading(device, channel, MeasurementKind.TEMPERATURE_C, value, observedAt);
            case "degrees-kelvin" -> reading(device, channel, MeasurementKind.TEMPERATURE_C,
                    value.subtract(KELVIN_OFFSET).setScale(2, RoundingMode.HALF_UP), observedAt);
            case "percent-relative-humidity" -> reading(device, channel, MeasurementKind.RELATIVE_HUMIDITY_PCT, value,
                    observedAt);
            case "parts-per-million" -> reading(device, channel, MeasurementKind.CO2_PPM, value, observedAt);
            case "watt-hours" -> reading(device, channel, MeasurementKind.ELECTRICAL_ENERGY_KWH,
                    value.divide(THOUSAND, 6, RoundingMode.HALF_UP), observedAt);
            case "kilowatt-hours" -> reading(device, channel, MeasurementKind.ELECTRICAL_ENERGY_KWH, value, observedAt);
            case "us-gallons" -> reading(device, channel, MeasurementKind.WATER_VOLUME_M3,
                    value.multiply(US_GALLON_M3).setScale(6, RoundingMode.HALF_UP), observedAt);
            case "cubic-meters" -> reading(device, channel, MeasurementKind.WATER_VOLUME_M3, value, observedAt);
            default -> throw new TelemetryTranslationException("point " + index + " has unsupported engineeringUnits '"
                    + units + "'");
        };
    }

    private static TranslatedReading classified(String device, String channel, Instant observedAt, String pointClass,
            Map<String, Object> point, int index) {
        return switch (pointClass.toLowerCase(Locale.ROOT)) {
            case "mains-supply" -> reading(device, channel, MeasurementKind.POWER_STATE, binary(point, index),
                    observedAt);
            case "lift-state" -> reading(device, channel, MeasurementKind.LIFT_STATUS,
                    multistate(point, index, 4).subtract(BigDecimal.ONE), observedAt);
            case "genset-state" -> reading(device, channel, MeasurementKind.GENERATOR_RUN_STATE,
                    genset(multistate(point, index, 3).intValue()), observedAt);
            case "alarm-code" -> reading(device, channel, MeasurementKind.FAULT_CODE,
                    number(point, "presentValue", index), observedAt);
            case "tank-level" -> reading(device, channel, MeasurementKind.FUEL_TANK_LEVEL_PCT,
                    number(point, "presentValue", index), observedAt);
            case "fuel-used" -> reading(device, channel, MeasurementKind.GENERATOR_FUEL_LITRES,
                    number(point, "presentValue", index), observedAt);
            default -> throw new TelemetryTranslationException("point " + index + " has unsupported pointClass '"
                    + pointClass + "'");
        };
    }

    /** BACnet binary present-value: "active" / "inactive", or 1 / 0. */
    private static BigDecimal binary(Map<String, Object> point, int index) {
        String raw = text(point, "presentValue", index).toLowerCase(Locale.ROOT);
        return switch (raw) {
            case "active", "1", "1.0", "true" -> BigDecimal.ONE;
            case "inactive", "0", "0.0", "false" -> BigDecimal.ZERO;
            default -> throw new TelemetryTranslationException("point " + index + " binary presentValue '" + raw
                    + "' is neither active nor inactive");
        };
    }

    /** BACnet multistate values are 1-based; anything outside 1..states is a malformed point. */
    private static BigDecimal multistate(Map<String, Object> point, int index, int states) {
        BigDecimal value = number(point, "presentValue", index);
        try {
            int state = value.intValueExact();
            if (state < 1 || state > states) {
                throw new TelemetryTranslationException("point " + index + " multistate value " + state
                        + " is outside 1.." + states);
            }
            return BigDecimal.valueOf(state);
        } catch (ArithmeticException notWhole) {
            throw new TelemetryTranslationException("point " + index + " multistate value must be a whole number");
        }
    }

    private static BigDecimal genset(int state) {
        return switch (state) {
            case 1 -> BigDecimal.ZERO;
            case 2 -> BigDecimal.ONE;
            default -> BigDecimal.valueOf(-1);
        };
    }

    private static TranslatedReading reading(String device, String channel, MeasurementKind kind, BigDecimal value,
            Instant observedAt) {
        return new TranslatedReading(device, channel, kind, value, observedAt);
    }
}
