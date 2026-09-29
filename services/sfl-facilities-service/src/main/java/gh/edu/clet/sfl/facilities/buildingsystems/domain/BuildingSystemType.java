package gh.edu.clet.sfl.facilities.buildingsystems.domain;

/**
 * The building systems S156 monitors - SRS-SFL-S156-01 names "HVAC, electrical, water, lift and
 * generator systems and IoT sensors"; the mapping (§30A.6) adds lighting and occupancy.
 *
 * <p>A device belongs to exactly one, and the health rollup (S156-03) is per site, building and system,
 * so this is the middle level of what a Facilities Director reads.
 */
public enum BuildingSystemType {
    HVAC,
    ELECTRICAL,
    LIGHTING,
    WATER,
    LIFT,
    GENERATOR,
    ENVIRONMENTAL,
    OCCUPANCY
}
