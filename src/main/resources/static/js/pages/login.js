/**
 * Sign in (FR-AUTH-05, FR-AUTH-10, FR-AUTH-11).
 *
 * The server's message is shown verbatim — it already distinguishes the cases the user can act
 * on (wrong credentials, disabled account, temporary lock) and deliberately does not
 * distinguish the ones they cannot (unknown email versus wrong password).
 */

import { el, mount, $ } from '../core/dom.js';
import { api } from '../core/api.js';
import { applyStoredTheme } from '../components/shell.js';
import { validateForm, setFieldError } from '../core/validate.js';

applyStoredTheme();

/**
 * Where to go after signing in.
 *
 * Only a same-site path is honoured. A `next` taken straight from the query string is an open
 * redirect: a link to our own login page could then bounce the user to an attacker's copy of
 * it, with our domain in the address bar right up until the moment they hand over a password.
 */
function safeNext() {
    const raw = new URLSearchParams(window.location.search).get('next');
    if (!raw) return '/dashboard.html';
    // Must be a single-slash absolute path — "//evil.com" is protocol-relative and off-site.
    return /^\/(?!\/)/.test(raw) ? raw : '/dashboard.html';
}

function render() {
    const form = el('form.uc-authcard', { id: 'login-form', novalidate: true, onsubmit: submit }, [
        el('h1', { text: 'Sign in' }),
        el('p.uc-pagesub', {
            text: 'Access your plantation plans and their estimates.',
            style: { marginBottom: 'var(--uc-s5)' },
        }),

        el('div.uc-field', null, [
            el('label.uc-label', { for: 'email' }, ['Email address', el('span.uc-req', { text: ' *' })]),
            el('input.uc-input', {
                id: 'email', name: 'email', type: 'email', autocomplete: 'username',
                required: true, autofocus: true,
            }),
        ]),

        el('div.uc-field', null, [
            el('label.uc-label', { for: 'password' }, ['Password', el('span.uc-req', { text: ' *' })]),
            el('input.uc-input', {
                id: 'password', name: 'password', type: 'password',
                autocomplete: 'current-password', required: true,
            }),
        ]),

        el('div', { id: 'login-error' }),

        el('button.uc-btn.uc-btn--primary', {
            type: 'submit', id: 'submit', text: 'Sign in',
            style: { width: '100%', marginTop: 'var(--uc-s2)' },
        }),

        el('div.uc-authfoot', null, [
            el('span', { text: 'No account yet? ' }),
            el('a', { href: '/register.html', text: 'Create one' }),
        ]),
    ]);

    mount('#content', form);
}

async function submit(event) {
    event.preventDefault();

    const form = $('#login-form');
    const button = $('#submit');
    const errorSlot = $('#login-error');
    errorSlot.replaceChildren();

    const valid = validateForm(form, {
        email: { required: true, requiredMessage: 'Enter your email address.' },
        password: { required: true, requiredMessage: 'Enter your password.' },
    });
    if (!valid) return;

    button.disabled = true;
    button.textContent = 'Signing in…';

    try {
        await api.login(form.elements.email.value.trim(), form.elements.password.value);
        window.location.href = safeNext();
    } catch (error) {
        mount(errorSlot, el('div.uc-badge.uc-badge--danger', {
            role: 'alert',
            style: { display: 'block', whiteSpace: 'normal', padding: 'var(--uc-s2) var(--uc-s3)' },
            text: error.message,
        }));

        // Clear only the password. Making them retype an email they got right is a small
        // cruelty that adds nothing.
        form.elements.password.value = '';
        setFieldError(form.elements.password, null);
        form.elements.password.focus();
    } finally {
        button.disabled = false;
        button.textContent = 'Sign in';
    }
}

render();
