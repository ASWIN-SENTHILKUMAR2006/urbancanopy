package com.urbancanopy.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an operation requires an identified caller and none is available.
 * Maps to HTTP 401.
 *
 * <p>Before Phase 12 this can only occur if the development identity cannot be resolved
 * — for example when the seeded demo account has been removed. From Phase 12 it is the
 * ordinary "not logged in" case, and the client's {@code api.js} translates it into a
 * redirect to the login page.</p>
 */
public class UnauthenticatedException extends ApplicationException {

    public UnauthenticatedException(String message) {
        super(message, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
    }
}
