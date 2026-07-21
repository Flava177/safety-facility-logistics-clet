package gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.persistence;

import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.FleetWorkflowType;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.OperatingMode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.WorkflowPriority;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.WorkflowSeverity;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.policy.SlaPolicy;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * A configured SLA rule.
 *
 * <p>A null dimension means "any", which is how one broad default and several narrow exceptions live
 * in the same table; {@link SlaPolicy} picks the most specific match.
 */
@Entity
@Table(name = "fleet_sla_rules", schema = "fleet_logistics")
public class SlaRuleEntity {

    @Id
    private UUID id;

    @Column(name = "rule_reference", nullable = false, length = 120)
    private String ruleReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "workflow_type", length = 60)
    private FleetWorkflowType workflowType;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private WorkflowPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private WorkflowSeverity severity;

    @Column(name = "site_code", length = 40)
    private String siteCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "operating_mode", length = 30)
    private OperatingMode operatingMode;

    @Column(name = "response_minutes", nullable = false)
    private int responseMinutes;

    @Column(name = "resolution_minutes", nullable = false)
    private int resolutionMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "escalation_role", nullable = false, length = 60)
    private SflRole escalationRole;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "effective_to")
    private Instant effectiveTo;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "updated_by", nullable = false, length = 160)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SlaRuleEntity() {
    }

    public SlaPolicy.SlaRule toDomain() {
        return new SlaPolicy.SlaRule(ruleReference, workflowType, priority, severity, siteCode, operatingMode,
                Duration.ofMinutes(responseMinutes), Duration.ofMinutes(resolutionMinutes), escalationRole);
    }
}
