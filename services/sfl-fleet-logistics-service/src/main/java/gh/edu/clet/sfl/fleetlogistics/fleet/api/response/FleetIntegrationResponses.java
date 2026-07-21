package gh.edu.clet.sfl.fleetlogistics.fleet.api.response;

import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.FleetIntegrationApplicationService.IntegrationHealth;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.FleetIntegrationApplicationService.IntegrationMessageSummary;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.IntegrationMessageStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleLocationSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Response DTOs for secure integrations and vehicle movement. */
public final class FleetIntegrationResponses {

    private FleetIntegrationResponses() {
    }

    public record InboxMessageResponse(
            UUID id,
            String sourceSystem,
            String idempotencyKey,
            String eventType,
            String siteCode,
            String correlationId,
            Instant occurredAt,
            String payloadHash,
            IntegrationMessageStatus status,
            int attempts,
            String failureReason,
            Instant receivedAt,
            Instant processedAt) {
    }

    public record IntegrationHealthResponse(
            Instant checkedAt,
            long processedMessages,
            long rejectedMessages,
            long deadLetterMessages,
            List<IntegrationMessageSummary> recentMessages) {

        public static IntegrationHealthResponse from(IntegrationHealth health) {
            return new IntegrationHealthResponse(health.checkedAt(), health.processedMessages(),
                    health.rejectedMessages(), health.deadLetterMessages(), health.recentMessages());
        }
    }

    public record VehicleLocationResponse(
            UUID id,
            UUID vehicleId,
            String siteCode,
            BigDecimal latitude,
            BigDecimal longitude,
            Long odometerValue,
            Instant recordedAt,
            String sourceSystem,
            UUID integrationMessageId,
            String correlationId) {

        public static VehicleLocationResponse from(VehicleLocationSnapshot snapshot) {
            return new VehicleLocationResponse(snapshot.id(), snapshot.vehicleId(), snapshot.siteCode().value(),
                    snapshot.latitude(), snapshot.longitude(), snapshot.odometerValue(), snapshot.recordedAt(),
                    snapshot.sourceSystem(), snapshot.integrationMessageId(), snapshot.correlationId());
        }
    }
}
