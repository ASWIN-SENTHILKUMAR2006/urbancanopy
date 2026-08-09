package com.urbancanopy.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Root of the application's deliberately small exception taxonomy.
 *
 * <p>Each subclass carries the HTTP status it deserves, so
 * {@link GlobalExceptionHandler} maps exception to response without a chain of
 * {@code instanceof} checks — adding a new exception type requires no change to the
 * handler (open/closed principle).</p>
 *
 * <p>The message of an {@code ApplicationException} is written for a user to read: it is
 * returned in the response body. Anything that should not be shown to a user must not be
 * put in the message.</p>
 */
public abstract class ApplicationException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    protected ApplicationException(String message, HttpStatus status, String errorCode) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    protected ApplicationException(String message, HttpStatus status, String errorCode, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    /** Stable machine-readable code, so a client can branch without parsing prose. */
    public String getErrorCode() {
        return errorCode;
    }
}
