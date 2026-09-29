package gh.edu.clet.sfl.facilities.spaceplanning.infrastructure.integration;

import gh.edu.clet.sfl.facilities.booking.application.BookingUtilisationReader;
import gh.edu.clet.sfl.facilities.spaceplanning.application.ports.BookingUtilisationPort;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * S158's read of S159 utilisation - the only S158 class that names a booking type, and it names only
 * {@link BookingUtilisationReader} and its {@code RoomUtilisation} record (SRS-SFL-S158-03 "S158 never
 * writes back into S159's booking records"). {@code SpacePlanningArchitectureTest} holds both halves.
 *
 * <p>An in-process call to the real S159 service - both modules are in this deployable, so there is
 * nothing to simulate. A failure is rethrown as the port's unavailable exception so the application
 * handles "S159 could not be read" as one case rather than every possible runtime failure.
 */
@Component
public class S159BookingUtilisationAdapter implements BookingUtilisationPort {

    private final BookingUtilisationReader reader;

    public S159BookingUtilisationAdapter(BookingUtilisationReader reader) {
        this.reader = reader;
    }

    @Override
    public List<ObservedUtilisation> utilisation(String siteCode, Instant from, Instant to) {
        try {
            return reader.utilisation(siteCode, from, to).stream()
                    .map(room -> new ObservedUtilisation(room.roomId(), room.roomCode(), room.bookingCount(),
                            room.takenUpCount(), room.noShowCount(), room.bookedMinutes(), room.usedMinutes(),
                            room.totalExpectedAttendees()))
                    .toList();
        } catch (RuntimeException failure) {
            throw new UtilisationUnavailableException("S159 utilisation could not be read for " + siteCode, failure);
        }
    }
}
