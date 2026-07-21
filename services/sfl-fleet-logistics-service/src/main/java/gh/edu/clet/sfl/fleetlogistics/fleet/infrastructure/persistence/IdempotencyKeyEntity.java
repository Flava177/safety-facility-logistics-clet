package gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Record of a completed state-creating command, keyed by its {@code Idempotency-Key}. */
@Entity
@Table(name = "fleet_idempotency_keys", schema = "fleet_logistics")
public class IdempotencyKeyEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String operation;

    @Column(name = "idempotency_key", nullable = false, length = 200)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;

    @Column(name = "result_id", nullable = false)
    private UUID resultId;

    @Column(name = "site_code", length = 40)
    private String siteCode;

    @Column(name = "actor_id", nullable = false, length = 160)
    private String actorId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected IdempotencyKeyEntity() {
    }

    public IdempotencyKeyEntity(UUID id, String operation, String idempotencyKey, String requestFingerprint,
            UUID resultId, String siteCode, String actorId, Instant createdAt) {
        this.id = id;
        this.operation = operation;
        this.idempotencyKey = idempotencyKey;
        this.requestFingerprint = requestFingerprint;
        this.resultId = resultId;
        this.siteCode = siteCode;
        this.actorId = actorId;
        this.createdAt = createdAt;
    }

    public String requestFingerprint() {
        return requestFingerprint;
    }

    public UUID resultId() {
        return resultId;
    }
}
