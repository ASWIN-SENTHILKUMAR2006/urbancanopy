package com.urbancanopy.common.dto;

import java.time.Instant;

/**
 * Uniform success envelope for every JSON endpoint.
 *
 * <p>A single response shape means the browser client can have exactly one unwrapping
 * routine ({@code api.js}) instead of a per-endpoint guess about where the payload lives.
 * The {@code message} field carries the text that becomes a toast, so the server decides
 * what the user is told — the client never invents success wording.</p>
 *
 * @param success   always {@code true}; present so a client can branch on one field
 * @param message   user-facing confirmation, or {@code null} for plain reads
 * @param data      the payload
 * @param timestamp server time the response was produced
 * @param <T>       payload type
 */
public record ApiResponse<T>(boolean success, String message, T data, Instant timestamp) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, null, data, Instant.now());
    }

    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(true, message, data, Instant.now());
    }

    public static ApiResponse<Void> message(String message) {
        return new ApiResponse<>(true, message, null, Instant.now());
    }
}
