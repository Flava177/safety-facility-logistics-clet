package gh.edu.clet.sfl.facilities.shared.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * The single-row chain head.
 *
 * <p>Writers take a pessimistic row lock here before appending, so two concurrent commands cannot
 * both read sequence <em>n</em> and both claim it. Without this the chain does not break loudly — it
 * breaks on a unique-constraint violation for one writer and a wrong previous-hash link for the
 * other, which replays later as tampering that never happened.
 */
@Entity
@Table(name = "facility_audit_chain_state", schema = "facilities")
class AuditChainStateEntity {

    @Id
    private Short id;
    @Column(name = "head_hash", nullable = false, length = 64)
    private String headHash;
    @Column(name = "next_sequence", nullable = false)
    private long nextSequence;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AuditChainStateEntity() {
    }

    String headHash() {
        return headHash;
    }

    long nextSequence() {
        return nextSequence;
    }

    void advance(String newHeadHash, Instant at) {
        this.headHash = newHeadHash;
        this.nextSequence = this.nextSequence + 1;
        this.updatedAt = at;
    }
}
