package gh.edu.clet.sfl.facilities.shared.domain.security;

/**
 * The inbound vendor feeds IFIMP accepts, each behind the one authenticated inbox.
 *
 * <p>A source is registered for exactly one channel. A BMS gateway's credential therefore cannot post a
 * meter reading or an event hand-off, however valid its signature: a stolen key buys one feed, not
 * all three.
 */
public enum VendorChannel {
    /** S156 - HVAC, electrical, water, lift, generator and environmental telemetry. */
    BMS_TELEMETRY,
    /** S157 - utility meter readings from an AMI gateway or vendor billing feed. */
    ENERGY_METERING,
    /** S173 - confirmed-event hand-offs from the CCP Events system (S078). */
    CCP_EVENTS
}
