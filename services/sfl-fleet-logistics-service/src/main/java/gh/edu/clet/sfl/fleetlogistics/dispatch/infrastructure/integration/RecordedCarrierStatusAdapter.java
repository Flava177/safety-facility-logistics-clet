package gh.edu.clet.sfl.fleetlogistics.dispatch.infrastructure.integration;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.fleetlogistics.dispatch.application.port.CarrierStatusPort;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Phase-1 recorded courier-carrier status adapter. Provider-neutral: it records the neutral status fact
 * against the dispatch without any live vendor coupling. A real carrier-API adapter replaces this class
 * without any domain change; vendor DTOs would live only inside such an adapter.
 */
@Component
public class RecordedCarrierStatusAdapter implements CarrierStatusPort {

    private static final Logger log = LoggerFactory.getLogger(RecordedCarrierStatusAdapter.class);

    @Override
    public void recordCarrierStatus(UUID dispatchId, String carrier, String status, Instant occurredAt,
            ActorContext actor, SourceChannel sourceChannel) {
        log.info("Recorded carrier status dispatch={} carrier={} status={} occurredAt={} channel={}",
                dispatchId, carrier, status, occurredAt, sourceChannel);
    }
}
