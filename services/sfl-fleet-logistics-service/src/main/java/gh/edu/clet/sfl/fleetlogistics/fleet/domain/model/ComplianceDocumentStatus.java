package gh.edu.clet.sfl.fleetlogistics.fleet.domain.model;

/**
 * Compliance document state machine (SRS-SFL-S166-01).
 *
 * <p>{@code ACTIVE → EXPIRING → EXPIRED} is date-driven and re-evaluated by the scheduled sweep;
 * {@code SUPERSEDED} is set when a newer document of the same type is registered; {@code REVOKED} is
 * an authorised action. Only one document per (vehicle, type) may be ACTIVE or EXPIRING.
 */
public enum ComplianceDocumentStatus {
    ACTIVE,
    EXPIRING,
    EXPIRED,
    SUPERSEDED,
    REVOKED;

    /** Whether this document still counts as cover for the vehicle. */
    public boolean isCurrent() {
        return this == ACTIVE || this == EXPIRING;
    }
}
