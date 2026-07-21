package gh.edu.clet.sfl.fleetlogistics.fleet.domain.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * Restricted-use rules for a vehicle: emergency-only vehicles and operating-mode restrictions.
 *
 * <p>The readiness policy turns a breach of these rules into the {@code EMERGENCY_ONLY_RESTRICTION} or
 * {@code OPERATING_MODE_RESTRICTION} blocker rather than a silent failure, so a dispatcher is told
 * exactly why an ambulance is not available for a routine campus run.
 */
public record RestrictedUse(boolean emergencyOnly, Set<OperatingMode> allowedOperatingModes) {

    public RestrictedUse {
        allowedOperatingModes = allowedOperatingModes == null || allowedOperatingModes.isEmpty()
                ? Set.copyOf(EnumSet.allOf(OperatingMode.class))
                : Set.copyOf(allowedOperatingModes);
    }

    /** No restriction: usable in every operating mode. */
    public static RestrictedUse unrestricted() {
        return new RestrictedUse(false, EnumSet.allOf(OperatingMode.class));
    }

    /** Named {@code forEmergencyUseOnly} because {@code emergencyOnly()} is the record accessor. */
    public static RestrictedUse forEmergencyUseOnly() {
        return new RestrictedUse(true, EnumSet.of(OperatingMode.EMERGENCY));
    }

    public static RestrictedUse limitedTo(Set<OperatingMode> modes) {
        return new RestrictedUse(false, modes);
    }

    public boolean permits(OperatingMode mode) {
        return mode != null && allowedOperatingModes.contains(mode);
    }

    /** True when an emergency-only vehicle has been requested for something other than an emergency. */
    public boolean violatesEmergencyOnlyRule(OperatingMode mode) {
        return emergencyOnly && mode != OperatingMode.EMERGENCY;
    }
}
