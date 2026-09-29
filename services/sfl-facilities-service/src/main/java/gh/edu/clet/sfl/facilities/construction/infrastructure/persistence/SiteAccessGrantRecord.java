package gh.edu.clet.sfl.facilities.construction.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.construction.domain.SiteAccessGrant;
import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.RecordMetadataEmbeddable;
import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.VersionedRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** JPA mapping for {@link SiteAccessGrant}. Column names match V21 exactly. */
@Entity
@Table(name = "construction_site_access_grants", schema = "facilities")
public class SiteAccessGrantRecord extends VersionedRecord {

    @Id
    private UUID id;
    @Column(name = "contractor_id", nullable = false)
    private UUID contractorId;
    @Column(name = "project_id")
    private UUID projectId;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Column(name = "access_scope", nullable = false, length = 500)
    private String accessScope;
    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;
    @Column(name = "valid_to", nullable = false)
    private Instant validTo;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SiteAccessGrant.Status status;
    @Column(name = "refusal_reason", length = 2000)
    private String refusalReason;
    @Column(name = "suspended_at")
    private Instant suspendedAt;
    @Column(name = "suspension_reason", length = 2000)
    private String suspensionReason;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private SiteAccessGrant.Enforcement enforcement;
    @Column(name = "dispatch_provider", length = 80)
    private String dispatchProvider;
    @Column(name = "requested_by", nullable = false, length = 160)
    private String requestedBy;
    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;
    @Embedded
    private RecordMetadataEmbeddable metadata;

    protected SiteAccessGrantRecord() {
    }

    static SiteAccessGrantRecord empty() {
        return new SiteAccessGrantRecord();
    }

    void apply(SiteAccessGrant grant) {
        id = grant.id();
        contractorId = grant.contractorId();
        projectId = grant.projectId();
        siteCode = grant.siteCode();
        accessScope = grant.accessScope();
        validFrom = grant.validFrom();
        validTo = grant.validTo();
        status = grant.status();
        refusalReason = grant.refusalReason();
        suspendedAt = grant.suspendedAt();
        suspensionReason = grant.suspensionReason();
        enforcement = grant.enforcement();
        dispatchProvider = grant.dispatchProvider();
        requestedBy = grant.requestedBy();
        requestedAt = grant.requestedAt();
        metadata = RecordMetadataEmbeddable.from(grant.metadata());
    }

    SiteAccessGrant toDomain() {
        return new SiteAccessGrant(id, contractorId, projectId, siteCode, accessScope, validFrom, validTo, status,
                refusalReason, suspendedAt, suspensionReason, enforcement, dispatchProvider, requestedBy, requestedAt,
                metadata.toDomain(recordVersion()));
    }
}
