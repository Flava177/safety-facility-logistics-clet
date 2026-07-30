package gh.edu.clet.sfl.facilities.readiness.domain;

/**
 * How much an open readiness blocker matters.
 *
 * <p>Severity is the input to the one rule this system exists to enforce: <strong>a space cannot be
 * marked READY while a critical blocker is open</strong> (SRS-SFL-S152-01, -05). Everything else about
 * readiness is reporting; this is the control.
 *
 * <p>Declared most severe first, so natural ordering matches how an operator reads a queue.
 */
public enum BlockerSeverity {

    /** Stops use of the space outright. Forces {@code BLOCKED}; READY is refused while one is open. */
    CRITICAL,
    /** Materially impairs use. Forces {@code DEGRADED}. */
    MAJOR,
    /** A real defect that does not stop use. Forces {@code DEGRADED}. */
    MINOR,
    /** Noted for information — a scuffed wall, a missing sign. Does not affect status. */
    ADVISORY;

    /** {@code true} when an open blocker of this severity forbids {@code READY}. */
    public boolean blocksReadiness() {
        return this == CRITICAL;
    }

    /** {@code true} when an open blocker of this severity degrades but does not block. */
    public boolean degradesReadiness() {
        return this == MAJOR || this == MINOR;
    }
}
