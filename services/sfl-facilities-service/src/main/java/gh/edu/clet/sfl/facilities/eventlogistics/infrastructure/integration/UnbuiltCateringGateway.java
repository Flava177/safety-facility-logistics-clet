package gh.edu.clet.sfl.facilities.eventlogistics.infrastructure.integration;

import gh.edu.clet.sfl.facilities.eventlogistics.application.ports.CateringGatewayPort;
import gh.edu.clet.sfl.facilities.eventlogistics.application.ports.GatewayOutcome;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * S172 Catering &amp; Cafeteria Management is Phase 3 (SRS 3.6) and not built. This adapter says so by
 * answering empty - there is no system to ask - and S173 records a manual coordination item.
 *
 * <p>It never answers "requested" or "confirmed": a catering request that looked like a system request
 * is exactly what S173-02's acceptance criterion forbids. The S172 build replaces this class and flips
 * {@code event-logistics.owning-system.S172.available}.
 */
@Component
public class UnbuiltCateringGateway implements CateringGatewayPort {

    @Override
    public Optional<GatewayOutcome> request(CateringRequest request) {
        return Optional.empty();
    }
}
