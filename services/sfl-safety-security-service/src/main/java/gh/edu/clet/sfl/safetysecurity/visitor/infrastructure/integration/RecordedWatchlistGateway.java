package gh.edu.clet.sfl.safetysecurity.visitor.infrastructure.integration;

import gh.edu.clet.sfl.safetysecurity.visitor.application.port.WatchlistCheckPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Phase-1 recorded watchlist check. No watchlist vendor is configured, so it honestly reports "not
 * flagged" for every visitor rather than fabricating a screen - the same provider-neutral, honest
 * stance {@code RecordedNotificationGateway} takes for S174. A real watchlist/restriction product
 * replaces this without any domain change.
 */
@Component
public class RecordedWatchlistGateway implements WatchlistCheckPort {

    private static final Logger log = LoggerFactory.getLogger(RecordedWatchlistGateway.class);

    @Override
    public WatchlistResult check(String visitorName, String visitorContact, String siteCode) {
        log.info("Recorded watchlist check visitor={} site={} - no watchlist provider configured, not flagged",
                visitorName, siteCode);
        return new WatchlistResult(false, null);
    }
}
