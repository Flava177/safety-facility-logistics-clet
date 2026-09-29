package gh.edu.clet.sfl.facilities.energy.domain;

/**
 * The three utilities S157 accounts for, each with its one canonical unit - SRS-SFL-S157-01
 * "Readings are normalised to a common unit set (kWh, m³, litres)".
 *
 * <p>The unit is a property of the utility, not of the reading. A reading arrives in whatever the
 * vendor sends (Wh, kL, US gallons) and is converted once, at the boundary, so nothing downstream -
 * aggregation, budget, tariff, carbon factor - ever has to ask which unit a number is in. A budget of
 * "40 000" for electricity is kWh because electricity is kWh; there is no field that could say otherwise.
 *
 * <p>{@link #unitCode()} is the ASCII form stored in the database and in event payloads ({@code m3});
 * {@link #unitSymbol()} is what a screen shows ({@code m³}).
 */
public enum Utility {
    ELECTRICITY("kWh", "kWh"),
    WATER("m3", "m³"),
    GENERATOR_FUEL("litres", "litres");

    private final String unitCode;
    private final String unitSymbol;

    Utility(String unitCode, String unitSymbol) {
        this.unitCode = unitCode;
        this.unitSymbol = unitSymbol;
    }

    public String unitCode() {
        return unitCode;
    }

    public String unitSymbol() {
        return unitSymbol;
    }
}
