package gh.edu.clet.sfl.common.api;

public record ApiResponse<T>(
        T data,
        ApiError error) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(data, null);
    }

    public static <T> ApiResponse<T> failed(ApiError error) {
        return new ApiResponse<>(null, error);
    }
}

