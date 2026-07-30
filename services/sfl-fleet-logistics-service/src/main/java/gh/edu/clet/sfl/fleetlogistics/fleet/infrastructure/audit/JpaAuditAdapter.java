package gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.audit;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.AuditPort;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.AuditAction;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.AuditChainVerification;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.AuditEvent;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.AuditHashChain;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Hash-chained, append-only audit writer (SRS-SFL-S166-03).
 *
 * <p>{@link #record} joins the caller's transaction, so a state change and its audit entry commit or
 * roll back together. {@link #recordAuthorizationDenied} runs in its own transaction because a denial
 * happens on a request that is about to fail — joining that transaction would discard the evidence that
 * the denial occurred.
 */
@Component
public class JpaAuditAdapter implements AuditPort {

    /** Site scope recorded for denials that never reached a site-scoped record. */
    private static final String UNSCOPED = "UNSCOPED";

    private final AuditRecordRepository auditRecords;
    private final AuditChainStateRepository chainState;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    JpaAuditAdapter(AuditRecordRepository auditRecords, AuditChainStateRepository chainState, Clock clock,
            ObjectMapper objectMapper) {
        this.auditRecords = auditRecords;
        this.chainState = chainState;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public AuditEvent record(ActorContext actor, SourceChannel sourceChannel, SiteCode siteScope, AuditAction action,
            String resourceType, String resourceId, Object beforeValue, Object afterValue) {
        return append(actor.actorId(), actor.principal().displayName(), siteScope, action, resourceType, resourceId,
                CanonicalJson.write(beforeValue), CanonicalJson.write(afterValue), actor.correlationId(),
                sourceChannel);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAuthorizationDenied(ActorContext actor, String siteScope, String resourceType,
            String resourceId, String requiredPermission, String reason) {
        Map<String, Object> denial = new LinkedHashMap<>();
        denial.put("reason", reason);
        denial.put("requiredPermission", requiredPermission);
        denial.put("actorRoles", actor.principal().roles().stream().map(Enum::name).sorted().toList());
        denial.put("actorSiteScopes", actor.principal().siteScopes().stream().sorted().toList());

        append(actor.actorId(), actor.principal().displayName(),
                SiteCode.of(siteScope == null || siteScope.isBlank() ? UNSCOPED : siteScope),
                AuditAction.AUTHORIZATION_DENIED, blankTo(resourceType, "Unknown"), blankTo(resourceId, "-"),
                null, CanonicalJson.write(denial),
                actor.correlationId(),
                actor.principal().serviceAccount() ? SourceChannel.INTEGRATION : SourceChannel.API);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditEvent> search(AuditQuery query) {
        boolean allSites = query.siteScopes() == null || query.siteScopes().isEmpty();
        List<String> scopes = allSites ? List.of() : query.siteScopes();
        return auditRecords.searchRecords(allSites, scopes, query).stream()
                .map(entity -> entity.toDomain(objectMapper))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AuditChainVerification verifyChain() {
        List<AuditEvent> ordered = auditRecords.findAllByOrderBySequenceNoAsc().stream()
                .map(entity -> entity.toDomain(objectMapper))
                .toList();
        return AuditHashChain.verify(ordered, AuditHashChain.GENESIS_HASH);
    }

    /**
     * A placeholder for an absent resource identifier or type.
     *
     * <p>{@code AuditEvent} requires both to be non-blank, and a denial legitimately has neither when
     * the thing being refused is a dashboard, a report or a collection rather than one record. This
     * guard used to test {@code == null} only, which was not enough: {@code FuelAccessPolicy} and
     * {@code DispatchAccessPolicy} build their denial details with {@code Map.of}, which rejects nulls,
     * so both substitute {@code ""} — and an empty string slipped past the null check and died in the
     * constructor. The denial itself was enforced and returned 403; the audit record of it was thrown
     * away and only logged. Twenty-seven call sites across fuel and dispatch were affected; fleet was
     * not, because its own details builder omits null keys rather than blanking them.
     *
     * <p>Guarding blankness here rather than in each policy is deliberate. This is where the invariant
     * is actually enforced, so it is the one place that cannot be forgotten by a policy written later.
     */
    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private AuditEvent append(String actorId, String actorDisplayName, SiteCode siteScope, AuditAction action,
            String resourceType, String resourceId, String beforeJson, String afterJson, String correlationId,
            SourceChannel channel) {
        AuditChainStateEntity head = chainState.lockChainHead()
                .orElseThrow(() -> new IllegalStateException(
                        "Audit chain head row is missing; V2__fleet_platform_foundation.sql did not run"));

        AuditEvent unsealed = AuditEvent.unsealed(UUID.randomUUID(), siteScope, actorId, actorDisplayName, action,
                resourceType, resourceId, beforeJson, afterJson, correlationId, channel, clock.instant());
        AuditEvent sealed = AuditHashChain.seal(unsealed, head.nextSequence(), head.headHash());

        auditRecords.save(AuditRecordEntity.from(sealed));
        head.advance(sealed.recordHash(), clock.instant());
        chainState.save(head);
        return sealed;
    }
}
