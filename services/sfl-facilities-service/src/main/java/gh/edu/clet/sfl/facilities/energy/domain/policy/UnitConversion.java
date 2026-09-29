package gh.edu.clet.sfl.facilities.energy.domain.policy;

import gh.edu.clet.sfl.facilities.energy.domain.Utility;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Optional;

/**
 * Conversion to the canonical unit of each utility - SRS-SFL-S157-01 "normalised to a common unit set
 * (kWh, m³, litres)".
 *
 * <p>Keyed by utility <em>and</em> unit, because the same unit means different things per utility: a
 * litre of water is 0.001 m³, a litre of diesel is a litre. A single unit table would silently convert
 * a fuel feed reported in litres into thousandths.
 *
 * <p>The unit names here are SFL's normalised spellings. A vendor's own vocabulary ("gallons", "GAL_US",
 * "m³") is mapped onto these in that vendor's adapter, so a second vendor with different spellings needs
 * a new adapter and no change here (SRS 2.6: no domain dependence on a vendor API).
 */
public final class UnitConversion {

    private static final Map<Utility, Map<String, BigDecimal>> FACTORS = Map.of(
            Utility.ELECTRICITY, Map.of(
                    "Wh", new BigDecimal("0.001"),
                    "kWh", BigDecimal.ONE,
                    "MWh", new BigDecimal("1000")),
            Utility.WATER, Map.of(
                    "m3", BigDecimal.ONE,
                    "kL", BigDecimal.ONE,
                    "L", new BigDecimal("0.001"),
                    "USgal", new BigDecimal("0.003785411784"),
                    "impgal", new BigDecimal("0.00454609")),
            Utility.GENERATOR_FUEL, Map.of(
                    "L", BigDecimal.ONE,
                    "m3", new BigDecimal("1000"),
                    "USgal", new BigDecimal("3.785411784"),
                    "impgal", new BigDecimal("4.54609")));

    private UnitConversion() {
    }

    /**
     * The value in the utility's canonical unit, to four decimal places, or empty when {@code unit} is not
     * a unit of that utility - a kWh figure on a water meter is a mapping defect, not a number to guess at.
     */
    public static Optional<BigDecimal> toCanonical(Utility utility, String unit, BigDecimal value) {
        if (utility == null || unit == null || value == null) {
            return Optional.empty();
        }
        BigDecimal factor = FACTORS.getOrDefault(utility, Map.of()).get(unit);
        return factor == null ? Optional.empty()
                : Optional.of(value.multiply(factor).setScale(4, RoundingMode.HALF_UP));
    }

    /** The normalised unit names accepted for a utility, for error messages. */
    public static java.util.Set<String> unitsOf(Utility utility) {
        return new java.util.TreeSet<>(FACTORS.getOrDefault(utility, Map.of()).keySet());
    }
}
