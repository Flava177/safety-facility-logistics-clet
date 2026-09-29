package gh.edu.clet.sfl.facilities.energy.domain;

/**
 * How a meter's readings reach S157, and therefore what a reading from it means.
 *
 * <h2>Cumulative register or interval consumption - a decision the SRS does not make</h2>
 *
 * SRS-SFL-S157-01 says readings are ingested, normalised and aggregated; it never says whether a
 * "reading" is the number on the dial (a cumulative register) or the quantity consumed since the last
 * one. Getting this wrong in either direction is silent and large - summing register reads reports a
 * year's consumption every month; differencing interval figures reports noise. So it is fixed per
 * source here and recorded in the gap report rather than guessed per reading:
 *
 * <ul>
 *   <li>{@link #MANUAL} - a person reads the <strong>register</strong>. Consumption is the delta from
 *       the previous posted register read on the same meter. The first read on a meter is a baseline and
 *       posts no consumption. A register lower than the previous one (rollover, replaced meter, typo) is
 *       held for verification like any other implausible value.</li>
 *   <li>{@link #AMI} - the vendor gateway / billing feed sends <strong>interval consumption</strong>
 *       (quantity over {@code intervalStart..intervalEnd}). This is how AMI head-ends and billing exports
 *       conventionally publish; a vendor that only exposes registers must be differenced in its adapter,
 *       before the reading reaches this module.</li>
 *   <li>{@link #BMS_STREAM} - S156's normalised stream. The contract documents
 *       {@code GENERATOR_FUEL_LITRES} as "consumed over the reading interval"; the same is assumed for
 *       {@code ELECTRICAL_ENERGY_KWH} and {@code WATER_VOLUME_M3}, and flagged, because the S156 contract
 *       does not say so for those two kinds.</li>
 * </ul>
 */
public enum MeterSource {
    MANUAL,
    AMI,
    BMS_STREAM;

    /** {@code true} when a reading is a register value and consumption is its delta. */
    public boolean readsCumulativeRegister() {
        return this == MANUAL;
    }

    /**
     * {@code true} when the meter must carry an AVAMP identity. A smart meter is a tracked physical
     * device; an old dial meter on a manual walk may not have been tagged yet.
     */
    public boolean requiresAssetIdentity() {
        return this != MANUAL;
    }
}
