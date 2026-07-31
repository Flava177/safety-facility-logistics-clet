package gh.edu.clet.sfl.assetvisibility.application;

import java.util.regex.Pattern;

/**
 * The one shape every AVAMP integration event name may take.
 *
 * <p>{@code docs/integration/event-catalog.md} defines the rule as
 * {@code sfl.{platform}.{event-name}.v{version}}. This service published
 * {@code sfl.asset.asset-registered} — the right prefix, the wrong platform token ({@code asset}
 * rather than {@code avamp}) and no version suffix — so a consumer binding {@code sfl.avamp.*.v1}
 * received nothing, and could not distinguish that from an estate with no asset movements.
 *
 * <p>Enforced at the outbox rather than asserted over a list of names, for the same reason as the
 * facilities copy of this class: a list has to be remembered, and the write path cannot be avoided.
 * The duplication between the two services is deliberate — shared kernel code lives in
 * {@code sfl-service-common}, and this rule is small enough that copying it costs less than coupling
 * two independently deployable services through it.
 */
public final class ServiceEventType {

    /** Matches the catalogue rule. The event-name segment takes hyphens, never dots or underscores. */
    private static final Pattern CANONICAL = Pattern.compile("^sfl\\.[a-z0-9]+\\.[a-z0-9-]+\\.v(\\d+)$");

    private ServiceEventType() {
    }

    /** Fails fast on a name that breaks the catalogue, or whose version contradicts {@code eventVersion}. */
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
