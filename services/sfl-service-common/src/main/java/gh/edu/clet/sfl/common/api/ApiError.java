package gh.edu.clet.sfl.common.api;

import java.time.Instant;

public record ApiError(
        String code,
        String message,
        String correlationId,
        Instant timestamp) {

    public static ApiError of(String code, String message, String correlationId) {
        return new ApiError(code, message, correlationId, Instant.now());
    }
}

