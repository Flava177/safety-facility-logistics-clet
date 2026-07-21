package gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.audit;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.AuditHashChain;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * The single-row head of the audit hash chain.
 *
 * <p>Appenders lock this row pessimistically before allocating a sequence number, which is what stops
 * two concurrent commands from computing hashes against the same predecessor and silently forking the
 * chain.
 */
@Entity
@Table(name = "fleet_audit_chain_state", schema = "fleet_logistics")
public class AuditChainStateEntity {

    /** There is exactly one chain, and it always has this id. */
    public static final short SINGLETON_ID = 1;

    @Id
    private short id;

    @Column(name = "head_hash", nullable = false, length = 64)
    private String headHash;

    @Column(name = "next_sequence", nullable = false)
    private long nextSequence;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AuditChainStateEntity() {
    }

    public String headHash() {
        return headHash == null || headHash.isBlank() ? AuditHashChain.GENESIS_HASH : headHash;
    }

    public long nextSequence() {
        return nextSequence;
    }

    public void advance(String newHeadHash, Instant now) {
        this.headHash = newHeadHash;
        this.nextSequence = this.nextSequence + 1;
        this.updatedAt = now;
    }
}
