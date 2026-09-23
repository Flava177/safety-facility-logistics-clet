package gh.edu.clet.sfl.safetysecurity.cctv.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A logged live-viewing session - SRS-SFL-S161-04: "authorised, role-restricted live viewing of
 * permitted cameras through the VMS, with every live-view session logged (operator, cameras, time)."
 * SFL logs the session; the VMS is the one actually streaming the video.
 */
public record LiveViewSession(UUID id, String siteCode, String operatorId, List<String> cameraIds, Instant startedAt,
        Instant endedAt) {

    public LiveViewSession {
        Objects.requireNonNull(id, "id is required");
        require(siteCode, "siteCode");
        require(operatorId, "operatorId");
        if (cameraIds == null || cameraIds.isEmpty()) {
            throw new IllegalArgumentException("cameraIds is required");
        }
        cameraIds = List.copyOf(cameraIds);
        Objects.requireNonNull(startedAt, "startedAt is required");
    }

    public static LiveViewSession start(UUID id, String siteCode, String operatorId, List<String> cameraIds,
            Instant startedAt) {
        return new LiveViewSession(id, siteCode, operatorId, cameraIds, startedAt, null);
    }

    public LiveViewSession end(Instant endedAt) {
        return new LiveViewSession(id, siteCode, operatorId, cameraIds, startedAt, endedAt);
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
