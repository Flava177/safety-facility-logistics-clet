package gh.edu.clet.sfl.safetysecurity.lifesafety.infrastructure.persistence;

import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.MusterCheckIn;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.SourceChannel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "lifesafety_muster_checkins", schema = "safety_security")
public class MusterCheckInJpaEntity {

    @Id
    private UUID id;
    @Column(name = "muster_session_id", nullable = false)
    private UUID musterSessionId;
    @Column(name = "person_ref", nullable = false, length = 160)
    private String personRef;
    @Column(name = "checked_in_at", nullable = false)
    private Instant checkedInAt;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SourceChannel source;

    protected MusterCheckInJpaEntity() {
    }

    public static MusterCheckInJpaEntity from(MusterCheckIn c) {
        MusterCheckInJpaEntity e = new MusterCheckInJpaEntity();
        e.id = c.id();
        e.musterSessionId = c.musterSessionId();
        e.personRef = c.personRef();
        e.checkedInAt = c.checkedInAt();
        e.source = c.source();
        return e;
    }

    public MusterCheckIn toDomain() {
        return new MusterCheckIn(id, musterSessionId, personRef, checkedInAt, source);
    }
}
