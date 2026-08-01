package gh.edu.clet.sfl.fleetlogistics.fleet.application.service;

import java.util.UUID;

/**
 * How much of the fleet a signed-in actor is allowed to see.
 *
 * <h2>Why this is a sealed type and not a nullable UUID</h2>
 *
 * <p>There are three answers, and the third is the one that gets forgotten. A supervising actor sees
 * everything their site scope allows; a driver bound to a profile sees that driver's records; and a
 * driver bound to <em>no</em> profile sees nothing at all. Expressed as a {@code UUID driverId} that
 * is sometimes null, the first and third cases have the same representation — and "no driver filter"
 * read as "no narrowing" is precisely how an unbound driver ends up seeing every trip at their site.
 * That is not a hypothetical: it is the shape the fuel logbook filter already has, where {@code ownOnly}
 * false and {@code actorId} null are indistinguishable at the SQL layer.
 *
 * <p>Sealed, so the set of answers is closed and a new one cannot be added from outside this package
 * without the compiler pointing at every place that handles them. (An exhaustive {@code switch} would
 * make that a compile error rather than a review comment, but pattern switches are a preview feature
 * on this project's Java 17 — so call sites test with {@code instanceof} and default to refusing.)
 *
 * <h2>The identity provider question</h2>
 *
 * <p>{@link Own} carries the driver's own {@code UUID}, resolved once from the actor's subject through
 * the {@code principal_subject} binding on the driver register. Nothing downstream compares a token
 * claim to a staff reference, which is what makes this survive the move to Zitadel: the subject format
 * changes, the binding row changes with it, and every query below this line is unaffected.
 */
public sealed interface DriverScope {

    /** The actor supervises: no per-driver narrowing, site scope still applies. */
    record Everything() implements DriverScope {
    }

    /** A driver bound to a profile. {@code driverId} is the only driver whose records they may see. */
    record Own(UUID driverId, String staffReference) implements DriverScope {
    }

    /**
     * A driver-only actor with no driver profile bound to their identity.
     *
     * <p>They see nothing, which is a deliberate decision and not a degradation: a driver whose
     * binding is missing or mis-set is an administrative problem, and the alternative reading — show
     * them their whole site meanwhile — hands one person every other driver's movements.
     *
     * <p>{@code reason} is carried so the interface can say why the list is empty rather than
     * presenting an unbound driver with a blank screen that looks like "you have no trips today".
     */
    record Nothing(String reason) implements DriverScope {
    }

    /** Convenience for the query layer: the driver id to filter on, or null when unrestricted. */
    default UUID driverIdFilter() {
        return this instanceof Own own ? own.driverId() : null;
    }

    /** True when the scope resolves to no records at all, so a query need not be run. */
    default boolean isEmpty() {
        return this instanceof Nothing;
    }
}
