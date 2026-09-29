package gh.edu.clet.sfl.facilities.energy.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import gh.edu.clet.sfl.facilities.energy.domain.Utility;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class UnitConversionTest {

    @Test
    void watt_hours_convert_to_kilowatt_hours() {
        assertThat(UnitConversion.toCanonical(Utility.ELECTRICITY, "Wh", new BigDecimal("1500")))
                .contains(new BigDecimal("1.5000"));
    }

    @Test
    void litres_of_water_convert_to_cubic_metres() {
        assertThat(UnitConversion.toCanonical(Utility.WATER, "L", new BigDecimal("2500")))
                .contains(new BigDecimal("2.5000"));
    }

    @Test
    void a_litre_of_fuel_is_already_canonical() {
        assertThat(UnitConversion.toCanonical(Utility.GENERATOR_FUEL, "L", new BigDecimal("40")))
                .contains(new BigDecimal("40.0000"));
    }

    @Test
    void a_unit_of_the_wrong_utility_is_not_convertible() {
        // kWh is not a water unit - a mapping defect, not a number to guess at.
        assertThat(UnitConversion.toCanonical(Utility.WATER, "kWh", BigDecimal.TEN)).isEmpty();
    }

    @Test
    void an_unknown_unit_is_not_convertible() {
        assertThat(UnitConversion.toCanonical(Utility.ELECTRICITY, "furlongs", BigDecimal.ONE)).isEmpty();
    }
}
