package gh.edu.clet.sfl.facilities.spaceplanning.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.RecordMetadataEmbeddable;
import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.VersionedRecord;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.UtilisationSignal;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** JPA mapping for {@link UtilisationSignal}. */
@Entity
@Table(name = "space_utilisation_signals", schema = "facilities")
public class UtilisationSignalRecord extends VersionedRecord {

    @Id
    private UUID id;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Column(name = "room_id", nullable = false)
    private UUID roomId;
    @Column(name = "room_code", nullable = false, length = 80)
    private String roomCode;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UtilisationSignal.Kind kind;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UtilisationSignal.Status status;
    @Column(name = "raised_at", nullable = false)
    private Instant raisedAt;
    @Column(name = "raised_for_period_start", nullable = false)
    private Instant raisedForPeriodStart;
    @Column(name = "raised_for_period_end", nullable = false)
    private Instant raisedForPeriodEnd;
    @Column(name = "latest_period_end", nullable = false)
    private Instant latestPeriodEnd;
    @Column(name = "latest_utilisation_rate", precision = 8, scale = 4)
    private BigDecimal latestUtilisationRate;
    @Column(name = "threshold_rate", precision = 8, scale = 4)
    private BigDecimal thresholdRate;
    @Column(length = 2000)
    private String detail;
    @Column(name = "cleared_at")
    private Instant clearedAt;
    @Embedded
    private RecordMetadataEmbeddable metadata;

    protected UtilisationSignalRecord() {
    }

    void apply(UtilisationSignal s) {
        id = s.id();
        siteCode = s.siteCode();
        roomId = s.roomId();
        roomCode = s.roomCode();
        kind = s.kind();
        status = s.status();
        raisedAt = s.raisedAt();
        raisedForPeriodStart = s.raisedForPeriodStart();
        raisedForPeriodEnd = s.raisedForPeriodEnd();
        latestPeriodEnd = s.latestPeriodEnd();
        latestUtilisationRate = s.latestUtilisationRate();
        thresholdRate = s.thresholdRate();
        detail = s.detail();
        clearedAt = s.clearedAt();
        metadata = RecordMetadataEmbeddable.from(s.metadata());
    }

    UtilisationSignal toDomain() {
        return new UtilisationSignal(id, siteCode, roomId, roomCode, kind, status, raisedAt, raisedForPeriodStart,
                raisedForPeriodEnd, latestPeriodEnd, latestUtilisationRate, thresholdRate, detail, clearedAt,
                metadata.toDomain(recordVersion()));
    }
}
