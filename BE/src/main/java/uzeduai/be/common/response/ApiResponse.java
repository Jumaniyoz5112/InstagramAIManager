package uzeduai.be.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        T data,
        String messageKey,
        List<String> messageArgs,
        Instant timestamp
) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, null, Instant.now());
    }

    public static <T> ApiResponse<T> ok(T data, String messageKey) {
        return new ApiResponse<>(true, data, messageKey, null, Instant.now());
    }

    public static ApiResponse<Void> fail(String messageKey) {
        return new ApiResponse<>(false, null, messageKey, null, Instant.now());
    }

    public static ApiResponse<Void> fail(String messageKey, List<String> messageArgs) {
        return new ApiResponse<>(false, null, messageKey, messageArgs, Instant.now());
    }
}
