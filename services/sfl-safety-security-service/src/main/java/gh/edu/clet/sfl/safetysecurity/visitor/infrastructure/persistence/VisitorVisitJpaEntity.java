package gh.edu.clet.sfl.safetysecurity.visitor.infrastructure.persistence;

import gh.edu.clet.sfl.safetysecurity.visitor.domain.model.RecordMetadata;
import gh.edu.clet.sfl.safetysecurity.visitor.domain.model.SourceChannel;
import gh.edu.clet.sfl.safetysecurity.visitor.domain.model.VisitPurpose;
import gh.edu.clet.sfl.safetysecurity.visitor.domain.model.VisitStatus;
import gh.edu.clet.sfl.safetysecurity.visitor.domain.model.VisitorVisit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * JPA mapping for {@link VisitorVisit}. Lands in {@code safety_security}, Hibernate's default
 * schema for this deployable, so no explicit {@code schema=} is needed here (contrast a future
 * entity mapped into {@code emergency_notification}, which must carry one).
 *
 * <p>{@code accessZones} is stored as one comma-joined column rather than an
 * {@code @ElementCollection} table: a handful of zone codes per visit does not earn a second table,
 * and the badge/access sync of record is the device gateway, not a queryable join here.
 *
 * <h2>{@code record_version} is a real JPA {@code @Version}, and {@code apply} never assigns it</h2>
 *
 * <p>{@link RecordMetadata#modifiedBy} still owns the business-meaningful number - every domain
 * transition bumps it by exactly one, and that is the value {@link #toDomain()} returns and the value
 * {@link RecordMetadata#requireVersion} checks a request's claimed version against. What changed is
 * who writes the column: {@code apply} used to copy {@code metadata.version()} onto this field
 * directly, which made the number Hibernate's flush would compute purely cosmetic - a plain
 * {@code @Column} has no WHERE-clause guard, so two requests loading the same row and both calling
 * {@code modifiedBy} would both write successfully, each overwriting the other's change with no error
 * to either caller. {@code @Version} makes Hibernate include {@code AND record_version = <loaded
 * value>} on the UPDATE and throw {@link org.springframework.dao.OptimisticLockingFailureException}
 * (already handled by {@code VisitorApiExceptionHandler}, mapped to the same
 * {@code VISITOR_RECORD_VERSION_CONFLICT} the pre-check throws) the moment a second writer loses the
 * race - a real compare-and-swap, not just an in-memory number. Because {@code modifiedBy} always
 * computes loaded-version-plus-one and Hibernate's own flush computes the identical loaded-value-
 * plus-one, the two numbers coincide in every normal path; the field simply stops being one this
 * class can get out of sync with the database.
 */
@Entity
@Table(name = "visitor_visits", schema = "safety_security")
public class VisitorVisitJpaEntity {

    @Id
    private UUID id;
    @Column(name = "site_code", nullable = false, length = 80)
    private String siteCode;
    @Column(name = "visitor_name", nullable = false, length = 200)
    private String visitorName;
    @Column(name = "visitor_organization", length = 200)
    private String visitorOrganization;
    @Column(name = "visitor_contact", length = 200)
    private String visitorContact;
    @Column(name = "host_id", nullable = false, length = 160)
    private String hostId;
    @Column(name = "host_name", length = 200)
    private String hostName;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private VisitPurpose purpose;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VisitStatus status;
    @Column(name = "expected_arrival", nullable = false)
    private Instant expectedArrival;
    @Column(name = "expected_departure")
    private Instant expectedDeparture;
    @Column(name = "approval_required", nullable = false)
    private boolean approvalRequired;
    @Column(name = "approval_id")
    private UUID approvalId;
    @Column(name = "watchlist_flagged", nullable = false)
    private boolean watchlistFlagged;
    @Column(name = "watchlist_override_reason", length = 2000)
    private String watchlistOverrideReason;
    @Column(name = "badge_number", length = 80)
    private String badgeNumber;
    @Column(name = "access_zones", length = 1000)
    private String accessZones;
    @Column(name = "checked_in_at")
    private Instant checkedInAt;
    @Column(name = "checked_out_at")
    private Instant checkedOutAt;
    @Column(name = "closure_reason", length = 2000)
    private String closureReason;
    @Column(name = "created_by", nullable = false, length = 160)
    private String createdBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "last_modified_by", nullable = false, length = 160)
    private String lastModifiedBy;
    @Column(name = "last_modified_at", nullable = false)
    private Instant lastModifiedAt;
    @Version
    @Column(name = "record_version", nullable = false)
    private long recordVersion;
    @Enumerated(EnumType.STRING)
    @Column(name = "source_channel", nullable = false, length = 20)
    private SourceChannel sourceChannel;
    @Column(name = "correlation_id", length = 120)
    private String correlationId;

    protected VisitorVisitJpaEntity() {
    }

    public static VisitorVisitJpaEntity from(VisitorVisit visit) {
        VisitorVisitJpaEntity entity = new VisitorVisitJpaEntity();
        entity.apply(visit);
        return entity;
    }

    public void apply(VisitorVisit visit) {
        id = visit.id();
        siteCode = visit.siteCode();
        visitorName = visit.visitorName();
        visitorOrganization = visit.visitorOrganization();
        visitorContact = visit.visitorContact();
        hostId = visit.hostId();
        hostName = visit.hostName();
        purpose = visit.purpose();
        status = visit.status();
        expectedArrival = visit.expectedArrival();
        expectedDeparture = visit.expectedDeparture();
        approvalRequired = visit.approvalRequired();
        approvalId = visit.approvalId();
        watchlistFlagged = visit.watchlistFlagged();
        watchlistOverrideReason = visit.watchlistOverrideReason();
        badgeNumber = visit.badgeNumber();
        accessZones = visit.accessZones().isEmpty() ? null : String.join(",", visit.accessZones());
        checkedInAt = visit.checkedInAt();
        checkedOutAt = visit.checkedOutAt();
        closureReason = visit.closureReason();
        RecordMetadata metadata = visit.metadata();
        createdBy = metadata.createdBy();
        createdAt = metadata.createdAt();
        lastModifiedBy = metadata.lastModifiedBy();
        lastModifiedAt = metadata.lastModifiedAt();
        // recordVersion is deliberately not assigned here - see the class Javadoc. It is a JPA
        // @Version field; Hibernate owns it exclusively, and an application write to it would defeat
        // the compare-and-swap this field exists to provide.
        sourceChannel = metadata.sourceChannel();
        correlationId = metadata.correlationId();
    }

    public VisitorVisit toDomain() {
        List<String> zones = accessZones == null || accessZones.isBlank() ? List.of()
                : Arrays.asList(accessZones.split(","));
        RecordMetadata metadata = RecordMetadata.rehydrate(createdBy, createdAt, lastModifiedBy, lastModifiedAt,
                recordVersion, sourceChannel, correlationId);
        return new VisitorVisit(id, siteCode, visitorName, visitorOrganization, visitorContact, hostId, hostName,
                purpose, status, expectedArrival, expectedDeparture, approvalRequired, approvalId,
                watchlistFlagged, watchlistOverrideReason, badgeNumber, zones, checkedInAt, checkedOutAt,
                closureReason, metadata);
    }

    public UUID getId() {
        return id;
    }
}
