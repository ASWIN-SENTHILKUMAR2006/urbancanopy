package com.urbancanopy.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a uniqueness rule would be violated — a duplicate email (FR-AUTH-04), a
 * duplicate species name (BR-05), a duplicate plan title. Maps to HTTP 409.
 *
 * <p>Carries the offending field name so the client can attach the message to the right
 * input rather than showing a page-level banner.</p>
 */
public class DuplicateResourceException extends ApplicationException {

    private final String field;

    public DuplicateResourceException(String field, String message) {
        super(message, HttpStatus.CONFLICT, "DUPLICATE_RESOURCE");
        this.field = field;
    }

    public String getField() {
        return field;
    }

    public static DuplicateResourceException of(String field, String label, Object value) {
        return new DuplicateResourceException(field, label + " '" + value + "' is already in use.");
    }
}
