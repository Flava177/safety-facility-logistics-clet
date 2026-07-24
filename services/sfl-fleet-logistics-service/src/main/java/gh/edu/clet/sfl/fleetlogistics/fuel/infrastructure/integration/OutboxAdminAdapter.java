package gh.edu.clet.sfl.fleetlogistics.fuel.infrastructure.integration;

import gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.messaging.OutboxDrainer;
import gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.messaging.OutboxMessageEntity;
import gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.messaging.OutboxMessageRepository;
import gh.edu.clet.sfl.fleetlogistics.fuel.application.port.FuelOutboxAdminPort;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/** Adapts the shared outbox repository/drainer to the fuel integration admin port. */
@Component
public class OutboxAdminAdapter implements FuelOutboxAdminPort {

    private final OutboxMessageRepository outbox;
    private final OutboxDrainer drainer;

    public OutboxAdminAdapter(OutboxMessageRepository outbox, OutboxDrainer drainer) {
        this.outbox = outbox;
        this.drainer = drainer;
    }

    @Override
    public OutboxHealth health() {
        List<OutboxEntry> deadLetters = outbox
                .findByStatusOrderByCreatedAtDesc(OutboxMessageEntity.STATUS_DEAD_LETTERED, PageRequest.of(0, 20))
                .stream()
                .map(m -> new OutboxEntry(m.id(), m.eventType(), m.aggregateType(), m.aggregateId(), m.status(),
                        m.attemptCount(), m.failureReason(), m.createdAt()))
                .toList();
        return new OutboxHealth(outbox.countByStatus(OutboxMessageEntity.STATUS_PENDING),
                outbox.countByStatus(OutboxMessageEntity.STATUS_PUBLISHED),
                outbox.countByStatus(OutboxMessageEntity.STATUS_DEAD_LETTERED), deadLetters);
    }

    @Override
    public boolean replay(UUID messageId) {
        return drainer.requeue(messageId);
    }
}
