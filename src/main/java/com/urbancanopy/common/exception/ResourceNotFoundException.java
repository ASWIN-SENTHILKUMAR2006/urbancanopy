package com.urbancanopy.common.exception;

import org.springframework.http.HttpStatus;

/** Thrown when a requested resource does not exist. Maps to HTTP 404. */
public class ResourceNotFoundException extends ApplicationException {

    public ResourceNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND");
    }

    /**
     * Convenience factory producing a consistent message.
     *
     * @param resource human-readable resource name, e.g. {@code "Plantation plan"}
     * @param id       the identifier that was not found
     */
    public static ResourceNotFoundException of(String resource, Object id) {
        return new ResourceNotFoundException(resource + " with id " + id + " was not found.");
    }
}
