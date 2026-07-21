package gh.edu.clet.sfl.fleetlogistics.fleet.domain.model;

/**
 * Trip (vehicle/driver assignment) lifecycle (SRS-SFL-S166-02).
 *
 * <pre>
 *   PLANNED ─assign─► ASSIGNED ─start─► IN_PROGRESS ─close─► COMPLETED
 *      │                 │  ▲              │  ▲
 *      │                 │  └── resume ────┘  │
 *      │                 ▼                    ▼
 *      └── cancel ──► CANCELLED ◄── cancel ── ON_HOLD
 * </pre>
 */
public enum TripStatus {
    PLANNED,
    ASSIGNED,
    IN_PROGRESS,
    ON_HOLD,
    COMPLETED,
    CANCELLED;

    /** Terminal statuses accept no further transition. */
    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED;
    }

    /** Statuses that hold a vehicle and a driver against the requested period. */
    public boolean holdsAssignment() {
        return this == PLANNED || this == ASSIGNED || this == IN_PROGRESS || this == ON_HOLD;
    }
}
