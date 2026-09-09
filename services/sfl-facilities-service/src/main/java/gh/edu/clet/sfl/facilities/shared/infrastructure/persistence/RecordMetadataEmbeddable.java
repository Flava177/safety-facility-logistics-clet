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
 * <p>Six of the seven columns SRS-SFL-S152-01 requires on every operational record. Declaring them in
 * one {@code @Embeddable} rather than copying them into every entity is what stops the set drifting -
 * an entity that forgot {@code correlation_id} would produce audit records nobody can trace, and the
 * omission would be invisible in review.
 *
 * <p>The seventh, {@code record_version}, is not here - JPA does not allow {@code @Version} on an
 * embeddable's property, so it lives on {@link VersionedRecord}, a mapped superclass every entity that
 * needs a real optimistic lock extends instead. This class stays the source of the other six; the
 * split is a JPA mapping restriction, not a change in what SRS-SFL-S152-01 asks for.
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
        embeddable.sourceChannel = metadata.sourceChannel();
        embeddable.correlationId = metadata.correlationId();
        return embeddable;
    }

    /** {@code version} comes from the owning entity's {@link VersionedRecord#recordVersion()}. */
    public RecordMetadata toDomain(long version) {
        return new RecordMetadata(createdBy, createdAt, lastModifiedBy, lastModifiedAt, version, sourceChannel,
                correlationId);
    }

    public Instant createdAt() {
        return createdAt;
    }
}
