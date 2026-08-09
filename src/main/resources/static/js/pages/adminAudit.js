/**
 * Audit log (FR-ADM-07).
 *
 * Read-only, with no control that writes anything. That is the point of an audit trail:
 * entries appear as a side effect of the operations they describe, and a screen that could
 * add, edit or delete one would make the whole table useless as evidence.
 *
 * Newest first — a log is read from the end.
 */

import { el, mount } from '../core/dom.js';
import { api } from '../core/api.js';
import * as fmt from '../core/format.js';
import { pageHead } from '../components/shell.js';
import { loadInto } from '../components/states.js';
import { badge, pager } from '../components/widgets.js';
import { startAdminPage } from '../components/admin.js';

const state = { eventType: '', page: 0, size: 25 };
let eventTypes = [];
const region = el('div');

/** Colour by what an event means, so a scan finds the security events first. */
const TONE = {
    LOGIN_FAILED: 'uc-badge--danger',
    ACCOUNT_DISABLED: 'uc-badge--danger',
    SPECIES_DELETED: 'uc-badge--danger',
    ROLE_CHANGED: 'uc-badge--warning',
    COEFFICIENT_CHANGED: 'uc-badge--warning',
    SPECIES_DEACTIVATED: 'uc-badge--warning',
    ADMIN_VIEWED_PLAN: 'uc-badge--info',
    LOGIN_SUCCEEDED: 'uc-badge--success',
    ACCOUNT_ENABLED: 'uc-badge--success',
    SPECIES_ACTIVATED: 'uc-badge--success',
};

async function init() {
    const permitted = await startAdminPage('/admin/audit.html', 'Audit log', 'Security- and data-sensitive events.');
    if (!permitted) return;

    eventTypes = await api.get('/admin/audit-events/types', { quiet: true }).catch(() => []);

    mount('#content', [
        pageHead('Audit log', 'Who changed what, and when. Append-only — nothing here can be edited or removed.'),
        buildFilters(),
        region,
    ]);

    await load();
}

function buildFilters() {
    return el('div.uc-filters', null, [
        el('div.uc-field', null, [
            el('label.uc-label', { for: 'f-type', text: 'Event type' }),
            el('select.uc-select', {
                id: 'f-type',
                onchange: (event) => {
                    state.eventType = event.target.value;
                    state.page = 0;
                    load();
                },
            }, [
                el('option', { value: '', text: 'All events' }),
                ...eventTypes.map((type) => el('option', { value: type.value, text: type.label })),
            ]),
        ]),
        el('div.uc-field', null, [
            el('label.uc-label', { text: ' ' }),
            el('button.uc-btn', { type: 'button', text: '↻ Refresh', onclick: () => load() }),
        ]),
    ]);
}

async function load() {
    await loadInto(
        region,
        () => api.get('/admin/audit-events', {
            quiet: true,
            query: {
                eventType: state.eventType,
                page: state.page,
                size: state.size,
                sort: 'occurredAt,desc',
            },
        }),
        render,
        {
            rows: 6,
            empty: {
                icon: '📄',
                title: 'No events recorded',
                body: state.eventType
                    ? 'No events of that type yet. Try "All events".'
                    : 'Events appear here as species, coefficients and accounts are changed.',
            },
        },
    );
}

function render(page) {
    return [
        el('p.uc-small.uc-muted', { text: `${page.totalElements} recorded events` }),
        el('div.uc-tablewrap', null, [
            el('table.uc-table', null, [
                el('thead', null, [
                    el('tr', null, [
                        el('th', { text: 'When' }),
                        el('th', { text: 'Event' }),
                        el('th', { text: 'Actor' }),
                        el('th', { text: 'Detail' }),
                        el('th', { text: 'Subject' }),
                        el('th', { text: 'IP' }),
                    ]),
                ]),
                el('tbody', null, page.content.map(eventRow)),
            ]),
        ]),
        pager(page, (target) => {
            state.page = target;
            load();
        }),
    ];
}

function eventRow(event) {
    return el('tr', null, [
        el('td.uc-small', null, [
            el('div', { text: fmt.dateTime(event.occurredAt) }),
            el('div.uc-muted', { text: fmt.relative(event.occurredAt) }),
        ]),
        el('td', null, [badge(event.eventType.label, TONE[event.eventType.value] || 'uc-badge--muted')]),
        el('td.uc-small', null, [
            // The actor reference is nulled if an account is ever removed, so the history
            // outlives its subject. Say so rather than rendering an empty cell.
            event.actorName
                ? [el('div', { text: event.actorName }), el('div.uc-muted', { text: event.actorEmail })]
                : el('span.uc-muted', { text: '(removed account)' }),
        ]),
        el('td.uc-small', { text: event.detail || '—' }),
        el('td.uc-small.uc-muted', {
            text: event.entityType ? `${event.entityType} #${event.entityId ?? '—'}` : '—',
        }),
        el('td.uc-small.uc-mono.uc-muted', { text: event.ipAddress || '—' }),
    ]);
}

init();
