package gh.edu.clet.sfl.facilities.eventlogistics.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.eventlogistics.domain.EventResourceRequest;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.EventResourceType;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.OwningSystem;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.ResourceRequestStatus;
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

/** JPA mapping for {@link EventResourceRequest}. Column names match V20 exactly. */
@Entity
@Table(name = "event_resource_requests", schema = "facilities")
public class EventResourceRequestRecord extends VersionedRecord {

    @Id
    private UUID id;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Column(name = "setup_task_id", nullable = false)
    private UUID setupTaskId;
    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 30)
    private EventResourceType resourceType;
    @Enumerated(EnumType.STRING)
    @Column(name = "owning_system", nullable = false, length = 10)
    private OwningSystem owningSystem;
    @Column(nullable = false, length = 1000)
    private String description;
    @Column(nullable = false)
    private int quantity;
    @Column(name = "bookable_resource_id")
    private UUID bookableResourceId;
    @Column(name = "needed_from", nullable = false)
    private Instant neededFrom;
    @Column(name = "needed_to", nullable = false)
    private Instant neededTo;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ResourceRequestStatus status;
    @Column(name = "external_reference", length = 120)
    private String externalReference;
    @Column(name = "external_parent_reference", length = 120)
    private String externalParentReference;
    @Column(name = "status_detail", length = 2000)
    private String statusDetail;
    @Column(name = "competing_commitment", length = 1000)
    private String competingCommitment;
    @Column(name = "route_attempts", nullable = false)
    private int routeAttempts;
    @Column(name = "manual_accepted_by", length = 160)
    private String manualAcceptedBy;
    @Column(name = "manual_arranged_with", length = 300)
    private String manualArrangedWith;
    @Column(name = "manual_accepted_at")
    private Instant manualAcceptedAt;
    @Column(name = "template_line_id")
    private UUID templateLineId;
    @Column(name = "requested_by", nullable = false, length = 160)
    private String requestedBy;
    @Embedded
    private RecordMetadataEmbeddable metadata;

    protected EventResourceRequestRecord() {
    }

    static EventResourceRequestRecord empty() {
        return new EventResourceRequestRecord();
    }

    void apply(EventResourceRequest request) {
        id = request.id();
        siteCode = request.siteCode();
        setupTaskId = request.setupTaskId();
        resourceType = request.resourceType();
        owningSystem = request.owningSystem();
        description = truncate(request.description(), 1000);
        quantity = request.quantity();
        bookableResourceId = request.bookableResourceId();
        neededFrom = request.neededFrom();
        neededTo = request.neededTo();
        status = request.status();
        externalReference = request.externalReference();
        externalParentReference = request.externalParentReference();
        statusDetail = truncate(request.statusDetail(), 2000);
        competingCommitment = truncate(request.competingCommitment(), 1000);
        routeAttempts = request.routeAttempts();
        manualAcceptedBy = request.manualAcceptedBy();
        manualArrangedWith = truncate(request.manualArrangedWith(), 300);
        manualAcceptedAt = request.manualAcceptedAt();
        templateLineId = request.templateLineId();
        requestedBy = request.requestedBy();
        metadata = RecordMetadataEmbeddable.from(request.metadata());
    }

    EventResourceRequest toDomain() {
        return new EventResourceRequest(id, siteCode, setupTaskId, resourceType, owningSystem, description, quantity,
                bookableResourceId, neededFrom, neededTo, status, externalReference, externalParentReference,
                statusDetail, competingCommitment, routeAttempts, manualAcceptedBy, manualArrangedWith,
                manualAcceptedAt, templateLineId, requestedBy, metadata.toDomain(recordVersion()));
    }

    /**
     * Owning-system text (a conflict naming several bookings, a template lesson appended to a description)
     * can outgrow its column. Truncated here, visibly, rather than failing the whole routing call.
     */
    static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max - 3) + "...";
    }
}
