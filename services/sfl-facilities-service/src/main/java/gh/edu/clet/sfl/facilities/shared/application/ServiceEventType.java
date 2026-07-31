package gh.edu.clet.sfl.facilities.shared.application;

import java.util.regex.Pattern;

/**
 * The one shape every IFIMP integration event name may take.
 *
 * <p>{@code docs/integration/event-catalog.md} defines the rule as
 * {@code sfl.{platform}.{event-name}.v{version}}, and the routing key a consumer binds is derived
 * from it. This service published {@code ifimp.work-order.assigned} instead — no {@code sfl.} prefix,
 * no version suffix, and a dot between the aggregate and the verb where the rule allows only one dot
 * either side of the event name. A consumer binding {@code sfl.ifimp.*.v1} would have received
 * nothing at all, and would have had no way to tell that from a quiet week.
 *
 * <p>Enforced here, at the outbox, rather than asserted over a list of known names. A list has to be
 * remembered; the write path cannot be avoided. {@code FleetEventTypeTest} asserts the same regex over
 * fleet's enum, which works because fleet routes every event through one enum — facilities publishes
 * string literals from fifty call sites, so the check belongs where they converge.
 *
 * <p>The version in the name and the {@code eventVersion} column are two statements of one fact, so
 * {@link #require} rejects a disagreement between them rather than letting a consumer that trusts the
 * routing key and a consumer that trusts the column disagree about what they are reading.
 */
public final class ServiceEventType {

    /** Matches the catalogue rule. The event-name segment takes hyphens, never dots or underscores. */
    private static final Pattern CANONICAL = Pattern.compile("^sfl\\.[a-z0-9]+\\.[a-z0-9-]+\\.v(\\d+)$");

    private ServiceEventType() {
    }

    /**
     * Fails fast on an event name that does not match the catalogue, or whose version contradicts the
     * {@code eventVersion} being stored alongside it.
     *
     * @throws IllegalArgumentException so the defect surfaces in the test that publishes it, which is
     *         cheaper than a consumer discovering it as silence on a queue
     */
    public static void require(String eventType, int eventVersion) {
        if (eventType == null || !CANONICAL.matcher(eventType).matches()) {
            throw new IllegalArgumentException("Event type '" + eventType
                    + "' does not match the catalogue rule sfl.{platform}.{event-name}.v{version}");
        }
        int declared = Integer.parseInt(eventType.substring(eventType.lastIndexOf(".v") + 2));
        if (declared != eventVersion) {
            throw new IllegalArgumentException("Event type '" + eventType + "' declares v" + declared
                    + " but is being stored with eventVersion " + eventVersion);
        }
    }
}
