package gh.edu.clet.sfl.facilities.buildingsystems.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.buildingsystems.domain.MeasuredQuantity;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.RuleConflict;
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

/** JPA mapping for {@link RuleConflict}. Column names match V16. */
@Entity
@Table(name = "bms_rule_conflicts", schema = "facilities")
public class RuleConflictRecord extends VersionedRecord {

    @Id
    private UUID id;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Column(name = "rule_id", nullable = false)
    private UUID ruleId;
    @Column(name = "conflicting_rule_id", nullable = false)
    private UUID conflictingRuleId;
    @Column(name = "winning_rule_id", nullable = false)
    private UUID winningRuleId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private MeasuredQuantity quantity;
    @Column(length = 1000)
    private String detail;
    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;
    @Embedded
    private RecordMetadataEmbeddable metadata;

    protected RuleConflictRecord() {
    }

    static RuleConflictRecord of(RuleConflict conflict) {
        RuleConflictRecord record = new RuleConflictRecord();
        record.id = conflict.id();
        record.siteCode = conflict.siteCode();
        record.ruleId = conflict.ruleId();
        record.conflictingRuleId = conflict.conflictingRuleId();
        record.winningRuleId = conflict.winningRuleId();
        record.quantity = conflict.quantity();
        record.detail = conflict.detail();
        record.detectedAt = conflict.detectedAt();
        record.metadata = RecordMetadataEmbeddable.from(conflict.metadata());
        return record;
    }

    RuleConflict toDomain() {
        return new RuleConflict(id, siteCode, ruleId, conflictingRuleId, winningRuleId, quantity, detail, detectedAt,
                metadata.toDomain(recordVersion()));
    }
}
