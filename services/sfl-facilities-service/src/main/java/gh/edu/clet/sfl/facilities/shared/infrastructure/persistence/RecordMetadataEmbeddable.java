package gh.edu.clet.sfl.facilities.shared.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.Instant;

/**
 * The system-managed fields, mapped once and embedded in every estate entity.
 *
 * <p>Seven columns that SRS-SFL-S152-01 requires on every operational record. Declaring them in one
 * {@code @Embeddable} rather than copying them into seven entities is what stops the set drifting —
 * an entity that forgot {@code correlation_id} would produce audit records nobody can trace, and the
 * omission would be invisible in review.
 *
 * <p>The column names match V6 exactly. {@code record_version} rather than {@code version} because
 * {@code version} is a reserved-ish word in enough tooling to be worth avoiding, and because this is
 * an application-managed optimistic lock rather than a JPA {@code @Version} — the domain increments
 * it through {@link RecordMetadata#modifiedBy}, so a change cannot happen without the version moving.
 */
@Embeddable
public class RecordMetadataEmbeddable {

    @Column(name = "created_by", nullable = false, length = 160)
    private String createdBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "last_modified_by", nullable = false, length = 160)
    private String lastModifiedBy;
    @Column(name = "last_modified_at", nullable = false)
    private Instant lastModifiedAt;
    @Column(name = "record_version", nullable = false)
    private long recordVersion;
    @Enumerated(EnumType.STRING)
    @Column(name = "source_channel", nullable = false, length = 40)
    private SourceChannel sourceChannel;
    @Column(name = "correlation_id", length = 120)
    private String correlationId;

    protected RecordMetadataEmbeddable() {
    }

    public static RecordMetadataEmbeddable from(RecordMetadata metadata) {
        RecordMetadataEmbeddable embeddable = new RecordMetadataEmbeddable();
        embeddable.createdBy = metadata.createdBy();
        embeddable.createdAt = metadata.createdAt();
        embeddable.lastModifiedBy = metadata.lastModifiedBy();
        embeddable.lastModifiedAt = metadata.lastModifiedAt();
        embeddable.recordVersion = metadata.version();
        embeddable.sourceChannel = metadata.sourceChannel();
        embeddable.correlationId = metadata.correlationId();
        return embeddable;
    }

    public RecordMetadata toDomain() {
        return new RecordMetadata(createdBy, createdAt, lastModifiedBy, lastModifiedAt, recordVersion,
                sourceChannel, correlationId);
    }

    public Instant createdAt() {
        return createdAt;
    }
}
