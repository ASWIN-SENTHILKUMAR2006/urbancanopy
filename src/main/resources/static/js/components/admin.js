/**
 * Shared pieces for the administration screens.
 *
 * The access check here is a **convenience, not a control**. Every admin endpoint asserts
 * the caller's role server-side and answers 403 regardless of what the browser believes, so
 * what this gate actually buys is a comprehensible screen instead of a page full of
 * permission errors. Deleting it would change nothing about what an attacker can reach.
 */

import { el, mount } from '../core/dom.js';
import { renderShell, pageHead } from './shell.js';

/**
 * Renders the shell and confirms the caller may see an admin screen.
 *
 * @returns {Promise<boolean>} false when the page should render nothing further
 */
export async function startAdminPage(activePath, title, subtitle, actions = []) {
    const user = await renderShell(activePath);

    if (!user?.admin) {
        // FR-ADM-08: a styled refusal, not a redirect loop. Someone signed in without the
        // role gets an explanation; someone not signed in at all gets a way to do so.
        mount('#content', [
            pageHead(title, subtitle),
            el('div.uc-state', null, [
                el('div.uc-state-icon', { text: '🔒', 'aria-hidden': 'true' }),
                el('h2.uc-state-title', { text: 'Administrator access required' }),
                el('p.uc-state-body', {
                    text: user
                        ? 'Your account does not have administrator rights. Ask an administrator '
                          + 'to grant them if you need access to this screen.'
                        : 'Sign in with an administrator account to view this screen.',
                }),
                user ? null : el('a.uc-btn.uc-btn--primary', { href: '/login.html', text: 'Sign in' }),
            ]),
        ]);
        return false;
    }

    return true;
}

/** A labelled input. */
export function field(name, label, attrs = {}, { required = false, hint = null } = {}) {
    return el('div.uc-field', null, [
        el('label.uc-label', { for: name }, [label, required ? el('span.uc-req', { text: ' *' }) : null]),
        el('input.uc-input', { id: name, name, ...attrs }),
        hint ? el('span.uc-hint', { text: hint }) : null,
    ]);
}

/** A labelled select, populated from `EnumOption`s the server supplied. */
export function selectField(name, label, options, selected, { required = false } = {}) {
    return el('div.uc-field', null, [
        el('label.uc-label', { for: name }, [label, required ? el('span.uc-req', { text: ' *' }) : null]),
        el('select.uc-select', { id: name, name },
            (options || []).map((option) => el('option', {
                value: option.value,
                text: option.label,
                selected: option.value === selected,
            }))),
    ]);
}

export function textareaField(name, label, value, { maxlength = 4000 } = {}) {
    return el('div.uc-field', null, [
        el('label.uc-label', { for: name, text: label }),
        el('textarea.uc-textarea', { id: name, name, maxlength: String(maxlength), text: value || '' }),
    ]);
}

export function checkboxField(name, label, checked) {
    return el('div.uc-field', null, [
        el('label.uc-row', { style: { gap: 'var(--uc-s2)', cursor: 'pointer' } }, [
            el('input', { type: 'checkbox', id: name, name, checked: checked || null }),
            el('span', { text: label }),
        ]),
    ]);
}

/** A section heading inside a long form. */
export function formSection(title) {
    return el('div.uc-section-label', { text: title, style: { marginTop: 'var(--uc-s4)' } });
}

/**
 * A focus-trapped modal dialog.
 *
 * @returns {{ element: Element, close: Function }}
 */
export function openModal({ title, body, footer, onClose }) {
    const lastFocused = document.activeElement;

    const close = () => {
        backdrop.remove();
        document.removeEventListener('keydown', onKey);
        lastFocused?.focus?.();
        onClose?.();
    };

    const onKey = (event) => {
        if (event.key === 'Escape') close();
    };

    const dialog = el('div.uc-modal', { role: 'dialog', 'aria-modal': 'true', 'aria-label': title }, [
        el('div.uc-row', null, [
            el('h2', { text: title, style: { margin: '0' } }),
            el('span.uc-spacer'),
            el('button.uc-iconbtn', { type: 'button', text: '×', 'aria-label': 'Close', onclick: close }),
        ]),
        el('div', { style: { marginTop: 'var(--uc-s4)' } }, [body]),
        footer ? el('div.uc-row', { style: { marginTop: 'var(--uc-s5)' } }, [footer]) : null,
    ]);

    const backdrop = el('div.uc-modal-backdrop', {
        onclick: (event) => {
            if (event.target === backdrop) close();
        },
    }, [dialog]);

    document.body.appendChild(backdrop);
    dialog.querySelector('input, select, textarea, button')?.focus();

    return { element: dialog, close };
}

/**
 * Confirms a consequential action.
 *
 * Says what will happen rather than asking "are you sure?" — a prompt that describes the
 * outcome is one the user can actually answer.
 */
export function confirmAction(message) {
    return window.confirm(message);
}
