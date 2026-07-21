package gh.edu.clet.sfl.fleetlogistics.fleet.application.port;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.IntegrationInboxMessage;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.IntegrationMessageStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Inbound integration inbox persistence port (SRS-SFL-S166-04). */
public interface IntegrationInboxRepository {

    IntegrationInboxMessage save(IntegrationInboxMessage message);

    Optional<IntegrationInboxMessage> findById(UUID id);

    Optional<IntegrationInboxMessage> findBySourceAndIdempotencyKey(String sourceSystem, String idempotencyKey);

    List<IntegrationInboxMessage> findRecent(int limit);

    long countByStatus(IntegrationMessageStatus status);
}
