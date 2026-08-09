/**
 * "My plans" — the planner's list (FR-PLAN-07, UC-03).
 */

import { el, mount, debounce } from '../core/dom.js';
import { api } from '../core/api.js';
import * as fmt from '../core/format.js';
import { renderShell, pageHead } from '../components/shell.js';
import { loadInto } from '../components/states.js';
import { statusBadge, pager } from '../components/widgets.js';

const state = { search: '', status: '', purpose: '', page: 0, size: 10 };
let options = {};
const listRegion = el('div');

async function init() {
    await renderShell('/plans.html');
    options = await api.get('/plantation-plans/options', { quiet: true }).catch(() => ({}));

    mount('#content', [
        pageHead('My plans', 'Each plan is a species composition on a site. Its estimate is one click away.', [
            el('a.uc-btn.uc-btn--primary', { href: '/plan-editor.html', text: '+ New plan' }),
        ]),
        buildFilters(),
        listRegion,
    ]);

    await load();
}

function buildFilters() {
    const onSearch = debounce((value) => {
        state.search = value;
        state.page = 0;
        load();
    }, 300);

    const select = (name, label, list) => el('div.uc-field', null, [
        el('label.uc-label', { for: `f-${name}`, text: label }),
        el('select.uc-select', {
            id: `f-${name}`,
            onchange: (event) => {
                state[name] = event.target.value;
                state.page = 0;
                load();
            },
        }, [
            el('option', { value: '', text: 'All' }),
            ...(list || []).map((o) => el('option', { value: o.value, text: o.label })),
        ]),
    ]);

    return el('div.uc-filters', null, [
        el('div.uc-field.uc-filter-grow', null, [
            el('label.uc-label', { for: 'f-search', text: 'Search' }),
            el('input.uc-input', {
                id: 'f-search',
                type: 'search',
                placeholder: 'Title or site name…',
                oninput: (event) => onSearch(event.target.value),
            }),
        ]),
        select('status', 'Status', options.status),
        select('purpose', 'Purpose', options.purpose),
    ]);
}

async function load() {
    await loadInto(
        listRegion,
        () => api.get('/plantation-plans', {
            quiet: true,
            query: {
                search: state.search,
                status: state.status,
                purpose: state.purpose,
                page: state.page,
                size: state.size,
                sort: 'updatedAt,desc',
            },
        }),
        renderList,
        {
            rows: 4,
            empty: {
                icon: '🌱',
                title: state.search || state.status || state.purpose
                    ? 'No plans match those filters'
                    : 'You have no plans yet',
                body: state.search || state.status || state.purpose
                    ? 'Try clearing a filter.'
                    : 'Create one to see what a planting would sequester, remove and shade.',
                action: el('a.uc-btn.uc-btn--primary', {
                    href: '/plan-editor.html',
                    text: 'Create your first plan',
                }),
            },
        },
    );
}

function renderList(page) {
    return [
        el('div.uc-tablewrap', null, [
            el('table.uc-table', null, [
                el('thead', null, [
                    el('tr', null, [
                        el('th', { text: 'Plan' }),
                        el('th', { text: 'Purpose' }),
                        el('th', { text: 'Status' }),
                        el('th.uc-right', { text: 'Species' }),
                        el('th.uc-right', { text: 'Trees' }),
                        el('th.uc-right', { text: 'Land' }),
                        el('th.uc-right', { text: 'CO₂ / yr' }),
                        el('th.uc-right', { text: 'Updated' }),
                        el('th', { text: '', 'aria-label': 'Actions' }),
                    ]),
                ]),
                el('tbody', null, page.content.map(planRow)),
            ]),
        ]),
        pager(page, (target) => {
            state.page = target;
            load();
        }),
    ];
}

function planRow(plan) {
    return el('tr', null, [
        el('td', null, [
            el('a', { href: `/plan-results.html?id=${plan.id}`, text: plan.title, style: { fontWeight: '600' } }),
            plan.siteName ? el('div.uc-small.uc-muted', { text: plan.siteName }) : null,
        ]),
        el('td.uc-small', { text: plan.purpose.label }),
        el('td', null, [statusBadge(plan.status)]),
        el('td.uc-right', { text: fmt.int(plan.speciesCount) }),
        el('td.uc-right', { text: fmt.int(plan.totalTrees) }),
        el('td.uc-right', { text: fmt.area(plan.landAreaSquareMetres) }),
        // Only a completed plan has a frozen figure. A draft shows an em dash rather than a
        // zero, because "not computed yet" and "sequesters nothing" are different claims.
        el('td.uc-right', {
            text: fmt.isMissing(plan.annualCo2Kg) ? '—' : `${fmt.num(fmt.toNumber(plan.annualCo2Kg) / 1000, 2)} t`,
            style: { color: fmt.isMissing(plan.annualCo2Kg) ? '' : 'var(--uc-m-carbon)', fontWeight: '600' },
        }),
        el('td.uc-right.uc-small.uc-muted', { text: fmt.relative(plan.updatedAt) }),
        el('td', null, [
            el('div.uc-row', { style: { gap: 'var(--uc-s1)', flexWrap: 'nowrap' } }, [
                el('a.uc-btn.uc-btn--sm.uc-btn--ghost', {
                    href: `/plan-editor.html?id=${plan.id}`,
                    text: 'Edit',
                    title: 'Edit this plan',
                }),
                el('button.uc-btn.uc-btn--sm.uc-btn--ghost', {
                    type: 'button',
                    text: 'Copy',
                    title: 'Duplicate as a new draft',
                    onclick: () => duplicate(plan.id),
                }),
                el('button.uc-btn.uc-btn--sm.uc-btn--ghost', {
                    type: 'button',
                    text: '🗑',
                    'aria-label': `Delete ${plan.title}`,
                    title: 'Delete',
                    onclick: () => remove(plan),
                }),
            ]),
        ]),
    ]);
}

async function duplicate(id) {
    const copy = await api.post(`/plantation-plans/${id}/duplicate`).catch(() => null);
    if (copy) window.location.href = `/plan-editor.html?id=${copy.id}`;
}

async function remove(plan) {
    // A soft delete is still a deletion as far as the planner is concerned, so it is
    // confirmed. The wording says what actually happens rather than "are you sure?".
    const confirmed = window.confirm(
        `Delete "${plan.title}"?\n\nIt will disappear from your plans and from every total. `
        + 'The record is retained and can be restored by an administrator.');
    if (!confirmed) return;

    await api.del(`/plantation-plans/${plan.id}`).catch(() => null);
    await load();
}

init();
