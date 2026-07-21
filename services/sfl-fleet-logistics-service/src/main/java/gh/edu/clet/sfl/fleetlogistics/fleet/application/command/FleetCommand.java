package gh.edu.clet.sfl.fleetlogistics.fleet.application.command;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;

/**
 * What every fleet command carries regardless of what it does: who is acting, through which channel,
 * and the idempotency key for retried state-creating requests.
 *
 * <p>The source channel is an explicit command input rather than something inferred inside a service,
 * because SRS-SFL-S166-01 and -03 both require it to be recorded accurately on the record and on the
 * audit entry.
 */
public interface FleetCommand {

    ActorContext actor();

    SourceChannel sourceChannel();

    /** {@code null} when the client did not supply an {@code Idempotency-Key}. */
    default String idempotencyKey() {
        return null;
    }
}
