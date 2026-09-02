package gh.edu.clet.sfl.safetysecurity.incident.infrastructure.persistence;

import gh.edu.clet.sfl.safetysecurity.incident.domain.model.CapaStatus;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.CorrectiveAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** JPA mapping for {@link CorrectiveAction}. */
@Entity
@Table(name = "corrective_actions", schema = "safety_security")
public class CorrectiveActionJpaEntity {

    @Id
    private UUID id;
    @Column(name = "incident_id", nullable = false)
    private UUID incidentId;
    @Column(name = "site_code", nullable = false, length = 80)
    private String siteCode;
    @Column(nullable = false, length = 2000)
    private String description;
    @Column(name = "owner_id", nullable = false, length = 160)
    private String ownerId;
    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;
    @Column(nullable = false)
    private boolean mandatory;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CapaStatus status;
    @Column(name = "verification_notes", length = 2000)
    private String verificationNotes;
    @Column(name = "created_by", nullable = false, length = 160)
    private String createdBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "resolved_by", length = 160)
    private String resolvedBy;
    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected CorrectiveActionJpaEntity() {
    }

    public static CorrectiveActionJpaEntity from(CorrectiveAction action) {
        CorrectiveActionJpaEntity entity = new CorrectiveActionJpaEntity();
        entity.apply(action);
        return entity;
    }

    public void apply(CorrectiveAction action) {
        id = action.id();
        incidentId = action.incidentId();
        siteCode = action.siteCode();
        description = action.description();
        ownerId = action.ownerId();
        dueDate = action.dueDate();
        mandatory = action.mandatory();
        status = action.status();
        verificationNotes = action.verificationNotes();
        createdBy = action.createdBy();
        createdAt = action.createdAt();
        resolvedBy = action.resolvedBy();
        resolvedAt = action.resolvedAt();
    }

    public CorrectiveAction toDomain() {
        return new CorrectiveAction(id, incidentId, siteCode, description, ownerId, dueDate, mandatory, status,
                verificationNotes, createdBy, createdAt, resolvedBy, resolvedAt);
    }

    public UUID getId() {
        return id;
    }

    public boolean isMandatory() {
        return mandatory;
    }

    public CapaStatus getStatus() {
        return status;
    }
}
