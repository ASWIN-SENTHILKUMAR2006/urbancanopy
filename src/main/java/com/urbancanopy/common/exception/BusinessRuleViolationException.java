package com.urbancanopy.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a request is well-formed but would break a domain rule — completing a plan
 * with no species (BR-10), setting scoring weights that do not sum to 1.0 (BR-20),
 * deleting a species that plans depend on (BR-18).
 *
 * <p>Maps to HTTP 422 rather than 400: the syntax was acceptable, the <em>meaning</em> was
 * not. Distinguishing the two tells a client whether to fix the payload or to fix the
 * user's intent.</p>
 */
public class BusinessRuleViolationException extends ApplicationException {

    private final String rule;

    public BusinessRuleViolationException(String message) {
        this(null, message);
    }

    /**
     * @param rule    the business-rule identifier, e.g. {@code "BR-10"}, for traceability
     * @param message user-facing explanation
     */
    public BusinessRuleViolationException(String rule, String message) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_VIOLATION");
        this.rule = rule;
    }

    public String getRule() {
        return rule;
    }
}
