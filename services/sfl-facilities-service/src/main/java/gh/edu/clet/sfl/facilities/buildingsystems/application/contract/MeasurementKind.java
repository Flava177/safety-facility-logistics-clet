package gh.edu.clet.sfl.facilities.buildingsystems.application.contract;

/**
 * What a normalised S156 reading measures, in SFL's common reading model (SRS-SFL-S156-01).
 *
 * <p>Part of the published contract because S157 energy consumes the stream (S157-04) and decides from
 * this alone whether a reading is its business. The unit is fixed per kind - a vendor that reports Wh
 * or gallons is converted in its adapter - so a consumer never has to ask "which unit is this".
 */
public enum MeasurementKind {
    TEMPERATURE_C("°C", false),
    RELATIVE_HUMIDITY_PCT("%", false),
    CO2_PPM("ppm", false),
    /** 1 = supply present, 0 = lost. The input to "total power loss". */
    POWER_STATE("state", false),
    ELECTRICAL_ENERGY_KWH("kWh", true),
    WATER_VOLUME_M3("m³", true),
    /** Generator fuel consumed over the reading interval. */
    GENERATOR_FUEL_LITRES("litres", true),
    FUEL_TANK_LEVEL_PCT("%", false),
    /** Vendor-normalised lift state; a positive entrapment code is a critical fault (S156-03). */
    LIFT_STATUS("code", false),
    /** Generator run state: 0 stopped, 1 running, -1 failed start. */
    GENERATOR_RUN_STATE("state", false),
    FAULT_CODE("code", false);

    private final String unit;
    private final boolean energyRelevant;

    MeasurementKind(String unit, boolean energyRelevant) {
        this.unit = unit;
        this.energyRelevant = energyRelevant;
    }

    public String unit() {
        return unit;
    }

    /** {@code true} for the consumption kinds S157 accounts for - kWh, m³, litres of generator fuel. */
    public boolean energyRelevant() {
        return energyRelevant;
    }
}
