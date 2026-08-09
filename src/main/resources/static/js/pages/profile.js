/**
 * Profile (FR-AUTH-09).
 *
 * Name, organisation and designation only. Email is the account's identity and changing it
 * needs the verification machinery that arrives with authentication in Phase 12; the
 * password field arrives at the same time, for the same reason.
 */

import { el, mount, $ } from '../core/dom.js';
import { api } from '../core/api.js';
import * as fmt from '../core/format.js';
import { toast } from '../core/toast.js';
import { validateForm, applyServerErrors } from '../core/validate.js';
import { renderShell, pageHead } from '../components/shell.js';
import { loadInto } from '../components/states.js';
import { badge } from '../components/widgets.js';

const region = el('div');

async function init() {
    await renderShell('/profile.html');
    mount('#content', [pageHead('Profile', 'Your account details.'), region]);

    await loadInto(region, () => api.get('/profile', { quiet: true }), render, { isEmpty: () => false });
}

function render(user) {
    const field = (name, label, value, required = false, maxlength = 150) => el('div.uc-field', null, [
        el('label.uc-label', { for: name }, [label, required ? el('span.uc-req', { text: ' *' }) : null]),
        el('input.uc-input', { id: name, name, value: value || '', maxlength: String(maxlength) }),
    ]);

    const form = el('form.uc-card', { id: 'profile-form', onsubmit: submit }, [
        field('fullName', 'Full name', user.fullName, true, 120),
        field('organisation', 'Organisation', user.organisation, false, 150),
        field('designation', 'Designation', user.designation, false, 100),
        el('div.uc-row', null, [
            el('button.uc-btn.uc-btn--primary', { type: 'submit', text: 'Save changes' }),
        ]),
    ]);

    return [
        el('div.uc-editor', null, [
            form,
            el('div.uc-card', null, [
                el('h2.uc-card-title', { text: 'Account' }),
                el('div.uc-stack--tight', { style: { marginTop: 'var(--uc-s3)' } }, [
                    row('Email', user.email),
                    row('Role', user.role.label),
                    row('Status', user.enabled ? 'Active' : 'Disabled'),
                    row('Member since', fmt.date(user.createdAt)),
                    row('Last sign-in', user.lastLoginAt ? fmt.dateTime(user.lastLoginAt) : 'Not recorded yet'),
                ]),
                el('p.uc-small.uc-muted', { style: { marginTop: 'var(--uc-s4)' } }, [
                    'Changing your email address and password becomes available when sign-in is '
                    + 'introduced. Until then every request is attributed to the demo planner.',
                ]),
                user.admin ? el('div', { style: { marginTop: 'var(--uc-s3)' } }, [
                    badge('Administrator', 'uc-badge--primary'),
                ]) : null,
            ]),
        ]),
    ];
}

function row(label, value) {
    return el('div.uc-row', null, [
        el('span.uc-muted.uc-small', { text: label }),
        el('span.uc-spacer'),
        el('span', { text: value }),
    ]);
}

async function submit(event) {
    event.preventDefault();
    const form = $('#profile-form');

    const schema = {
        fullName: { required: true, maxLength: 120, requiredMessage: 'Your name is required.' },
        organisation: { maxLength: 150 },
        designation: { maxLength: 100 },
    };
    if (!validateForm(form, schema)) return;

    const body = {
        fullName: form.elements.fullName.value.trim(),
        organisation: form.elements.organisation.value.trim() || null,
        designation: form.elements.designation.value.trim() || null,
    };

    try {
        await api.put('/profile', body);
        // The topbar carries the name, so it must not keep showing the previous one.
        window.location.reload();
    } catch (error) {
        if (error.isValidation) {
            applyServerErrors(form, error);
            toast.warning('Please correct the highlighted fields.');
        }
    }
}

init();
