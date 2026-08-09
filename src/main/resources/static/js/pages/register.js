/**
 * Create an account (FR-AUTH-01, FR-AUTH-03).
 *
 * The password policy is stated before the field, not discovered by failing it. The server
 * enforces the same rules and is the authority; this is only the courtesy of saying so first.
 */

import { el, mount, $ } from '../core/dom.js';
import { api } from '../core/api.js';
import { toast } from '../core/toast.js';
import { applyStoredTheme } from '../components/shell.js';
import { validateForm, applyServerErrors } from '../core/validate.js';

applyStoredTheme();

/** Mirrors app.password-policy.* on the server. */
const MIN_LENGTH = 8;

function render() {
    const field = (name, label, attrs, { required = false, hint = null } = {}) =>
        el('div.uc-field', null, [
            el('label.uc-label', { for: name }, [label, required ? el('span.uc-req', { text: ' *' }) : null]),
            el('input.uc-input', { id: name, name, ...attrs }),
            hint ? el('span.uc-hint', { text: hint }) : null,
        ]);

    const form = el('form.uc-authcard.uc-authcard--wide', {
        id: 'register-form', novalidate: true, onsubmit: submit,
    }, [
        el('h1', { text: 'Create your account' }),
        el('p.uc-pagesub', {
            text: 'Plan an urban plantation and estimate what it will do.',
            style: { marginBottom: 'var(--uc-s5)' },
        }),

        field('fullName', 'Full name', {
            type: 'text', maxlength: '120', autocomplete: 'name', autofocus: true,
        }, { required: true }),

        field('email', 'Email address', {
            type: 'email', maxlength: '160', autocomplete: 'username',
        }, { required: true, hint: 'You will sign in with this address.' }),

        field('password', 'Password', {
            type: 'password', autocomplete: 'new-password',
        }, {
            required: true,
            hint: `At least ${MIN_LENGTH} characters, including a letter and a digit.`,
        }),

        el('div.uc-inline', null, [
            el('div', { style: { flex: '1' } }, [
                field('organisation', 'Organisation', { type: 'text', maxlength: '150' }),
            ]),
            el('div', { style: { flex: '1' } }, [
                field('designation', 'Designation', { type: 'text', maxlength: '100' }),
            ]),
        ]),

        el('div', { id: 'register-error' }),

        el('button.uc-btn.uc-btn--primary', {
            type: 'submit', id: 'submit', text: 'Create account',
            style: { width: '100%', marginTop: 'var(--uc-s2)' },
        }),

        el('div.uc-authfoot', null, [
            el('span', { text: 'Already have an account? ' }),
            el('a', { href: '/login.html', text: 'Sign in' }),
        ]),
    ]);

    mount('#content', form);
}

async function submit(event) {
    event.preventDefault();

    const form = $('#register-form');
    const button = $('#submit');
    const errorSlot = $('#register-error');
    errorSlot.replaceChildren();

    const valid = validateForm(form, {
        fullName: { required: true, maxLength: 120, requiredMessage: 'Your name is required.' },
        email: { required: true, maxLength: 160, requiredMessage: 'An email address is required.' },
        password: { required: true, requiredMessage: 'Choose a password.' },
        organisation: { maxLength: 150 },
        designation: { maxLength: 100 },
    });
    if (!valid) return;

    button.disabled = true;
    button.textContent = 'Creating…';

    try {
        await api.post('/auth/register', {
            fullName: form.elements.fullName.value.trim(),
            email: form.elements.email.value.trim(),
            password: form.elements.password.value,
            organisation: form.elements.organisation.value.trim() || null,
            designation: form.elements.designation.value.trim() || null,
        }, { quiet: true });

        // Registering does not sign you in; the login page proves the credentials work.
        toast.success('Your account has been created. Please sign in.');
        window.location.href = '/login.html';
    } catch (error) {
        // 400 carries per-field messages; 409 (duplicate email) and 422 (password policy)
        // carry one sentence about the whole form.
        const attached = error.isValidation && applyServerErrors(form, error);
        if (!attached) {
            mount(errorSlot, el('div.uc-badge.uc-badge--danger', {
                role: 'alert',
                style: { display: 'block', whiteSpace: 'normal', padding: 'var(--uc-s2) var(--uc-s3)' },
                text: error.message,
            }));
        }
    } finally {
        button.disabled = false;
        button.textContent = 'Create account';
    }
}

render();
