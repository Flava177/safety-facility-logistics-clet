package gh.edu.clet.sfl.fleetlogistics.fleet.api.mapper;

import gh.edu.clet.sfl.fleetlogistics.fleet.api.response.FleetIntegrationResponses.InboxMessageResponse;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.IntegrationInboxMessage;
import org.springframework.stereotype.Component;

/** Maps integration domain objects to API DTOs. */
@Component
public class FleetIntegrationMapper {

    public InboxMessageResponse toResponse(IntegrationInboxMessage message) {
        return new InboxMessageResponse(message.id(), message.sourceSystem(), message.idempotencyKey(),
                message.eventType(), message.siteCode().value(), message.correlationId(), message.occurredAt(),
                message.payloadHash(), message.status(), message.attempts(), message.failureReason(),
                message.receivedAt(), message.processedAt());
    }
}
