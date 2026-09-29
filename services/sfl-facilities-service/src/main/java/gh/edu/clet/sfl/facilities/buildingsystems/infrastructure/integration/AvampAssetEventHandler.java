package gh.edu.clet.sfl.facilities.buildingsystems.infrastructure.integration;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.common.security.SiteScopedPrincipal;
import gh.edu.clet.sfl.facilities.buildingsystems.application.AvampAssetProjectionService;
import gh.edu.clet.sfl.facilities.shared.application.integration.InboundIntegrationEvent;
import gh.edu.clet.sfl.facilities.shared.application.integration.IntegrationEventHandler;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * AVAMP (FTLMP) says an asset was registered or moved; S156 updates its projection - SRS-SFL-S156-04.
 *
 * <p>The body is FTLMP's {@code AssetReference} serialised whole by {@code AssetVisibilityService.record}:
 * {@code id, assetCode, name, category, status, siteCode, locationType, locationReference, ... updatedAt}.
 * The AVAMP asset id S156 keys on is {@code id} - AVAMP's stable identifier - falling back to the
 * envelope's aggregate id, which FTLMP sets to the same value.
 *
 * <p>AVAMP-Lite publishes no retirement, firmware, warranty or calibration facts, so none are projected;
 * S156 holds them on the device (see {@code BmsDevice}). A payload with no asset id or site cannot be
 * projected and will never become projectable, so it is logged and dropped rather than thrown - throwing
 * would redeliver it forever.
 */
@Component
public class AvampAssetEventHandler implements IntegrationEventHandler {

    private static final Logger log = LoggerFactory.getLogger(AvampAssetEventHandler.class);

    static final String REGISTERED = "sfl.avamp.asset-registered.v1";
    static final String LOCATION_CHANGED = "sfl.avamp.asset-location-changed.v1";

    private static final SiteScopedPrincipal SYSTEM = new SiteScopedPrincipal("system.avamp-integration",
            "AVAMP integration", Set.of(SflRole.SFL_ADMIN), Set.of("*"), true);

    private final AvampAssetProjectionService projection;

    public AvampAssetEventHandler(AvampAssetProjectionService projection) {
        this.projection = projection;
    }

    @Override
    public boolean handles(String eventType) {
        return REGISTERED.equals(eventType) || LOCATION_CHANGED.equals(eventType);
    }

    @Override
    public void handle(InboundIntegrationEvent event) {
        String assetId = event.text("id") != null ? event.text("id") : event.aggregateId();
        String siteCode = event.text("siteCode") != null ? event.text("siteCode") : event.siteCode();
        if (assetId == null || siteCode == null || siteCode.isBlank() || "*".equals(siteCode)) {
            log.error("{} lacks an asset id or a site code; the AVAMP projection cannot be updated", event.eventType());
            return;
        }
        projection.apply(new AvampAssetProjectionService.AvampAssetFact(event.eventType(), assetId, siteCode,
                event.text("assetCode"), event.text("name"), event.text("category"), event.text("status"),
                event.text("locationType"), event.text("locationReference"), instant(event.text("updatedAt"))),
                new ActorContext(SYSTEM, event.correlationId() == null ? UUID.randomUUID().toString()
                        : event.correlationId()));
    }

    /** FTLMP serialises instants as ISO text; an absent or odd value orders as "now-unknown" (applied). */
    private static Instant instant(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException notIso) {
            try {
                // Jackson's numeric timestamp form: seconds with a fraction.
                return Instant.ofEpochMilli((long) (Double.parseDouble(raw) * 1000));
            } catch (NumberFormatException unreadable) {
                return null;
            }
        }
    }
}
