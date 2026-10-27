package com.genisource.erp.platform.common;

import org.springframework.http.HttpStatus;

import java.util.Map;

/**
 * Business failure that the API layer turns into an RFC 9457 problem detail.
 *
 * <p>Throw these instead of {@code RuntimeException}: the status and error code are
 * part of the contract, and the handler fills in a localized message.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final HttpStatus status;
    private final transient Map<String, Object> properties;

    public ApiException(ErrorCode code, HttpStatus status, String message) {
        this(code, status, message, Map.of());
    }

    public ApiException(ErrorCode code, HttpStatus status, String message, Map<String, Object> properties) {
        super(message);
        this.code = code;
        this.status = status;
        this.properties = properties == null ? Map.of() : Map.copyOf(properties);
    }

    public static ApiException notFound(String message) {
        return new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, message);
    }

    public static ApiException badRequest(String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, message);
    }

    public static ApiException unauthorized(ErrorCode code, String message) {
        return new ApiException(code, HttpStatus.UNAUTHORIZED, message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, message);
    }

    /** A business key such as an item code is already taken inside this tenant. */
    public static ApiException duplicateCode(String message) {
        return new ApiException(ErrorCode.DUPLICATE_CODE, HttpStatus.CONFLICT, message);
    }

    /** The row is referenced elsewhere and cannot be deactivated or removed. */
    public static ApiException inUse(String message) {
        return new ApiException(ErrorCode.IN_USE, HttpStatus.CONFLICT, message);
    }

    /** The requested transition is not allowed from the current state. */
    public static ApiException invalidState(String message) {
        return new ApiException(ErrorCode.INVALID_STATE, HttpStatus.CONFLICT, message);
    }

    /** A bill of materials would become its own ancestor. */
    public static ApiException bomCycle(String message) {
        return new ApiException(ErrorCode.BOM_CYCLE, HttpStatus.CONFLICT, message);
    }

    /** Something went wrong on our side; the cause is for the log, not for the user. */
    public static ApiException internal(String message, Throwable cause) {
        ApiException exception = new ApiException(ErrorCode.INTERNAL_ERROR,
                HttpStatus.INTERNAL_SERVER_ERROR, message);
        if (cause != null) {
            exception.initCause(cause);
        }
        return exception;
    }

    /** The shelf does not hold what the document asks for. */
    public static ApiException insufficientStock(String message) {
        return new ApiException(ErrorCode.INSUFFICIENT_STOCK, HttpStatus.CONFLICT, message);
    }

    public ErrorCode code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }

    public Map<String, Object> properties() {
        return properties;
    }
}
