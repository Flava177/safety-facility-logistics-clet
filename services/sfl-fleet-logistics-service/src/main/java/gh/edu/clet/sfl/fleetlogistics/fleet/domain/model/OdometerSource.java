package gh.edu.clet.sfl.fleetlogistics.fleet.domain.model;

/**
 * Provenance of an odometer reading (SRS-SFL-S166-01 "current odometer with provenance").
 *
 * <p>Provenance matters because readings arrive from several places with different trust levels, and
 * a readiness decision should say which one it relied on.
 */
public enum OdometerSource {
    /** Read from the dashboard by a driver or officer. */
    MANUAL_ENTRY,
    /** Captured on a pre- or post-trip inspection. */
    INSPECTION,
    /** Recorded by a service provider at a service event. */
    SERVICE_RECORD,
    /** Reported by the telematics device. */
    TELEMATICS,
    /** Reported alongside a fuel transaction (S168_fuel seam). */
    FUEL_TRANSACTION,
    /** Written by an authorised correction workflow with a reason and evidence. */
    AUTHORISED_CORRECTION,
    /** Loaded during data migration. */
    MIGRATION
}
