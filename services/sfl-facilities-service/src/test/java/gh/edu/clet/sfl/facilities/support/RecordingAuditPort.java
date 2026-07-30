package gh.edu.clet.sfl.facilities.support;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.shared.application.port.AuditPort;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditAction;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditChainVerification;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditEvent;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditHashChain;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * An audit port that keeps its records in a list and seals them with the real chain.
 *
 * <p>Sealing for real matters: a test double that skipped hashing would let a chain-breaking change
 * pass unnoticed here and fail only in the integration test.
 */
public class RecordingAuditPort implements AuditPort {

    private final List<AuditEvent> events = new ArrayList<>();
    private final Instant fixedNow;

    public RecordingAuditPort(Instant fixedNow) {
        this.fixedNow = fixedNow;
    }

    public List<AuditEvent> events() {
        return List.copyOf(events);
    }

    /** Every recorded action, in order — the shape most assertions want. */
    public List<AuditAction> actions() {
        return events.stream().map(AuditEvent::action).toList();
    }

    public boolean recorded(AuditAction action) {
        return events.stream().anyMatch(event -> event.action() == action);
    }

    public long countOf(AuditAction action) {
        return events.stream().filter(event -> event.action() == action).count();
    }

    public void clear() {
        events.clear();
    }

    @Override
    public AuditEvent record(ActorContext actor, SourceChannel channel, AuditAction action, String resourceType,
            String resourceId, String siteScope, Object before, Object after) {
        return append(AuditEvent.of(UUID.randomUUID(), siteScope == null ? "*" : siteScope, actor.actorId(),
                actor.principal().displayName(), action, resourceType, resourceId,
                before == null ? null : before.toString(), after == null ? null : after.toString(),
                actor.correlationId(), channel, fixedNow));
    }

    @Override
    public AuditEvent recordDenial(ActorContext actor, SourceChannel channel, String resourceType,
            String resourceId, String siteScope, String reason) {
        return append(AuditEvent.of(UUID.randomUUID(), siteScope == null ? "*" : siteScope, actor.actorId(),
                actor.principal().displayName(), AuditAction.AUTHORIZATION_DENIED, resourceType,
                resourceId == null || resourceId.isBlank() ? "-" : resourceId, null, reason,
                actor.correlationId(), channel, fixedNow));
    }

    @Override
    public List<AuditEvent> search(String siteScope, String resourceType, String resourceId, String actorId,
            AuditAction action, Instant from, Instant to, int limit) {
        return events.stream()
                .filter(event -> siteScope == null || siteScope.equals(event.siteScope()))
                .filter(event -> resourceType == null || resourceType.equals(event.resourceType()))
                .filter(event -> action == null || action == event.action())
                .limit(Math.max(1, limit))
                .toList();
    }

    @Override
    public AuditChainVerification verifyChain() {
        return AuditHashChain.verify(events, AuditHashChain.GENESIS_HASH);
    }

    @Override
    public List<AuditEvent> historyFor(String resourceType, UUID resourceId) {
        return events.stream()
                .filter(event -> event.resourceType().equals(resourceType)
                        && event.resourceId().equals(resourceId.toString()))
                .toList();
    }

    private AuditEvent append(AuditEvent event) {
        String previous = events.isEmpty()
                ? AuditHashChain.GENESIS_HASH
                : events.get(events.size() - 1).recordHash();
        AuditEvent sealed = AuditHashChain.seal(event, events.size(), previous);
        events.add(sealed);
        return sealed;
    }
}
