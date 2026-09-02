package gh.edu.clet.sfl.safetysecurity.visitor.application.port;

/**
 * The "watchlist or restriction check" step in the SRS workflow (§11.3). No real watchlist vendor is
 * chosen in this build, so this stands in for one - see {@code RecordedWatchlistGateway}, which mirrors
 * S174's {@code RecordedNotificationGateway}: honest about not checking anything real, swappable
 * without any domain change once a vendor exists.
 */
public interface WatchlistCheckPort {

    WatchlistResult check(String visitorName, String visitorContact, String siteCode);

    record WatchlistResult(boolean flagged, String reason) {
    }
}
