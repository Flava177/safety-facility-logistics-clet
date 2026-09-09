package gh.edu.clet.sfl.facilities.shared.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

/**
 * The real, database-enforced half of {@code record_version}.
 *
 * <p>JPA does not allow {@code @Version} on a property of an {@code @Embeddable} - Hibernate rejects
 * the mapping outright at boot ("Embedded class ... may not have a property annotated '@Version'").
 * {@link RecordMetadataEmbeddable} carries the other six SRS-SFL-S152-01 provenance columns as an
 * embeddable for exactly the reason its own Javadoc gives; this one field is split out into a mapped
 * superclass instead, so every entity that needs it inherits one real optimistic lock rather than each
 * declaring the field itself.
 *
 * <p>{@code @Version} governs the actual {@code UPDATE ... WHERE record_version = ?}, on top of - not
 * instead of - {@code RecordMetadata.requireVersion}'s explicit, friendlier If-Match check. The two
 * are complementary: {@code requireVersion} rejects a write built on a version the caller explicitly
 * named as stale before touching the database at all; this closes the gap that check cannot -
 * two concurrent writers who both read the same version and both pass that check, only one of which
 * may actually win. See {@code FacilitiesApiExceptionHandler#optimisticLock}, which already existed
 * and already translated {@code OptimisticLockingFailureException} to {@code VERSION_CONFLICT} - it
 * was reachable code with nothing that could throw it until this.
 *
 * <h2>Why an adapter reuses the managed row instead of merging a detached one</h2>
 *
 * <p>By the time a command reaches the adapter, the domain object it holds has already had
 * {@code RecordMetadata.modifiedBy} called on it - so its version is already one past what the edit
 * actually read. Handing that straight to {@code repository.save(EntityRecord.from(domain))} merges a
 * detached instance whose version Hibernate has never seen justified against the database, and every
 * write - not just a genuinely stale one - fails with a spurious {@code StaleObjectStateException}
 * (verified directly: the very first, entirely sequential update already threw it before this existed).
 *
 * <p>The adapter instead fetches the row it already has a session for, calls
 * {@link #requireNotStale(long)} on it, and only then applies the domain object's other fields onto
 * it - never onto {@code recordVersion} - and saves that same managed instance. Hibernate's own
 * dirty-checking issues the version-guarded {@code UPDATE} and increments the version itself.
 */
@MappedSuperclass
public abstract class VersionedRecord {

    @Version
    @Column(name = "record_version", nullable = false)
    private long recordVersion;

    public long recordVersion() {
        return recordVersion;
    }

    protected void recordVersion(long value) {
        this.recordVersion = value;
    }

    /**
     * Rejects reusing this managed row for an update whose edit was not actually based on the row's
     * current state - the check {@link #recordVersion} being a real JPA {@code @Version} cannot make
     * on its own, because reusing the row on its own just fetches whatever is current and would
     * silently apply a stale edit on top of it.
     *
     * <p>{@code editBasisVersion} is the version already on the incoming domain object, which is
     * <em>usually</em> one past what the edit actually read - see the class Javadoc -
     * because {@code RecordMetadata.modifiedBy} always increments by 1. A handful of mutators
     * deliberately do not call {@code modifiedBy} at all (e.g. {@code Booking.withReadinessHold},
     * {@code FacilityFault.withBlockerRaised}): they mirror a decision another module made rather than
     * record an edit somebody performed, and bumping the version for them would make a routine
     * reconciliation sweep collide with any concurrent, real edit of the same row. For those,
     * {@code editBasisVersion} equals the version the row was actually read at, unchanged. Both shapes
     * are accepted here; only a basis that matches neither is a genuinely stale write.
     */
    public void requireNotStale(long editBasisVersion) {
        if (recordVersion != editBasisVersion - 1 && recordVersion != editBasisVersion) {
            throw new ObjectOptimisticLockingFailureException(getClass(), null);
        }
    }
}
