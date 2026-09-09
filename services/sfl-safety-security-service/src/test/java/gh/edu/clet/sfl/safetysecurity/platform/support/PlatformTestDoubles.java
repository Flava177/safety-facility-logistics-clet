package gh.edu.clet.sfl.safetysecurity.platform.support;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.safetysecurity.platform.application.port.AuditPort;
import gh.edu.clet.sfl.safetysecurity.platform.application.port.IntegrationEventPublisher;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * In-memory doubles for the two ports every subdomain service shares ({@link AuditPort}, {@link
 * IntegrationEventPublisher}), following the idiom {@code sfl-fleet-logistics-service}'s {@code
 * fleet.support.FleetTestDoubles} uses: real, recording implementations rather than mocks, so a
 * service-layer unit test can assert exactly what was audited and published without a database.
 *
 * <p>Shared across {@code incident} and {@code visitor} test support rather than duplicated per
 * subdomain, because {@link AuditPort} and {@link IntegrationEventPublisher} are themselves shared
 * platform ports, not per-module ones - see {@code AuditAdapter}'s Javadoc. This is unlike {@code
 * RecordMetadata}, which each subdomain deliberately keeps its own copy of.
 */
public final class PlatformTestDoubles {

    private PlatformTestDoubles() {
    }

    /** Records every call rather than actually hashing a chain - the hash chain itself is tested elsewhere. */
    public static final class RecordingAuditPort implements AuditPort {

        private final List<Recorded> records = new ArrayList<>();

        @Override
        public void record(ActorContext actor, String sourceChannel, String siteScope, String action,
                String resourceType, String resourceId, Object beforeValue, Object afterValue, String reason) {
            records.add(new Recorded(actor, sourceChannel, siteScope, action, resourceType, resourceId, beforeValue,
                    afterValue, reason));
        }

        @Override
        public AuditVerification verifyChain() {
            return new AuditVerification(true, records.size(), null, null);
        }

        public List<Recorded> records() {
            return List.copyOf(records);
        }

        public boolean hasRecord(String action, String resourceType) {
            return records.stream().anyMatch(r -> r.action.equals(action) && r.resourceType.equals(resourceType));
        }

        public record Recorded(ActorContext actor, String sourceChannel, String siteScope, String action,
                String resourceType, String resourceId, Object beforeValue, Object afterValue, String reason) {
        }
    }

    /** Collects published events so a test can assert exactly what reached the outbox. */
    public static final class RecordingEventPublisher implements IntegrationEventPublisher {

        private final List<Published> published = new ArrayList<>();

        @Override
        public void publish(String eventType, int eventVersion, String aggregateType, String aggregateId,
                String siteScope, ActorContext actor, Map<String, Object> payload) {
            published.add(new Published(eventType, eventVersion, aggregateType, aggregateId, siteScope, actor,
                    payload));
        }

        public List<Published> published() {
            return List.copyOf(published);
        }

        public boolean hasEvent(String eventType) {
            return published.stream().anyMatch(p -> p.eventType.equals(eventType));
        }

        public record Published(String eventType, int eventVersion, String aggregateType, String aggregateId,
                String siteScope, ActorContext actor, Map<String, Object> payload) {
        }
    }
}
