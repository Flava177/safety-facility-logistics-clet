package gh.edu.clet.sfl.safetysecurity.visitor.infrastructure.integration;

import gh.edu.clet.sfl.safetysecurity.visitor.application.port.WatchlistCheckPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Phase-1 recorded watchlist check. No watchlist vendor is configured, so it honestly reports "not
 * flagged" for every visitor rather than fabricating a screen - the same provider-neutral, honest
 * stance {@code RecordedNotificationGateway} takes for S174. A real watchlist/restriction product
 * replaces this without any domain change.
 *
 * <p>Deliberately not fail-closed: refusing to start (or refusing every visitor) because no vendor is
 * wired up would be a self-inflicted outage over a known, intentional Phase-1 limitation, not a bug -
 * see the audit note this class was flagged in. What it must not do is run unnoticed, so it logs the
 * same loud, startup-time warning {@code SafetySecurityStartupReporter} logs its own banner with -
 * same {@code @EventListener(ApplicationReadyEvent.class)} mechanism, so it appears once per boot
 * regardless of which profile is active (there is no "production" guard here because there is, as yet,
 * no other {@link WatchlistCheckPort} implementation to prefer over this one in any profile).
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

    @EventListener(ApplicationReadyEvent.class)
    void warnOnStartup() {
        log.warn("");
        log.warn("  ################################################################################");
        log.warn("  #  WATCHLIST SCREENING IS NOT CONFIGURED (RecordedWatchlistGateway)             #");
        log.warn("  #  Every visitor is being recorded as \"not flagged\" - no real watchlist/         #");
        log.warn("  #  restriction check is being performed. This is a known Phase-1 limitation,     #");
        log.warn("  #  not a bug, but it must not go unnoticed if this is a production deployment.   #");
        log.warn("  ################################################################################");
        log.warn("");
    }
}
