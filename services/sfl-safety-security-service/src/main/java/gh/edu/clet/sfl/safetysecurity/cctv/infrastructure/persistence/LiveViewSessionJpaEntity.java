package gh.edu.clet.sfl.safetysecurity.cctv.infrastructure.persistence;

import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.LiveViewSession;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** JPA mapping for {@link LiveViewSession}. {@code cameraIds} is stored as a comma-delimited string,
 * following {@code EvidenceRequestJpaEntity}'s reasoning. */
@Entity
@Table(name = "cctv_live_view_sessions", schema = "safety_security")
public class LiveViewSessionJpaEntity {

    @Id
    private UUID id;
    @Column(name = "site_code", nullable = false, length = 80)
    private String siteCode;
    @Column(name = "operator_id", nullable = false, length = 160)
    private String operatorId;
    @Column(name = "camera_ids", nullable = false, length = 2000)
    private String cameraIdsText;
    @Column(name = "started_at", nullable = false)
    private Instant startedAt;
    @Column(name = "ended_at")
    private Instant endedAt;

    protected LiveViewSessionJpaEntity() {
    }

    public void apply(LiveViewSession session) {
        id = session.id();
        siteCode = session.siteCode();
        operatorId = session.operatorId();
        cameraIdsText = String.join(",", session.cameraIds());
        startedAt = session.startedAt();
        endedAt = session.endedAt();
    }

    public LiveViewSession toDomain() {
        List<String> cameraIds = cameraIdsText == null || cameraIdsText.isBlank() ? List.of()
                : Arrays.asList(cameraIdsText.split(","));
        return new LiveViewSession(id, siteCode, operatorId, cameraIds, startedAt, endedAt);
    }

    public UUID getId() {
        return id;
    }
}
