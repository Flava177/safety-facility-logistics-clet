package gh.edu.clet.sfl.fleetlogistics.fleet.domain.model;

/**
 * Unit of an odometer reading. Ghana operates in kilometres; miles exist only so an imported record
 * is never silently reinterpreted as kilometres.
 */
public enum DistanceUnit {
    KILOMETRES,
    MILES
}
