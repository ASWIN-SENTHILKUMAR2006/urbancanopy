/**
 * User administration (FR-ADM-01 … FR-ADM-03).
 *
 * The two refusals that matter — an administrator cannot disable or demote their own
 * account, and the last enabled administrator cannot be removed by either route — are
 * enforced in the service and will produce a 403 or a 422 regardless of what this screen
 * allows. What the screen adds is that the forbidden controls are *disabled with a reason*
 * on the caller's own row, so the rule is visible before it is hit rather than discovered
 * as an error.
 *
 * There is no delete. Accounts own plans and appear as the actor on audit entries;
 * disabling is the withdrawal this system offers.
 */

import { el, mount, debounce } from '../core/dom.js';
import { api } from '../core/api.js';
import * as fmt from '../core/format.js';
import { pageHead } from '../components/shell.js';
import { loadInto } from '../components/states.js';
import { badge, pager } from '../components/widgets.js';
import { startAdminPage, confirmAction } from '../components/admin.js';

const state = { search: '', role: '', enabled: '', page: 0, size: 15 };
let roles = [];
const region = el('div');

async function init() {
    const permitted = await startAdminPage('/admin/users.html', 'Users', 'Accounts, roles and access.');
    if (!permitted) return;

    roles = await api.get('/admin/users/roles', { quiet: true }).catch(() => []);

    mount('#content', [
        pageHead('Users', 'Search accounts, change roles, and enable or disable access.'),
        buildFilters(),
        region,
    ]);

    await load();
}

function buildFilters() {
    const onSearch = debounce((value) => {
        state.search = value;
        state.page = 0;
        load();
    }, 300);

    return el('div.uc-filters', null, [
        el('div.uc-field.uc-filter-grow', null, [
            el('label.uc-label', { for: 'f-search', text: 'Search' }),
            el('input.uc-input', {
                id: 'f-search',
                type: 'search',
                placeholder: 'Name, email or organisation…',
                oninput: (event) => onSearch(event.target.value),
            }),
        ]),
        el('div.uc-field', null, [
            el('label.uc-label', { for: 'f-role', text: 'Role' }),
            el('select.uc-select', {
                id: 'f-role',
                onchange: (event) => {
                    state.role = event.target.value;
                    state.page = 0;
                    load();
                },
            }, [
                el('option', { value: '', text: 'All' }),
                ...roles.map((role) => el('option', { value: role.value, text: role.label })),
            ]),
        ]),
        el('div.uc-field', null, [
            el('label.uc-label', { for: 'f-enabled', text: 'Status' }),
            el('select.uc-select', {
                id: 'f-enabled',
                onchange: (event) => {
                    state.enabled = event.target.value;
                    state.page = 0;
                    load();
                },
            }, [
                el('option', { value: '', text: 'All' }),
                el('option', { value: 'true', text: 'Enabled' }),
                el('option', { value: 'false', text: 'Disabled' }),
            ]),
        ]),
    ]);
}

async function load() {
    await loadInto(
        region,
        () => api.get('/admin/users', {
            quiet: true,
            query: {
                search: state.search,
                role: state.role,
                enabled: state.enabled,
                page: state.page,
                size: state.size,
                sort: 'fullName,asc',
            },
        }),
        render,
        {
            rows: 5,
            empty: { icon: '👥', title: 'No accounts match', body: 'Try clearing a filter.' },
        },
    );
}

function render(page) {
    return [
        el('div.uc-tablewrap', null, [
            el('table.uc-table', null, [
                el('thead', null, [
                    el('tr', null, [
                        el('th', { text: 'Account' }),
                        el('th', { text: 'Organisation' }),
                        el('th', { text: 'Role' }),
                        el('th', { text: 'Status' }),
                        el('th.uc-right', { text: 'Plans' }),
                        el('th.uc-right', { text: 'Last sign-in' }),
                        el('th', { text: '', 'aria-label': 'Actions' }),
                    ]),
                ]),
                el('tbody', null, page.content.map(userRow)),
            ]),
        ]),
        pager(page, (target) => {
            state.page = target;
            load();
        }),
    ];
}

function userRow(user) {
    const isAdmin = user.role.value === 'ROLE_ADMIN';

    return el('tr', null, [
        el('td', null, [
            el('div', { style: { fontWeight: '600' } }, [
                el('span', { text: user.fullName }),
                user.self ? badge('You', 'uc-badge--primary') : null,
            ]),
            el('div.uc-small.uc-muted', { text: user.email }),
            user.designation ? el('div.uc-small.uc-muted', { text: user.designation }) : null,
        ]),
        el('td.uc-small', { text: user.organisation || '—' }),
        el('td', null, [badge(user.role.label, isAdmin ? 'uc-badge--primary' : 'uc-badge--muted')]),
        el('td', null, [
            user.enabled ? badge('Enabled', 'uc-badge--success') : badge('Disabled', 'uc-badge--danger'),
        ]),
        el('td.uc-right', { text: fmt.int(user.planCount) }),
        el('td.uc-right.uc-small.uc-muted', {
            text: user.lastLoginAt ? fmt.relative(user.lastLoginAt) : 'Never',
        }),
        el('td', null, [
            el('div.uc-row', { style: { gap: 'var(--uc-s1)', flexWrap: 'nowrap' } }, [
                el('button.uc-btn.uc-btn--sm', {
                    type: 'button',
                    text: user.enabled ? 'Disable' : 'Enable',
                    // FR-ADM-03: the self-lockout guard, made visible rather than discovered.
                    disabled: user.self && user.enabled,
                    title: user.self && user.enabled
                        ? 'You cannot disable your own account'
                        : (user.enabled ? 'Prevent this account from signing in' : 'Restore access'),
                    onclick: () => setEnabled(user),
                }),
                el('button.uc-btn.uc-btn--sm.uc-btn--ghost', {
                    type: 'button',
                    text: isAdmin ? 'Make planner' : 'Make admin',
                    disabled: user.self,
                    title: user.self
                        ? 'You cannot change your own role'
                        : (isAdmin ? 'Withdraw administrator rights' : 'Grant administrator rights'),
                    onclick: () => changeRole(user, isAdmin ? 'ROLE_USER' : 'ROLE_ADMIN'),
                }),
            ]),
        ]),
    ]);
}

async function setEnabled(user) {
    if (user.enabled) {
        const confirmed = confirmAction(
            `Disable ${user.fullName}?\n\nThey will be refused access on their next request. `
            + `Their ${user.planCount} plan(s) and their audit history are untouched, and you can re-enable them at any time.`);
        if (!confirmed) return;
    }

    const updated = await api.patch(`/admin/users/${user.id}/status`, { enabled: !user.enabled }).catch(() => null);
    if (updated) await load();
}

async function changeRole(user, role) {
    const granting = role === 'ROLE_ADMIN';
    const confirmed = confirmAction(granting
        ? `Make ${user.fullName} an administrator?\n\nThey will be able to edit the species catalogue and the `
          + 'coefficient registry, which changes the figures every estimate reports.'
        : `Withdraw administrator rights from ${user.fullName}?\n\nThey keep their own plans and can still use the planner.`);
    if (!confirmed) return;

    const updated = await api.patch(`/admin/users/${user.id}/role`, { role }).catch(() => null);
    if (updated) await load();
}

init();
