package gh.edu.clet.sfl.facilities.energy.infrastructure.integration;

import gh.edu.clet.sfl.facilities.energy.application.ports.MeteringVendorPort;
import gh.edu.clet.sfl.facilities.energy.domain.Utility;
import gh.edu.clet.sfl.facilities.energy.domain.policy.UnitConversion;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * The simulated AMI gateway / billing-feed adapter - the only {@link MeteringVendorPort} shipped, and the
 * name {@code application.yml} and the procurement-gate register report ({@code sfl.facilities.vendor-
 * integrations.energy-metering.adapter}). No vendor has been selected (SRS §5.2, CORR-07).
 *
 * <p>It defines a plausible generic interval message rather than imitating any product:
 *
 * <pre>{"meterRef":"AMI-MAIN-001","value":"1250","unit":"Wh",
 *  "intervalStart":"2026-09-28T07:00:00Z","intervalEnd":"2026-09-28T08:00:00Z"}</pre>
 *
 * <p>What makes it an adapter and not a pass-through is the vendor vocabulary it absorbs: unit spellings
 * such as {@code "gallons"}, {@code "GAL_US"}, {@code "litres"}, {@code "m³"} and {@code "KWH"} are mapped to
 * SFL's normalised units, then {@link UnitConversion} takes them to the meter's canonical unit. A procured
 * product gets its own adapter with its own mapping; the application and domain do not change.
 */
@Component
public class SimulatedMeteringAdapter implements MeteringVendorPort {

    static final String NAME = "SimulatedMeteringAdapter";

    /** Vendor spellings, lower-cased, onto SFL's normalised unit names. Gallons are US unless stated. */
    private static final Map<String, String> UNIT_SPELLINGS = Map.ofEntries(
            Map.entry("wh", "Wh"), Map.entry("kwh", "kWh"), Map.entry("mwh", "MWh"),
            Map.entry("m3", "m3"), Map.entry("m³", "m3"), Map.entry("cubic_metre", "m3"),
            Map.entry("kl", "kL"), Map.entry("kilolitre", "kL"),
            Map.entry("l", "L"), Map.entry("litre", "L"), Map.entry("litres", "L"), Map.entry("liter", "L"),
            Map.entry("liters", "L"),
            Map.entry("gal", "USgal"), Map.entry("gallon", "USgal"), Map.entry("gallons", "USgal"),
            Map.entry("gal_us", "USgal"), Map.entry("usgal", "USgal"),
            Map.entry("gal_imp", "impgal"), Map.entry("impgal", "impgal"));

    @Override
    public String adapterName() {
        return NAME;
    }

    @Override
    public List<String> requiredFields() {
        return List.of("meterRef", "value", "unit", "intervalEnd");
    }

    @Override
    public String meterReference(Map<String, Object> payload) {
        Object reference = payload.get("meterRef");
        if (reference == null || String.valueOf(reference).isBlank()) {
            throw new UntranslatableMessageException("Missing meterRef.");
        }
        return String.valueOf(reference).strip();
    }

    @Override
    public MeterInterval translate(Map<String, Object> payload, Utility utility) {
        BigDecimal value;
        try {
            value = new BigDecimal(String.valueOf(payload.get("value")).strip());
        } catch (NumberFormatException notNumeric) {
            throw new UntranslatableMessageException("value is not a number.");
        }
        if (value.signum() < 0) {
            throw new UntranslatableMessageException("Interval consumption cannot be negative.");
        }
        String vendorUnit = String.valueOf(payload.get("unit")).strip();
        String normalised = UNIT_SPELLINGS.get(vendorUnit.toLowerCase(Locale.ROOT));
        BigDecimal canonical = normalised == null ? null
                : UnitConversion.toCanonical(utility, normalised, value).orElse(null);
        if (canonical == null) {
            throw new UntranslatableMessageException("Unit '" + vendorUnit + "' is not a unit of " + utility
                    + " (accepted: " + UnitConversion.unitsOf(utility) + ").");
        }
        Instant end = instant(payload.get("intervalEnd"), "intervalEnd");
        Instant start = payload.get("intervalStart") == null ? null : instant(payload.get("intervalStart"),
                "intervalStart");
        if (start != null && !start.isBefore(end)) {
            throw new UntranslatableMessageException("intervalStart must be before intervalEnd.");
        }
        return new MeterInterval(start, end, canonical, value, vendorUnit);
    }

    private static Instant instant(Object value, String field) {
        try {
            return Instant.parse(String.valueOf(value).strip());
        } catch (DateTimeParseException unreadable) {
            throw new UntranslatableMessageException(field + " is not an ISO-8601 instant.");
        }
    }
}
