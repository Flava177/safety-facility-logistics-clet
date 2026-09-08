package gh.edu.clet.sfl.facilities.shared.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.facilities.shared.application.port.AuditPort;
import gh.edu.clet.sfl.facilities.shared.application.port.RepositoryPage;
import gh.edu.clet.sfl.facilities.shared.application.port.RuntimeConfigurationPort;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditAction;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditChainVerification;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditEvent;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Audit and runtime-configuration use cases (SRS-SFL-S152-02, -03).
 *
 * <p>Exists because both of its write paths are <em>two</em> operations that must share a transaction:
 * verify-then-record, and configure-then-record. {@link AuditPort#record} is deliberately
 * {@code MANDATORY} - it must never run outside the transaction of the change it documents - so a
 * controller calling it directly fails with "No existing transaction found". Found by running the
 * endpoints against a real database; the fix is this class, not a weaker propagation.
 */
@Service
public class FacilitiesGovernanceService {

    private final AuditPort audit;
    private final RuntimeConfigurationPort configuration;
    private final FacilitiesAuthorization authorization;

    public FacilitiesGovernanceService(AuditPort audit, RuntimeConfigurationPort configuration,
            FacilitiesAuthorization authorization) {
        this.audit = audit;
        this.configuration = configuration;
        this.authorization = authorization;
    }

    @Transactional(readOnly = true)
    public RepositoryPage<AuditEvent> search(String siteCode, String resourceType, String resourceId,
            String actorId, AuditAction action, Instant from, Instant to, int page, int size, ActorContext actor,
            SourceChannel channel) {
        authorization.require(actor, SflPermission.FACILITIES_AUDIT_READ, channel, "Audit", "search", siteCode);
        authorization.requireRequestedSite(actor, siteCode, channel, "Audit");
        RepositoryPage<AuditEvent> found = audit.search(siteCode, resourceType, resourceId, actorId, action, from,
                to, page, size);
        // Platform-wide records (site scope "*") are visible to anyone who may read the audit at all;
        // site-scoped ones follow the actor's scopes like every other record.
        List<AuditEvent> visible = found.items().stream()
                .filter(event -> "*".equals(event.siteScope())
                        || authorization.canAccessSite(actor, event.siteScope()))
                .toList();
        // When filtering removed rows, the total is reported as what remains: a total counting records
        // the caller may not see would let them infer another site's activity.
        return visible.size() == found.items().size()
                ? found
                : RepositoryPage.of(visible, visible.size(), found.page(), found.size());
    }

    /**
     * Replays the chain and records that the check was run.
     *
     * <p>One transaction, because the integrity report and its own audit entry are one act. A report
     * nobody can prove was run is not evidence of anything.
     */
    @Transactional
    public AuditChainVerification verifyChain(ActorContext actor, SourceChannel channel) {
        authorization.require(actor, SflPermission.FACILITIES_AUDIT_INTEGRITY_CHECK, channel, "Audit",
                "integrity", null);
        AuditChainVerification verification = audit.verifyChain();
        audit.record(actor, channel, AuditAction.AUDIT_INTEGRITY_VERIFIED, "Audit", "chain", "*", null,
                verification);
        return verification;
    }

    @Transactional(readOnly = true)
    public List<RuntimeConfigurationPort.ConfigurationValue> activeConfiguration(String siteCode,
            ActorContext actor, SourceChannel channel) {
        authorization.require(actor, SflPermission.FACILITIES_CONFIG_READ, channel, "Configuration", "list",
                siteCode);
        return configuration.activeValues(siteCode);
    }

    @Transactional
    public RuntimeConfigurationPort.ConfigurationValue putConfiguration(String key, String siteCode,
            String value, String valueType, String description, ActorContext actor, SourceChannel channel) {
        authorization.require(actor, SflPermission.FACILITIES_CONFIG_MANAGE, channel, "Configuration", key,
                siteCode);
        if (siteCode != null && !siteCode.isBlank()) {
            authorization.requireSite(actor, siteCode, channel, "Configuration", key);
        }
        RuntimeConfigurationPort.ConfigurationValue saved = configuration.put(key, siteCode, value, valueType,
                description, actor.actorId());
        audit.record(actor, channel, AuditAction.RUNTIME_CONFIGURATION_CHANGED, "Configuration", key,
                siteCode == null || siteCode.isBlank() ? "*" : siteCode, null, saved);
        return saved;
    }
}
