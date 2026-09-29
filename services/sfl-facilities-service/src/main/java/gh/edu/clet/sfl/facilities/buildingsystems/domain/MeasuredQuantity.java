package gh.edu.clet.sfl.facilities.buildingsystems.domain;

/**
 * What a stored S156 reading measures, in the domain's own vocabulary - SRS-SFL-S156-01.
 *
 * <p>Constant-for-constant the same as the published
 * {@code buildingsystems.application.contract.MeasurementKind}, and deliberately a second enum rather
 * than a reuse of it. The contract lives in {@code application} because S157 consumes it, and the
 * house rule is that {@code domain} never depends on {@code application}. Duplicating eleven names is
 * the cheaper side of that trade: {@code MeasuredQuantityParityTest} fails the build the moment the two
 * drift, so a kind added to the contract without a domain meaning cannot slip through.
 *
 * <p>The unit is fixed per quantity (see the contract's {@code unit()}): a vendor adapter converts
 * Fahrenheit, watt-hours or gallons before a value ever reaches the domain, so no rule or plausibility
 * band here has to ask which unit it is looking at.
 */
public enum MeasuredQuantity {
    TEMPERATURE_C,
    RELATIVE_HUMIDITY_PCT,
    CO2_PPM,
    /** 1 = supply present, 0 = lost. */
    POWER_STATE,
    ELECTRICAL_ENERGY_KWH,
    WATER_VOLUME_M3,
    GENERATOR_FUEL_LITRES,
    FUEL_TANK_LEVEL_PCT,
    /** 0 normal, 1 out of service, 2 fault, 3 entrapment - the normalised S156 lift code set. */
    LIFT_STATUS,
    /** 0 stopped, 1 running, -1 failed start. */
    GENERATOR_RUN_STATE,
    FAULT_CODE;

    /** The configuration-key spelling: {@code TEMPERATURE_C} becomes {@code temperature-c}. */
    public String configurationKey() {
        return name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
    }
}
