package com.urbancanopy.common.exception;

import com.urbancanopy.common.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.UUID;

/**
 * Single translation point from exception to HTTP response (FR-SYS-03).
 *
 * <p>Two principles govern this class:</p>
 * <ol>
 *   <li><strong>Nothing internal escapes.</strong> Expected failures return the message
 *       their exception carries, which was written for a user. Unexpected failures return
 *       a fixed apology plus a correlation reference; the detail goes to the log only.</li>
 *   <li><strong>The taxonomy does the work.</strong> Every {@link ApplicationException}
 *       carries its own status and code, so one handler covers all of them and adding a
 *       new exception type needs no change here.</li>
 * </ol>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String UNEXPECTED_MESSAGE =
            "Something went wrong while processing your request. "
                    + "Please try again, and quote the reference below if the problem persists.";

    // ---------------------------------------------------------------- application errors

    /** Handles every exception in the application's own taxonomy. */
    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<ErrorResponse> handleApplication(ApplicationException ex, HttpServletRequest request) {
        log.debug("Handled application exception [{}] on {}: {}",
                ex.getErrorCode(), request.getRequestURI(), ex.getMessage());

        List<ErrorResponse.FieldError> fieldErrors = null;
        if (ex instanceof DuplicateResourceException duplicate && duplicate.getField() != null) {
            fieldErrors = List.of(new ErrorResponse.FieldError(duplicate.getField(), ex.getMessage(), null));
        }

        return respond(ex.getStatus(), ex.getErrorCode(), ex.getMessage(), request, null, fieldErrors);
    }

    // ------------------------------------------------------------------- validation (400)

    /** Bean Validation failures on an {@code @Valid @RequestBody} DTO. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleBodyValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ErrorResponse.FieldError(
                        error.getField(), error.getDefaultMessage(), error.getRejectedValue()))
                .toList();

        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "Please correct the highlighted fields and try again.", request, null, fieldErrors);
    }

    /** Bean Validation failures on {@code @Validated} method parameters (query params, path vars). */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {

        List<ErrorResponse.FieldError> fieldErrors = ex.getConstraintViolations().stream()
                .map(violation -> new ErrorResponse.FieldError(
                        lastPathNode(violation.getPropertyPath().toString()),
                        violation.getMessage(),
                        violation.getInvalidValue()))
                .toList();

        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "One or more request parameters are invalid.", request, null, fieldErrors);
    }

    /** Malformed JSON, or a value that cannot be bound to its target type. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.debug("Unreadable request body on {}: {}", request.getRequestURI(), ex.getMessage());
        return respond(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "The request body could not be read. Please check the submitted values.",
                request, null, null);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER",
                "Required parameter '" + ex.getParameterName() + "' is missing.",
                request, null, null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER",
                "Parameter '" + ex.getName() + "' has an invalid value.", request, null,
                List.of(new ErrorResponse.FieldError(ex.getName(), "Invalid value.", ex.getValue())));
    }

    // ---------------------------------------------------------------------- request shape

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return respond(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
                "This endpoint does not support " + ex.getMethod() + " requests.", request, null, null);
    }

    /** A request for a static asset or API path that does not exist. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(
            NoResourceFoundException ex, HttpServletRequest request) {
        return respond(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND",
                "The requested resource was not found.", request, null, null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleUploadTooLarge(
            MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return respond(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE",
                "The uploaded file is larger than the permitted limit.", request, null, null);
    }

    // ------------------------------------------------------------------------ persistence

    /**
     * A database constraint rejected the write.
     *
     * <p>Reaching here means the service-layer check and the database constraint disagreed
     * — usually a genuine race between two concurrent requests. It is logged at WARN
     * because it is expected to be rare and is worth noticing when it is not.</p>
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(
            DataIntegrityViolationException ex, HttpServletRequest request) {
        String reference = newReference();
        log.warn("Data integrity violation [ref={}] on {}: {}",
                reference, request.getRequestURI(), rootMessage(ex));
        return respond(HttpStatus.CONFLICT, "DATA_INTEGRITY_VIOLATION",
                "This change conflicts with existing data. Please review your input and try again.",
                request, reference, null);
    }

    // -------------------------------------------------------------------------- catch-all

    /** Anything unanticipated. The user gets a reference; the log gets the truth. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        String reference = newReference();
        log.error("Unexpected failure [ref={}] on {} {}",
                reference, request.getMethod(), request.getRequestURI(), ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                UNEXPECTED_MESSAGE, request, reference, null);
    }

    // --------------------------------------------------------------------------- helpers

    private ResponseEntity<ErrorResponse> respond(
            HttpStatus status,
            String errorCode,
            String message,
            HttpServletRequest request,
            String reference,
            List<ErrorResponse.FieldError> fieldErrors) {

        ErrorResponse body = ErrorResponse.builder()
                .status(status.value())
                .error(status.getReasonPhrase())
                .errorCode(errorCode)
                .message(message)
                .path(request.getRequestURI())
                .reference(reference)
                .fieldErrors(fieldErrors)
                .build();

        return ResponseEntity.status(status).body(body);
    }

    private static String newReference() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    /** {@code createPlan.arg0.title} → {@code title}. */
    private static String lastPathNode(String propertyPath) {
        int lastDot = propertyPath.lastIndexOf('.');
        return lastDot >= 0 ? propertyPath.substring(lastDot + 1) : propertyPath;
    }

    private static String rootMessage(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getMessage();
    }
}
