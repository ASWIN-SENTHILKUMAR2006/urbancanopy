/**
 * Plan comparison (UC-08, FR-CMP-01 … FR-CMP-03).
 *
 * The table is read across a row, so the server returns metrics × plans and this module
 * renders it in that shape without transposing anything.
 *
 * The normalised block is not decoration. A 2,400-tree plan wins every total against a
 * 480-tree plan regardless of which is the better design; without per-tree and per-100 m²
 * rows the screen would rank by size while appearing to rank by quality.
 */

import { el, mount } from '../core/dom.js';
import { api } from '../core/api.js';
import * as fmt from '../core/format.js';
import { renderShell, pageHead, disclaimer } from '../components/shell.js';
import { loadInto, emptyState } from '../components/states.js';
import { badge, statusBadge } from '../components/widgets.js';

const MIN_PLANS = 2;
const MAX_PLANS = 4;

const selected = new Set(
    (new URLSearchParams(window.location.search).get('planIds') || '')
        .split(',')
        .map((value) => Number(value))
        .filter((value) => Number.isInteger(value) && value > 0),
);

let available = [];
const pickerRegion = el('div.uc-card');
const resultRegion = el('div');

async function init() {
    await renderShell('/compare.html');

    mount('#content', [
        pageHead('Compare plans', `Select ${MIN_PLANS} to ${MAX_PLANS} of your plans. Totals favour the larger plan — the normalised rows do not.`),
        pickerRegion,
        resultRegion,
    ]);

    const page = await api.get('/plantation-plans', {
        quiet: true,
        query: { size: 100, sort: 'updatedAt,desc' },
    }).catch(() => null);

    available = page?.content || [];
    renderPicker();
    await refresh();
}

function renderPicker() {
    if (!available.length) {
        mount(pickerRegion, emptyState({
            icon: '▤',
            title: 'You have no plans to compare',
            body: 'Create at least two plans, then return here.',
            action: el('a.uc-btn.uc-btn--primary', { href: '/plan-editor.html', text: 'Create a plan' }),
        }));
        return;
    }

    mount(pickerRegion, [
        el('div.uc-section-label', { text: `Selected ${selected.size} of ${MAX_PLANS}` }),
        el('div.uc-row', null, available.map((plan) => {
            const isSelected = selected.has(plan.id);
            // The 5th selection is refused with a reason rather than silently ignored.
            const atLimit = !isSelected && selected.size >= MAX_PLANS;

            return el('button.uc-btn.uc-btn--sm', {
                type: 'button',
                'aria-pressed': String(isSelected),
                disabled: atLimit,
                title: atLimit ? `${MAX_PLANS} plans is the readable maximum` : plan.title,
                style: isSelected
                    ? { background: 'var(--uc-primary)', borderColor: 'var(--uc-primary)', color: 'var(--uc-on-primary)' }
                    : null,
                onclick: () => {
                    if (isSelected) selected.delete(plan.id); else selected.add(plan.id);
                    syncUrl();
                    renderPicker();
                    refresh();
                },
            }, [
                el('span', { text: isSelected ? '✓' : '+', 'aria-hidden': 'true' }),
                el('span', { text: plan.title }),
            ]);
        })),
    ]);
}

function syncUrl() {
    const query = selected.size ? `?planIds=${[...selected].join(',')}` : '';
    window.history.replaceState({}, '', `/compare.html${query}`);
}

async function refresh() {
    if (selected.size < MIN_PLANS) {
        mount(resultRegion, emptyState({
            icon: '⇄',
            title: `Select at least ${MIN_PLANS} plans`,
            body: 'A comparison needs two columns to compare.',
        }));
        return;
    }

    await loadInto(
        resultRegion,
        () => api.get('/comparisons', { quiet: true, query: { planIds: [...selected].join(',') } }),
        render,
        { rows: 6, isEmpty: () => false },
    );
}

function render(comparison) {
    return [
        el('div.uc-tablewrap', { style: { marginTop: 'var(--uc-s4)' } }, [
            el('table.uc-table', null, [
                el('thead', null, [
                    el('tr', null, [
                        el('th', { text: 'Metric' }),
                        ...comparison.plans.map((plan) => el('th.uc-right', null, [
                            el('div', { text: plan.title }),
                            el('div', { style: { fontWeight: '400', textTransform: 'none' } }, [
                                statusBadge(plan.status),
                            ]),
                        ])),
                    ]),
                ]),
                el('tbody', null, comparison.groups.flatMap((group) => [
                    el('tr', null, [
                        el('td', {
                            colspan: String(comparison.plans.length + 1),
                            style: { background: 'var(--uc-surface-alt)', fontWeight: '700' },
                        }, [
                            el('span', { text: group.label }),
                            el('span.uc-small.uc-muted', {
                                text: ` — ${group.description}`,
                                style: { fontWeight: '400' },
                            }),
                        ]),
                    ]),
                    ...group.metrics.map((metric) => metricRow(metric, comparison.plans)),
                ])),
            ]),
        ]),

        el('div.uc-row', null, [
            el('p.uc-small.uc-muted', {
                text: '★ marks the best value on that row.',
                style: { margin: '0' },
            }),
            el('span.uc-spacer'),
            el('a.uc-btn.uc-btn--primary', {
                href: `/api/v1/reports/comparison/pdf?planIds=${[...selected].join(',')}`,
                text: '⬇ Comparison PDF',
            }),
        ]),
        disclaimer(comparison.disclaimer),
    ];
}

function metricRow(metric, plans) {
    return el('tr', null, [
        el('td', null, [
            el('span', { text: metric.label }),
            metric.unit ? el('span.uc-small.uc-muted', { text: ` (${metric.unit})` }) : null,
        ]),
        ...metric.values.map((value, index) => {
            const isBest = metric.bestPlanId !== null
                && metric.bestPlanId !== undefined
                && plans[index].id === metric.bestPlanId;

            return el('td.uc-right', {
                style: isBest ? { color: `var(${metric.colourToken})`, fontWeight: '700' } : null,
            }, [
                isBest ? el('span', { text: '★ ', 'aria-hidden': 'true' }) : null,
                el('span', { text: fmt.isMissing(value) ? '—' : fmt.num(value, 2) }),
                isBest ? el('span.uc-sr-only', { text: ' (best)' }) : null,
            ]);
        }),
    ]);
}

init();
