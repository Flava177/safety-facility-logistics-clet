package gh.edu.clet.sfl.facilities.energy.support;

import gh.edu.clet.sfl.facilities.energy.application.ports.MeteringVendorPort;
import gh.edu.clet.sfl.facilities.energy.domain.Utility;
import gh.edu.clet.sfl.facilities.energy.domain.policy.UnitConversion;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Mirrors {@code SimulatedMeteringAdapter} closely enough for a test to build a payload by hand. */
public final class FakeMeteringVendorAdapter implements MeteringVendorPort {

    @Override
    public String adapterName() {
        return "FakeMeteringVendorAdapter";
    }

    @Override
    public List<String> requiredFields() {
        return List.of("meterRef", "value", "unit", "intervalEnd");
    }

    @Override
    public String meterReference(Map<String, Object> payload) {
        Object reference = payload.get("meterRef");
        if (reference == null) {
            throw new UntranslatableMessageException("Missing meterRef.");
        }
        return String.valueOf(reference);
    }

    @Override
    public MeterInterval translate(Map<String, Object> payload, Utility utility) {
        BigDecimal value;
        try {
            value = new BigDecimal(String.valueOf(payload.get("value")));
        } catch (NumberFormatException notNumeric) {
            throw new UntranslatableMessageException("value is not a number.");
        }
        String unit = String.valueOf(payload.get("unit"));
        BigDecimal canonical = UnitConversion.toCanonical(utility, unit, value)
                .orElseThrow(() -> new UntranslatableMessageException("Unit '" + unit + "' is not a unit of " + utility));
        Instant end = Instant.parse(String.valueOf(payload.get("intervalEnd")));
        Instant start = payload.get("intervalStart") == null ? null
                : Instant.parse(String.valueOf(payload.get("intervalStart")));
        return new MeterInterval(start, end, canonical, value, unit);
    }
}
