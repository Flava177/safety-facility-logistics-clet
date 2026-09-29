package gh.edu.clet.sfl.facilities.spaceplanning.support;

import gh.edu.clet.sfl.facilities.spaceplanning.application.ports.BookingUtilisationPort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A double for {@code BookingUtilisationReader} (S159), so the S158-03 reconciliation tests control what
 * "was observed" without a real booking diary. A room absent from {@link #utilisation} is what the real
 * reader does for a room with no bookings - so the reconciliation service reads it as zero use.
 */
public class FakeBookingUtilisationPort implements BookingUtilisationPort {

    public final List<ObservedUtilisation> rows = new ArrayList<>();
    private boolean unavailable;

    public void set(ObservedUtilisation... observed) {
        rows.clear();
        rows.addAll(List.of(observed));
    }

    public void makeUnavailable() {
        unavailable = true;
    }

    @Override
    public List<ObservedUtilisation> utilisation(String siteCode, Instant from, Instant to) {
        if (unavailable) {
            throw new UtilisationUnavailableException("S159 is not available in this test", null);
        }
        return List.copyOf(rows);
    }
}
