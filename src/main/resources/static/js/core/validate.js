/**
 * Client-side validation that mirrors the server's rules.
 *
 * It exists for speed of feedback, not for safety. The server validates every request
 * independently — three layers, deliberately (architecture §7) — and nothing here is
 * trusted by anything. If the two ever disagree, the server is right and this file is the
 * bug.
 */

/** Attaches an error message to a field, or clears it. */
export function setFieldError(input, message) {
    if (!input) return;

    const field = input.closest('.uc-field') || input.parentElement;
    let error = field?.querySelector('.uc-error');

    if (message) {
        input.setAttribute('aria-invalid', 'true');
        if (!error) {
            error = document.createElement('span');
            error.className = 'uc-error';
            field?.appendChild(error);
        }
        error.textContent = message;
        // Tie the message to the input so a screen reader announces them together.
        if (!error.id) error.id = `${input.id || input.name || 'field'}-error`;
        input.setAttribute('aria-describedby', error.id);
    } else {
        input.removeAttribute('aria-invalid');
        input.removeAttribute('aria-describedby');
        error?.remove();
    }
}

/** Clears every error inside a form. */
export function clearErrors(form) {
    form.querySelectorAll('.uc-error').forEach((node) => node.remove());
    form.querySelectorAll('[aria-invalid]').forEach((node) => {
        node.removeAttribute('aria-invalid');
        node.removeAttribute('aria-describedby');
    });
}

/**
 * Renders an `ApiError`'s field errors against the form that produced them, and focuses
 * the first offender so the user is taken to the problem rather than hunting for it.
 *
 * @returns {boolean} whether any error was placed on a field
 */
export function applyServerErrors(form, apiError) {
    clearErrors(form);
    const entries = apiError?.fieldErrors || [];
    let first = null;

    for (const { field, message } of entries) {
        const input = form.elements.namedItem(field);
        if (input) {
            setFieldError(input, message);
            first ??= input;
        }
    }

    first?.focus();
    return Boolean(first);
}

/** Validates one value against a small rule set; returns a message or null. */
export function check(value, rules = {}) {
    const text = typeof value === 'string' ? value.trim() : value;

    if (rules.required && (text === '' || text === null || text === undefined)) {
        return rules.requiredMessage || 'This field is required.';
    }
    if (text === '' || text === null || text === undefined) return null;

    if (rules.maxLength && String(text).length > rules.maxLength) {
        return `Must be at most ${rules.maxLength} characters.`;
    }
    if (rules.min !== undefined && Number(text) < rules.min) {
        return `Must be at least ${rules.min}.`;
    }
    if (rules.max !== undefined && Number(text) > rules.max) {
        return `Must be at most ${rules.max}.`;
    }
    if (rules.positive && !(Number(text) > 0)) {
        return 'Must be greater than zero.';
    }
    if (rules.integer && !Number.isInteger(Number(text))) {
        return 'Must be a whole number.';
    }
    return null;
}

/**
 * Validates a form against a field → rules map.
 *
 * @returns {boolean} true when the form is valid
 */
export function validateForm(form, schema) {
    clearErrors(form);
    let firstInvalid = null;

    for (const [name, rules] of Object.entries(schema)) {
        const input = form.elements.namedItem(name);
        if (!input) continue;

        const message = check(input.value, rules);
        setFieldError(input, message);
        if (message) firstInvalid ??= input;
    }

    firstInvalid?.focus();
    return !firstInvalid;
}
