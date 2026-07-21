package gh.edu.clet.sfl.fleetlogistics.fleet.domain.model;

/**
 * Vehicle record lifecycle (SRS-SFL-S166-01: "Records shall support active, inactive, suspended and
 * archived lifecycle states where applicable").
 *
 * <p>Transitions are governed by {@code VehicleLifecyclePolicy}; the status is never set directly.
 */
public enum VehicleLifecycleStatus {
    /** In the operational fleet and eligible for assignment. */
    ACTIVE,
    /** Retained in the register but not in operational use; may be reactivated. */
    INACTIVE,
    /** Withdrawn pending an investigation or compliance action; reinstatement is privileged. */
    SUSPENDED,
    /** Retired from the register. Read-only; only an authorised restoration workflow can bring it back. */
    ARCHIVED;

    public boolean isEditable() {
        return this != ARCHIVED;
    }

    public boolean isOperational() {
        return this == ACTIVE;
    }
}
