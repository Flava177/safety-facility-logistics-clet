package gh.edu.clet.sfl.facilities.buildingsystems.infrastructure.integration;

import static gh.edu.clet.sfl.facilities.buildingsystems.infrastructure.integration.TelemetryPayloadFields.instant;
import static gh.edu.clet.sfl.facilities.buildingsystems.infrastructure.integration.TelemetryPayloadFields.items;
import static gh.edu.clet.sfl.facilities.buildingsystems.infrastructure.integration.TelemetryPayloadFields.number;
import static gh.edu.clet.sfl.facilities.buildingsystems.infrastructure.integration.TelemetryPayloadFields.text;

import gh.edu.clet.sfl.facilities.buildingsystems.application.contract.MeasurementKind;
import gh.edu.clet.sfl.facilities.buildingsystems.application.ports.BmsTelemetryTranslatorPort;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * The shipped S156 adapter: SFL's own BMS simulator format, {@code sfl-bms-sim/v1}.
 *
 * <p>This is a <strong>simulated adapter</strong>. No BMS/IoT vendor has passed the SRS §5.2 procurement
 * gate (CORR-07); {@code GET /api/v1/facilities/vendor-integrations} and the S156 health view both report
 * {@code SIMULATED_ADAPTER_ONLY}, and nothing should describe the feed as integrated. It exists so the
 * whole S156 pipeline - authentication, normalisation, quarantine, rules, work orders, escalation - runs
 * end to end today, and so a real vendor's adapter has a working reference beside it.
 *
 * <p>The shape is deliberately already in SFL's common model, so it tests the pipeline rather than a
 * conversion:
 *
 * <pre>{@code
 * {
 *   "messageType": "bms.telemetry",
 *   "idempotencyKey": "sim-000123",
 *   "siteCode": "MAIN",
 *   "format": "sfl-bms-sim/v1",
 *   "readings": [
 *     {"deviceId": "AHU-01", "channel": "supply-air-temp", "kind": "TEMPERATURE_C",
 *      "value": 21.5, "observedAt": "2026-09-28T07:59:00Z"}
 *   ]
 * }
 * }</pre>
 *
 * <p>{@code kind} is a {@link MeasurementKind} name and {@code value} is already in that kind's unit.
 */
@Component
public class SimulatedBmsTelemetryAdapter implements BmsTelemetryTranslatorPort {

    public static final String FORMAT = "sfl-bms-sim/v1";

    @Override
    public String format() {
        return FORMAT;
    }

    @Override
    public List<TranslatedReading> translate(Map<String, Object> payload) {
        List<TranslatedReading> readings = new ArrayList<>();
        List<Map<String, Object>> entries = items(payload, "readings");
        for (int index = 0; index < entries.size(); index++) {
            Map<String, Object> entry = entries.get(index);
            String kindName = text(entry, "kind", index).toUpperCase(Locale.ROOT);
            MeasurementKind kind;
            try {
                kind = MeasurementKind.valueOf(kindName);
            } catch (IllegalArgumentException unknown) {
                throw new TelemetryTranslationException("reading " + index + " has unknown kind '" + kindName + "'");
            }
            readings.add(new TranslatedReading(text(entry, "deviceId", index), text(entry, "channel", index), kind,
                    number(entry, "value", index), instant(entry, "observedAt", index)));
        }
        return readings;
    }
}
