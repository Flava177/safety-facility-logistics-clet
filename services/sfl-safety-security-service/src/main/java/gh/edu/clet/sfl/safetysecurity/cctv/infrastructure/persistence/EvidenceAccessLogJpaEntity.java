package gh.edu.clet.sfl.safetysecurity.cctv.infrastructure.persistence;

import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.EvidenceAccessAction;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.EvidenceAccessLog;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** JPA mapping for {@link EvidenceAccessLog} - append-only, so no {@code @Version} field, matching
 * {@code AccessEventJpaEntity}'s reasoning for its own append-only rows. */
@Entity
@Table(name = "cctv_evidence_access_log", schema = "safety_security")
public class EvidenceAccessLogJpaEntity {

    @Id
    private UUID id;
    @Column(name = "evidence_item_id", nullable = false)
    private UUID evidenceItemId;
    @Column(name = "actor_id", nullable = false, length = 160)
    private String actorId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EvidenceAccessAction action;
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected EvidenceAccessLogJpaEntity() {
    }

    public static EvidenceAccessLogJpaEntity from(EvidenceAccessLog log) {
        EvidenceAccessLogJpaEntity entity = new EvidenceAccessLogJpaEntity();
        entity.id = log.id();
        entity.evidenceItemId = log.evidenceItemId();
        entity.actorId = log.actorId();
        entity.action = log.action();
        entity.occurredAt = log.occurredAt();
        return entity;
    }

    public EvidenceAccessLog toDomain() {
        return new EvidenceAccessLog(id, evidenceItemId, actorId, action, occurredAt);
    }

    public UUID getId() {
        return id;
    }
}
