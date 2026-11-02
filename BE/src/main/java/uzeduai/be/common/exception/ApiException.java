package uzeduai.be.common.exception;

import org.springframework.http.HttpStatus;

import java.util.Arrays;
import java.util.List;

public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String messageKey;
    private final List<String> messageArgs;

    public ApiException(HttpStatus status, String messageKey, Object... args) {
        super(messageKey);
        this.status = status;
        this.messageKey = messageKey;
        this.messageArgs = Arrays.stream(args == null ? new Object[0] : args)
                .map(arg -> arg == null ? "" : String.valueOf(arg))
                .toList();
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public List<String> getMessageArgs() {
        return messageArgs;
    }
}
