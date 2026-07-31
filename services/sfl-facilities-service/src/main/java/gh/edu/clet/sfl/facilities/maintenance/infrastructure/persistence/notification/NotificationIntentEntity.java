package gh.edu.clet.sfl.facilities.maintenance.infrastructure.persistence.notification;

import gh.edu.clet.sfl.facilities.maintenance.application.ports.NotificationPort.NotificationKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.ColumnTransformer;

/** A notification that should be sent, recorded durably so it can be reconciled rather than assumed. */
@Entity
@Table(name = "facility_notification_intents", schema = "facilities")
class NotificationIntentEntity {

    static final String RECIPIENT_USER = "USER";
    static final String RECIPIENT_ROLE = "ROLE";

    /** Recorded, not delivered. The distinction is the point of the table. */
    static final String STATUS_PENDING = "PENDING";

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
        // JPA. Package-private rather than public: nothing outside this package constructs one.
    }

    NotificationIntentEntity(UUID id, String siteCode, String recipientType, String recipient,
            NotificationKind notificationKind, String subjectReference, String context, Instant createdAt) {
        this.id = id;
        this.siteCode = siteCode;
        this.recipientType = recipientType;
        this.recipient = recipient;
        this.notificationKind = notificationKind;
        this.subjectReference = subjectReference;
        this.context = context;
        this.status = STATUS_PENDING;
        this.createdAt = createdAt;
    }
}
