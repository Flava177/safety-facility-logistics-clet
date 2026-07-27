package gh.edu.clet.sfl.fleetlogistics.dispatch.application.port;

import java.util.Optional;
import java.util.UUID;

/**
 * Phase-2 GPS/telematics seam. Provider-neutral; the Phase-1 recorded adapter returns no live track.
 * Present so the custody/route view can later attach GPS provenance without a domain change.
 */
public interface GpsTrackReferencePort {

    /** Latest known track reference for the carrying trip, if any. Empty in Phase 1. */
    Optional<String> latestTrackReference(UUID tripId);
}
