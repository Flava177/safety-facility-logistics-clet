package gh.edu.clet.sfl.safetysecurity.cctv.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A camera inventory reference and its current health - SRS-SFL-S161-01. Identity and health are kept
 * on one aggregate rather than split into two (contrast S160a's separate {@code AccessZone}/
 * {@code ReaderHealth}): a camera is inventoried once and its health merely changes over its life,
 * where a reader's health is reported far more often than a zone is redefined.
 *
 * @param locationRef the S152 facility-register reference for this camera, held by value - never a
 *        cross-schema foreign key; S152 is a different service's schema entirely.
 * @param examinationArea {@code true} if this camera covers an examination hall - carried on the
 *        published {@code CAMERA_HEALTH_CHANGED} event so a future readiness consumer can act on a
 *        coverage gap; S161 does not itself touch IFIMP's readiness scoring (see the implementation
 *        notes for why that stays a documented follow-up rather than a direct cross-service call).
 */
public record Camera(UUID id, String siteCode, String cameraId, String name, String locationRef,
        String coverageArea, boolean examinationArea, RecordingStatus recordingStatus,
        CameraHealthStatus healthStatus, Instant lastHealthAt, RecordMetadata metadata) {

    public Camera {
        Objects.requireNonNull(id, "id is required");
        require(siteCode, "siteCode");
        require(cameraId, "cameraId");
        require(name, "name");
        locationRef = blankToNull(locationRef);
        coverageArea = blankToNull(coverageArea);
        Objects.requireNonNull(recordingStatus, "recordingStatus is required");
        Objects.requireNonNull(healthStatus, "healthStatus is required");
        Objects.requireNonNull(lastHealthAt, "lastHealthAt is required");
        Objects.requireNonNull(metadata, "metadata is required");
    }

    public static Camera register(UUID id, String siteCode, String cameraId, String name, String locationRef,
            String coverageArea, boolean examinationArea, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        return new Camera(id, siteCode, cameraId, name, locationRef, coverageArea, examinationArea,
                RecordingStatus.UNKNOWN, CameraHealthStatus.ONLINE, at,
                RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    /** SRS-SFL-S161-01: health is "maintained as current device state" - each report replaces the last. */
    public Camera updateHealth(CameraHealthStatus status, RecordingStatus recording, Instant observedAt,
            String actorId, Instant at, SourceChannel channel, String correlationId) {
        return new Camera(id, siteCode, cameraId, name, locationRef, coverageArea, examinationArea, recording, status,
                observedAt, metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
