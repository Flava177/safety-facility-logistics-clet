package gh.edu.clet.sfl.emergencynotification.domain.model;

import java.util.Objects;
import java.util.UUID;

/** A building/room/zone recipient scope, referenced by ID into the facilities location model. */
public record RecipientZone(UUID id, String zoneCode, SiteCode siteCode, String name, String locationReference,
        RecordLifecycle lifecycle, RecordMetadata metadata) {

    public RecipientZone {
        Objects.requireNonNull(id);
        Objects.requireNonNull(siteCode);
        Objects.requireNonNull(lifecycle);
        Objects.requireNonNull(metadata);
        zoneCode = require(zoneCode, "zoneCode");
        name = require(name, "name");
    }

    public boolean active() {
        return lifecycle.isActive();
    }

    public RecipientZone withLifecycle(RecordLifecycle next, RecordMetadata changed) {
        return new RecipientZone(id, zoneCode, siteCode, name, locationReference, next, changed);
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.strip();
    }
}
