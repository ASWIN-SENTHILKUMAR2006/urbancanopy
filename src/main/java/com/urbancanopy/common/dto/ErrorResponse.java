package com.urbancanopy.common.dto;

import java.time.Instant;
import java.util.List;

/**
 * Uniform error body (FR-SYS-03).
 *
 * <p>Stack traces never appear here. When an unexpected failure occurs, the response
 * carries a generated {@code reference} that also appears in the server log, so a user
 * can quote it and an engineer can find the cause — without the browser ever receiving
 * internal detail.</p>
 *
 * @param timestamp   when the failure occurred
 * @param status      HTTP status code
 * @param error       HTTP reason phrase
 * @param errorCode   stable machine-readable code, e.g. {@code BUSINESS_RULE_VIOLATION}
 * @param message     user-facing explanation
 * @param path        request path
 * @param reference   correlation id, present only for unexpected failures
 * @param fieldErrors per-field validation messages, present only for validation failures
 */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String errorCode,
        String message,
        String path,
        String reference,
        List<FieldError> fieldErrors
) {

    /**
     * A single field-level validation failure.
     *
     * @param field         the offending field's name, matching the request DTO
     * @param message       what the user should do about it
     * @param rejectedValue the value that failed, echoed so the client can highlight it
     */
    public record FieldError(String field, String message, Object rejectedValue) {}

    public static Builder builder() {
        return new Builder();
    }

    /** Small fluent builder — an error body has many optional parts. */
    public static final class Builder {
        private int status;
        private String error;
        private String errorCode;
        private String message;
        private String path;
        private String reference;
        private List<FieldError> fieldErrors;

        public Builder status(int status) {
            this.status = status;
            return this;
        }

        public Builder error(String error) {
            this.error = error;
            return this;
        }

        public Builder errorCode(String errorCode) {
            this.errorCode = errorCode;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public Builder reference(String reference) {
            this.reference = reference;
            return this;
        }

        public Builder fieldErrors(List<FieldError> fieldErrors) {
            this.fieldErrors = (fieldErrors == null || fieldErrors.isEmpty()) ? null : fieldErrors;
            return this;
        }

        public ErrorResponse build() {
            return new ErrorResponse(
                    Instant.now(), status, error, errorCode, message, path, reference, fieldErrors);
        }
    }
}
