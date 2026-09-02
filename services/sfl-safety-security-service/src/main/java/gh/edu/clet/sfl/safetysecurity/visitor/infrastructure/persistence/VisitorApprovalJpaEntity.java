package gh.edu.clet.sfl.safetysecurity.visitor.infrastructure.persistence;

import gh.edu.clet.sfl.safetysecurity.visitor.domain.model.VisitorApproval;
import gh.edu.clet.sfl.safetysecurity.visitor.domain.model.VisitorApprovalDecision;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** JPA mapping for {@link VisitorApproval}. */
@Entity
@Table(name = "visitor_approvals", schema = "safety_security")
public class VisitorApprovalJpaEntity {

    @Id
    private UUID id;
    @Column(name = "visit_id", nullable = false)
    private UUID visitId;
    @Column(name = "site_code", nullable = false, length = 80)
    private String siteCode;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VisitorApprovalDecision decision;
    @Column(length = 2000)
    private String reason;
    @Column(name = "decided_by", nullable = false, length = 160)
    private String decidedBy;
    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;

    protected VisitorApprovalJpaEntity() {
    }

    public static VisitorApprovalJpaEntity from(VisitorApproval approval) {
        VisitorApprovalJpaEntity entity = new VisitorApprovalJpaEntity();
        entity.apply(approval);
        return entity;
    }

    public void apply(VisitorApproval approval) {
        id = approval.id();
        visitId = approval.visitId();
        siteCode = approval.siteCode();
        decision = approval.decision();
        reason = approval.reason();
        decidedBy = approval.decidedBy();
        decidedAt = approval.decidedAt();
    }

    public VisitorApproval toDomain() {
        return new VisitorApproval(id, visitId, siteCode, decision, reason, decidedBy, decidedAt);
    }
}
