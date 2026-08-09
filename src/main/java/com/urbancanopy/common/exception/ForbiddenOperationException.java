package com.urbancanopy.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an identified caller may not perform an operation — accessing another
 * user's plan (FR-PLAN-10, BR-06) or reaching an administrator endpoint (FR-ADM-08).
 * Maps to HTTP 403.
 *
 * <p>Deliberately distinct from {@link UnauthenticatedException}: 403 means "we know who
 * you are and the answer is no", 401 means "we do not know who you are". Collapsing the
 * two makes both bugs and genuine permission problems harder to diagnose.</p>
 */
public class ForbiddenOperationException extends ApplicationException {

    public ForbiddenOperationException(String message) {
        super(message, HttpStatus.FORBIDDEN, "FORBIDDEN");
    }
}
