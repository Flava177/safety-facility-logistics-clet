package gh.edu.clet.sfl.safetysecurity.incident.infrastructure.persistence;

import gh.edu.clet.sfl.safetysecurity.incident.domain.model.Impact;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.IncidentSource;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.IncidentStatus;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.Likelihood;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.RecordMetadata;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.RiskRating;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.SecurityIncident;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.Severity;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.SourceChannel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA mapping for {@link SecurityIncident}. Lands in {@code safety_security}, Hibernate's default
 * schema for this deployable, same as S160's entities - no explicit {@code schema=} needed.
 *
 * <p>{@link RiskRating}'s two axes are flattened into two nullable columns rather than an embedded
 * type, since the rating does not exist until triage - both are null together or set together, which
 * a nullable embeddable cannot express as cleanly as two plain nullable enum columns can.
 */
@Entity
@Table(name = "security_incidents", schema = "safety_security")
public class SecurityIncidentJpaEntity {

    @Id
    private UUID id;
    @Column(name = "site_code", nullable = false, length = 80)
    private String siteCode;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IncidentSource source;
    @Column(nullable = false, length = 40)
    private String reference;
    @Column(nullable = false)
    private boolean anonymous;
    @Column(name = "reporter_id", length = 160)
    private String reporterId;
    @Column(name = "reporter_contact", length = 200)
    private String reporterContact;
    @Column(nullable = false, length = 4000)
    private String description;
    @Column(name = "near_miss", nullable = false)
    private boolean nearMiss;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IncidentStatus status;
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Severity severity;
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Likelihood likelihood;
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Impact impact;
    @Column(name = "emergency_escalated", nullable = false)
    private boolean emergencyEscalated;
    @Column(name = "investigator_id", length = 160)
    private String investigatorId;
    @Column(name = "investigation_notes", length = 4000)
    private String investigationNotes;
    @Column(nullable = false)
    private boolean reportable;
    @Column(name = "reportability_notes", length = 2000)
    private String reportabilityNotes;
    @Column(name = "closure_notes", length = 4000)
    private String closureNotes;
    @Column(name = "closed_at")
    private Instant closedAt;
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
    @Column(name = "source_channel", nullable = false, length = 20)
    private SourceChannel sourceChannel;
    @Column(name = "correlation_id", length = 120)
    private String correlationId;

    protected SecurityIncidentJpaEntity() {
    }

    public static SecurityIncidentJpaEntity from(SecurityIncident incident) {
        SecurityIncidentJpaEntity entity = new SecurityIncidentJpaEntity();
        entity.apply(incident);
        return entity;
    }

    public void apply(SecurityIncident incident) {
        id = incident.id();
        siteCode = incident.siteCode();
        source = incident.source();
        reference = incident.reference();
        anonymous = incident.anonymous();
        reporterId = incident.reporterId();
        reporterContact = incident.reporterContact();
        description = incident.description();
        nearMiss = incident.nearMiss();
        status = incident.status();
        severity = incident.severity();
        RiskRating rating = incident.riskRating();
        likelihood = rating == null ? null : rating.likelihood();
        impact = rating == null ? null : rating.impact();
        emergencyEscalated = incident.emergencyEscalated();
        investigatorId = incident.investigatorId();
        investigationNotes = incident.investigationNotes();
        reportable = incident.reportable();
        reportabilityNotes = incident.reportabilityNotes();
        closureNotes = incident.closureNotes();
        closedAt = incident.closedAt();
        RecordMetadata metadata = incident.metadata();
        createdBy = metadata.createdBy();
        createdAt = metadata.createdAt();
        lastModifiedBy = metadata.lastModifiedBy();
        lastModifiedAt = metadata.lastModifiedAt();
        recordVersion = metadata.version();
        sourceChannel = metadata.sourceChannel();
        correlationId = metadata.correlationId();
    }

    public SecurityIncident toDomain() {
        RiskRating rating = likelihood == null || impact == null ? null : new RiskRating(likelihood, impact);
        RecordMetadata metadata = RecordMetadata.rehydrate(createdBy, createdAt, lastModifiedBy, lastModifiedAt,
                recordVersion, sourceChannel, correlationId);
        return new SecurityIncident(id, siteCode, source, reference, anonymous, reporterId, reporterContact,
                description, nearMiss, status, severity, rating, emergencyEscalated, investigatorId,
                investigationNotes, reportable, reportabilityNotes, closureNotes, closedAt, metadata);
    }

    public UUID getId() {
        return id;
    }

    public String getSiteCode() {
        return siteCode;
    }

    public IncidentStatus getStatus() {
        return status;
    }

    public Severity getSeverity() {
        return severity;
    }
}
