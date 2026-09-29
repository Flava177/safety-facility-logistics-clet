package gh.edu.clet.sfl.facilities.construction.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.construction.domain.RegisterChange;
import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.RecordMetadataEmbeddable;
import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.VersionedRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** JPA mapping for {@link RegisterChange}. Column names match V21 exactly. */
@Entity
@Table(name = "construction_handover_register_changes", schema = "facilities")
public class RegisterChangeRecord extends VersionedRecord {

    @Id
    private UUID id;
    @Column(name = "handover_id", nullable = false)
    private UUID handoverId;
    @Column(name = "project_id", nullable = false)
    private UUID projectId;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RegisterChange.Action action;
    @Column(name = "room_id", nullable = false)
    private UUID roomId;
    @Column(name = "room_code", nullable = false, length = 80)
    private String roomCode;
    @Column(name = "room_version", nullable = false)
    private long roomVersion;
    @Embedded
    private RecordMetadataEmbeddable metadata;

    protected RegisterChangeRecord() {
    }

    static RegisterChangeRecord empty() {
        return new RegisterChangeRecord();
    }

    void apply(RegisterChange change) {
        id = change.id();
        handoverId = change.handoverId();
        projectId = change.projectId();
        siteCode = change.siteCode();
        action = change.action();
        roomId = change.roomId();
        roomCode = change.roomCode();
        roomVersion = change.roomVersion();
        metadata = RecordMetadataEmbeddable.from(change.metadata());
    }

    RegisterChange toDomain() {
        return new RegisterChange(id, handoverId, projectId, siteCode, action, roomId, roomCode, roomVersion,
                metadata.toDomain(recordVersion()));
    }
}
