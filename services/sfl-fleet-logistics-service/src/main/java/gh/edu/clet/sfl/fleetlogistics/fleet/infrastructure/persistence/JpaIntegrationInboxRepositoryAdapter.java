package gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.persistence;

import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.IntegrationInboxRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.IntegrationInboxMessage;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.IntegrationMessageStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** JPA adapter for the integration inbox. */
@Component
class JpaIntegrationInboxRepositoryAdapter implements IntegrationInboxRepository {

    private final IntegrationInboxMessageJpaRepository messages;

    JpaIntegrationInboxRepositoryAdapter(IntegrationInboxMessageJpaRepository messages) {
        this.messages = messages;
    }

    @Override
    @Transactional
    public IntegrationInboxMessage save(IntegrationInboxMessage message) {
        IntegrationInboxMessageEntity entity = messages.findById(message.id())
                .map(existing -> {
                    existing.applyFrom(message);
                    return existing;
                })
                .orElseGet(() -> IntegrationInboxMessageEntity.from(message));
        return messages.save(entity).toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<IntegrationInboxMessage> findById(UUID id) {
        return messages.findById(id).map(IntegrationInboxMessageEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<IntegrationInboxMessage> findBySourceAndIdempotencyKey(String sourceSystem,
            String idempotencyKey) {
        return messages.findBySourceSystemAndIdempotencyKey(sourceSystem, idempotencyKey)
                .map(IntegrationInboxMessageEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<IntegrationInboxMessage> findRecent(int limit) {
        return messages.findByOrderByReceivedAtDescIdDesc(PageRequest.of(0, Math.max(1, Math.min(limit, 200))))
                .stream()
                .map(IntegrationInboxMessageEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long countByStatus(IntegrationMessageStatus status) {
        return messages.countByStatus(status);
    }
}
