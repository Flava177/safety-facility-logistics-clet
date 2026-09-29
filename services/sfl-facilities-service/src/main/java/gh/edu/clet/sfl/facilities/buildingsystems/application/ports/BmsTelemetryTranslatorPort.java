package gh.edu.clet.sfl.facilities.buildingsystems.application.ports;

import gh.edu.clet.sfl.facilities.buildingsystems.application.contract.MeasurementKind;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The integration boundary for BMS/IoT vendors - SRS-SFL-S156-05, NFR-MAINT1.
 *
 * <p>"All vendor specifics live in an infrastructure adapter behind a port/interface." This is that port,
 * and it is the only thing the S156 application knows about a vendor. An adapter takes a message body the
 * verifier has already authenticated and returns readings in SFL's common model: SFL's measurement kinds,
 * SFL's units (°C, kWh, m³), SFL's normalised codes. Fahrenheit, watt-hours, BACnet multistate numbering
 * and vendor field names never cross this line.
 *
 * <p>Adding a second vendor at a second site is one new implementation of this interface, registered as a
 * bean, and a source entry in {@code sfl.facilities.vendor-inbox.sources} - no change to S156's services,
 * to S152 or to S153. {@code BuildingSystemsArchitectureTest} and the S156-05 scenario prove it.
 *
 * <p>An adapter is selected by the {@code format} field every message must carry. Formats are the vendor
 * contract's name and version ({@code sfl-bms-sim/v1}), so a vendor's v2 is a new adapter beside v1
 * rather than a branch inside it.
 */
public interface BmsTelemetryTranslatorPort {

    /** The message format this adapter reads, e.g. {@code sfl-bms-sim/v1}. Unique across adapters. */
    String format();

    /**
     * Translates one authenticated message body into normalised readings.
     *
     * @throws TelemetryTranslationException for a body this adapter cannot read. The caller records it as
     *         a schema rejection through the verifier; nothing from the message is stored or actioned
     */
    List<TranslatedReading> translate(Map<String, Object> payload);

    /**
     * A reading in SFL's common model, before it has been matched to a device.
     *
     * @param deviceCode the identifier the vendor reports the device under
     * @param channel the point on the device, in the vendor's naming - e.g. {@code supply-air-temp}
     */
    record TranslatedReading(String deviceCode, String channel, MeasurementKind kind, BigDecimal value,
            Instant observedAt) {

        public TranslatedReading {
            Objects.requireNonNull(deviceCode, "deviceCode is required");
            Objects.requireNonNull(channel, "channel is required");
            Objects.requireNonNull(kind, "kind is required");
            Objects.requireNonNull(value, "value is required");
            Objects.requireNonNull(observedAt, "observedAt is required");
        }
    }

    /** A body the adapter cannot read. The message is what the vendor got wrong, so it says which field. */
    final class TelemetryTranslationException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public TelemetryTranslationException(String message) {
            super(message);
        }
    }
}
