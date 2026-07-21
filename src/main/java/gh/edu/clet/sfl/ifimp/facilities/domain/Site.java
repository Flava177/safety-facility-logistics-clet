package gh.edu.clet.sfl.ifimp.facilities.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Site(
        UUID id,
        String siteCode,
        String name,
        String description,
        boolean active,
        Instant createdAt) {

    public Site {
        Objects.requireNonNull(id, "id is required");
        requireText(siteCode, "siteCode");
        requireText(name, "name");
        Objects.requireNonNull(createdAt, "createdAt is required");
    }

    public static Site create(UUID id, String siteCode, String name, String description, Instant createdAt) {
        return new Site(id, normalizeCode(siteCode), name.strip(), blankToNull(description), true, createdAt);
    }

    static String normalizeCode(String value) {
        requireText(value, "code");
        return value.strip().toUpperCase();
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
