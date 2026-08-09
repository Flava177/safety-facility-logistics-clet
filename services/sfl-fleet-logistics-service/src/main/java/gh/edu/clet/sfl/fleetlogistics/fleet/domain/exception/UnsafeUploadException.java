package gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception;

import java.util.Map;

/**
 * A file the platform will not store.
 *
 * <p>The message is deliberately specific - which check failed, and what to do about it - because the
 * person who hits this is usually holding a perfectly legitimate document in a format nobody told
 * them about. A flat "upload failed" turns that into a support call.
 *
 * <p>It is specific but not diagnostic: nothing here echoes file contents back, and the reason names
 * the construct that was found rather than where it was found.
 */
public class UnsafeUploadException extends FleetDomainException {

    public UnsafeUploadException(Map<String, Object> details) {
        super(FleetErrorCode.FLEET_UPLOAD_REJECTED,
                String.valueOf(details.getOrDefault("reason", FleetErrorCode.FLEET_UPLOAD_REJECTED.message())),
                details);
    }
}
