package gh.edu.clet.sfl.fleetlogistics.dispatch.infrastructure.integration;

import gh.edu.clet.sfl.fleetlogistics.dispatch.application.port.GpsTrackReferencePort;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Phase-2 GPS/telematics seam. The Phase-1 recorded adapter returns no live track; a real telematics
 * integration replaces this class without any domain change, so the custody/route view can later attach
 * GPS provenance for the carrying trip.
 */
@Component
public class RecordedGpsTrackReferenceAdapter implements GpsTrackReferencePort {

    @Override
    public Optional<String> latestTrackReference(UUID tripId) {
        return Optional.empty();
    }
}
