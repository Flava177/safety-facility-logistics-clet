package gh.edu.clet.sfl.facilities.construction.infrastructure.integration;

import gh.edu.clet.sfl.facilities.construction.application.ConstructionEvents;
import gh.edu.clet.sfl.facilities.construction.application.PermitProjectionService;
import gh.edu.clet.sfl.facilities.construction.domain.PermitNotice;
import gh.edu.clet.sfl.facilities.construction.domain.PermitRecord;
import gh.edu.clet.sfl.facilities.shared.application.integration.InboundIntegrationEvent;
import gh.edu.clet.sfl.facilities.shared.application.integration.IntegrationEventHandler;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * S164 (SSEMP) says something happened to a permit; S176 updates the projection its start gate reads.
 *
 * <p>Bound to the four reserved names {@code sfl.ssemp.permit-issued.v1}, {@code -suspended.v1},
 * {@code -extended.v1} and {@code -closed.v1}; the inbound queue already binds {@code ssemp.#}.
 * <strong>Nothing publishes these yet</strong> - S164 is not built - so in this release the handler is
 * wired and idle, and every permit-requiring project is refused the start. That is the fail-closed
 * outcome, and the S176 runbook says how to tell it from a broken consumer.
 *
 * <h2>Schema validation, and what a bad payload does</h2>
 *
 * The expected payload is in {@code docs/facilities/S176_Event_Contracts.md}. A payload missing a
 * required field, or with an unparseable timestamp, is logged at ERROR and dropped - never applied and
 * never thrown, because retrying a malformed message will not make the field appear and throwing
 * would loop it forever (the {@link IntegrationEventHandler} contract). A partially applied "issued"
 * would be worse than none: a permit with no validity window cannot be current, and guessing one
 * would authorise hot work on a guess.
 */
@Component
public class PermitEventHandler implements IntegrationEventHandler {

    private static final Logger log = LoggerFactory.getLogger(PermitEventHandler.class);

    private static final Map<String, PermitNotice.Kind> KINDS = Map.of(
            ConstructionEvents.PERMIT_ISSUED, PermitNotice.Kind.ISSUED,
            ConstructionEvents.PERMIT_SUSPENDED, PermitNotice.Kind.SUSPENDED,
            ConstructionEvents.PERMIT_EXTENDED, PermitNotice.Kind.EXTENDED,
            ConstructionEvents.PERMIT_CLOSED, PermitNotice.Kind.CLOSED);

    private final PermitProjectionService projection;

    public PermitEventHandler(PermitProjectionService projection) {
        this.projection = projection;
    }

    @Override
    public boolean handles(String eventType) {
        return KINDS.containsKey(eventType);
    }

    @Override
    public void handle(InboundIntegrationEvent event) {
        PermitNotice notice;
        try {
            notice = parse(event);
        } catch (FacilitiesException | DateTimeParseException | IllegalArgumentException invalid) {
            log.error("{} message {} rejected by S176 schema validation and not applied: {}", event.eventType(),
                    event.messageId(), invalid.getMessage());
            return;
        }
        PermitRecord applied = projection.apply(notice, event.correlationId());
        log.info("Permit {} at {} is now {} (from {})", applied.permitId(), applied.siteCode(), applied.status(),
                event.eventType());
    }

    /** Package-visible so the schema rules can be tested without a broker. */
    static PermitNotice parse(InboundIntegrationEvent event) {
        String permitId = event.text("permitId") != null ? event.text("permitId") : event.aggregateId();
        String siteCode = event.siteCode() != null ? event.siteCode() : event.text("siteCode");
        String occurred = event.text("occurredAt");
        return new PermitNotice(KINDS.get(event.eventType()), event.eventType(), event.messageId(), permitId,
                event.text("permitReference"), siteCode, event.text("workType"), instant(event.text("validFrom")),
                instant(event.text("validTo")), event.text("contractorReference"), event.text("originReference"),
                occurred == null ? null : Instant.parse(occurred));
    }

    private static Instant instant(String value) {
        return value == null || value.isBlank() ? null : Instant.parse(value);
    }
}
