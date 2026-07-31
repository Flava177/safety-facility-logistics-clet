package gh.edu.clet.sfl.facilities.booking.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.booking.domain.ApprovalDecision;
import gh.edu.clet.sfl.facilities.booking.domain.BookingApproval;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** JPA mapping for {@link BookingApproval}. Column names match V10 exactly. */
@Entity
@Table(name = "booking_approvals", schema = "facilities")
public class BookingApprovalRecord {

    @Id
    private UUID id;
    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApprovalDecision decision;
    @Column(length = 2000)
    private String reason;
    @Column(name = "decided_by", nullable = false, length = 160)
    private String decidedBy;
    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;

    protected BookingApprovalRecord() {
    }

    public void apply(BookingApproval approval) {
        id = approval.id();
        bookingId = approval.bookingId();
        siteCode = approval.siteCode();
        decision = approval.decision();
        reason = approval.reason();
        decidedBy = approval.decidedBy();
        decidedAt = approval.decidedAt();
    }

    public BookingApproval toDomain() {
        return new BookingApproval(id, bookingId, siteCode, decision, reason, decidedBy, decidedAt);
    }
}
