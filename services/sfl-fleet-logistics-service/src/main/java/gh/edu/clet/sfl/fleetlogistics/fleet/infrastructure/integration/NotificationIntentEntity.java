package gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.integration;

import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.NotificationPort.NotificationKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.ColumnTransformer;

/** A workflow notification the service intended to send (SRS-SFL-S166-02). */
@Entity
@Table(name = "fleet_notification_intents", schema = "fleet_logistics")
public class NotificationIntentEntity {

    public static final String RECIPIENT_USER = "USER";
    public static final String RECIPIENT_ROLE = "ROLE";
    public static final String STATUS_RECORDED = "RECORDED";
    public static final String STATUS_DELIVERED = "DELIVERED";
    public static final String STATUS_FAILED = "FAILED";

    @Id
    private UUID id;

    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;

    @Column(name = "recipient_type", nullable = false, length = 20)
    private String recipientType;

    @Column(nullable = false, length = 160)
    private String recipient;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_kind", nullable = false, length = 60)
    private NotificationKind notificationKind;

    @Column(name = "subject_reference", nullable = false, length = 160)
    private String subjectReference;

    @Column(nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String context;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    protected NotificationIntentEntity() {
    }

    public NotificationIntentEntity(UUID id, String siteCode, String recipientType, String recipient,
            NotificationKind notificationKind, String subjectReference, String context, Instant createdAt) {
        this.id = id;
        this.siteCode = siteCode;
        this.recipientType = recipientType;
        this.recipient = recipient;
        this.notificationKind = notificationKind;
        this.subjectReference = subjectReference;
        this.context = context;
        this.status = STATUS_RECORDED;
        this.createdAt = createdAt;
    }

    public UUID id() {
        return id;
    }

    public String recipient() {
        return recipient;
    }

    public NotificationKind notificationKind() {
        return notificationKind;
    }

    public String subjectReference() {
        return subjectReference;
    }

    public String status() {
        return status;
    }
}
