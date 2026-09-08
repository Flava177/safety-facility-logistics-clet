package gh.edu.clet.sfl.common.api;

import java.util.Objects;

/**
 * Envelope wrapping every JSON response body in the platform. Three shapes are valid: a success
 * with a payload ({@code data} set, {@code error} null), a void success ({@code data} and
 * {@code error} both null, e.g. a 204-style response via {@link #ok}), and a failure ({@code error}
 * set, with {@code data} optionally carrying structured error details such as per-field validation
 * failures). The raw canonical constructor stays available for that error-with-details shape;
 * {@link #failed} guards against the one genuinely invalid case, an error response with no error.
 */
public record ApiResponse<T>(
        T data,
        ApiError error) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(data, null);
    }

    public static <T> ApiResponse<T> failed(ApiError error) {
        return new ApiResponse<>(null, Objects.requireNonNull(error, "error must not be null"));
    }
}

