package gh.edu.clet.sfl.emergencynotification.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * A named audience of recipients. Recipient contact detail is held by reference (directory) and masked
 * unless the role permits viewing; {@code recipientCount} is the sizing used for delivery/ack reconciliation.
 */
public record AudienceGroup(UUID id, String groupCode, SiteCode siteCode, String name, String directoryReference,
        int recipientCount, RecordLifecycle lifecycle, RecordMetadata metadata) {

    public AudienceGroup {
        Objects.requireNonNull(id);
        Objects.requireNonNull(siteCode);
        Objects.requireNonNull(lifecycle);
        Objects.requireNonNull(metadata);
        groupCode = require(groupCode, "groupCode");
        name = require(name, "name");
        if (recipientCount < 0) {
            throw new IllegalArgumentException("recipientCount cannot be negative");
        }
    }

    public boolean active() {
        return lifecycle.isActive();
    }

    public AudienceGroup withLifecycle(RecordLifecycle next, RecordMetadata changed) {
        return new AudienceGroup(id, groupCode, siteCode, name, directoryReference, recipientCount, next, changed);
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.strip();
    }
}
