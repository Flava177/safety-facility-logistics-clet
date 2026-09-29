package gh.edu.clet.sfl.facilities.spaceplanning.application.ports;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Read-only S159 utilisation, as S158 needs it - SRS-SFL-S158-03.
 *
 * <p>"Utilisation data is read-only from S158's perspective - S158 never writes back into S159's booking
 * records." This port has one method and it reads. Its adapter is the only S158 class allowed to name an
 * S159 type, and only {@code BookingUtilisationReader} and its record - {@code SpacePlanningArchitectureTest}
 * fails the build otherwise, so the rule does not rest on review.
 */
public interface BookingUtilisationPort {

    /**
     * @return one entry per room with any booking in {@code [from, to)}; a room with none is absent and
     *         the caller reads that as zero use
     * @throws UtilisationUnavailableException when S159 cannot be read
     */
    List<ObservedUtilisation> utilisation(String siteCode, Instant from, Instant to);

    record ObservedUtilisation(UUID roomId, String roomCode, int bookingCount, int takenUpCount, int noShowCount,
            long bookedMinutes, long usedMinutes, long totalExpectedAttendees) {
    }

    class UtilisationUnavailableException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public UtilisationUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
