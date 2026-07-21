package gh.edu.clet.sfl.fleetlogistics.fleet.domain.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * Ghana DVLA driving licence classes and the vehicle categories each one covers.
 *
 * <p>Modelling the coverage here rather than in a service is what lets the eligibility policy answer
 * "may this driver take this vehicle?" without a lookup table that can drift out of step.
 */
public enum LicenceClass {

    /** Motorcycles. */
    A(EnumSet.of(VehicleCategory.MOTORCYCLE)),
    /** Light vehicles up to 3,000 kg. */
    B(EnumSet.of(VehicleCategory.SALOON_CAR, VehicleCategory.PICKUP, VehicleCategory.FOUR_WHEEL_DRIVE,
            VehicleCategory.UTILITY)),
    /** Light vehicles plus minibuses and ambulances. */
    C(EnumSet.of(VehicleCategory.SALOON_CAR, VehicleCategory.PICKUP, VehicleCategory.FOUR_WHEEL_DRIVE,
            VehicleCategory.UTILITY, VehicleCategory.MINIBUS, VehicleCategory.AMBULANCE)),
    /** Buses. */
    D(EnumSet.of(VehicleCategory.SALOON_CAR, VehicleCategory.PICKUP, VehicleCategory.FOUR_WHEEL_DRIVE,
            VehicleCategory.UTILITY, VehicleCategory.MINIBUS, VehicleCategory.AMBULANCE, VehicleCategory.BUS)),
    /** Heavy goods vehicles. */
    E(EnumSet.allOf(VehicleCategory.class));

    private final Set<VehicleCategory> coveredCategories;

    LicenceClass(Set<VehicleCategory> coveredCategories) {
        this.coveredCategories = Set.copyOf(coveredCategories);
    }

    public boolean covers(VehicleCategory category) {
        return category != null && coveredCategories.contains(category);
    }

    public Set<VehicleCategory> coveredCategories() {
        return coveredCategories;
    }
}
