/**
 * Dashboard (FR-DASH-01 … FR-DASH-09, UC-07).
 *
 * The KPI row and the charts are loaded separately and on purpose: the headline figures are
 * what the reader looks at first and depend on nothing else, so they do not wait behind four
 * aggregations, and a chart that fails renders its own error state without taking the numbers
 * down with it.
 *
 * Every chart is a single hue — see `components/charts.js` for why — and every chart ships
 * with a toggleable data table, so no figure is carried by a picture alone (NFR-07).
 */

import { el, mount } from '../core/dom.js';
import { api } from '../core/api.js';
import * as fmt from '../core/format.js';
import { renderShell, pageHead, disclaimer } from '../components/shell.js';
import { loadInto, emptyState } from '../components/states.js';
import { kpi, statusBadge } from '../components/widgets.js';
import { renderChartCard, emptyChartCard } from '../components/charts.js';

const summaryRegion = el('div');
const chartsRegion = el('div');
const adminRegion = el('div');

const CHARTS = [
    { key: 'co2-by-species', form: 'hbar' },
    { key: 'pollutant-breakdown', form: 'hbar' },
    { key: 'co2-projection', form: 'line' },
    { key: 'plans-by-purpose', form: 'hbar' },
];

async function init() {
    await renderShell('/dashboard.html');

    mount('#content', [
        pageHead('Dashboard', 'Estimated impact across all of your plantation plans.', [
            el('a.uc-btn.uc-btn--primary', { href: '/plan-editor.html', text: '+ New plan' }),
        ]),
        summaryRegion,
        chartsRegion,
        adminRegion,
    ]);

    const summary = await loadInto(
        summaryRegion,
        () => api.get('/dashboard/summary', { quiet: true }),
        renderSummary,
        { rows: 3, isEmpty: () => false },
    );

    // No plans means no charts worth drawing — an empty canvas reads as a broken one, so the
    // page says so instead (FR-DASH-09).
    if (!summary) return;
    if (summary.kpis.totalPlans === 0) {
        mount(chartsRegion, emptyState({
            icon: '🌱',
            title: 'Nothing to chart yet',
            body: 'Create a plan and its figures will appear here.',
            action: el('a.uc-btn.uc-btn--primary', { href: '/plan-editor.html', text: 'Create your first plan' }),
        }));
        return;
    }

    await renderCharts();
    if (summary.admin) renderAdmin(summary.admin);
}

// -------------------------------------------------------------------------- summary

function renderSummary(summary) {
    const k = summary.kpis;

    return [
        el('div.uc-kpis', null, [
            kpi({
                label: 'CO₂ / year', value: fmt.num(k.annualCo2Tonnes, 2), unit: 't',
                note: `across ${fmt.int(k.totalPlans)} plan(s)`, token: '--uc-m-carbon',
            }),
            kpi({
                label: 'O₂ / year', value: fmt.num(k.annualO2Kg, 0), unit: 'kg', token: '--uc-m-oxygen',
            }),
            kpi({
                label: 'Pollutants / year', value: fmt.num(k.annualPollutantKg, 1), unit: 'kg',
                note: 'PM2.5, PM10, NO₂, SO₂, CO', token: '--uc-m-pm25',
            }),
            kpi({
                label: 'Canopy at maturity', value: fmt.num(k.canopyAreaSquareMetres, 0), unit: 'm²',
                token: '--uc-m-canopy',
            }),
            kpi({ label: 'Trees planned', value: fmt.int(k.totalTrees) }),
            kpi({
                label: 'Average score',
                // An average of nothing is not zero; a planner with only empty drafts sees a dash.
                value: fmt.isMissing(k.averageImpactScore) ? '—' : fmt.num(k.averageImpactScore, 0),
                unit: fmt.isMissing(k.averageImpactScore) ? '' : '/100',
                note: `${fmt.int(k.completedPlans)} completed · ${fmt.int(k.draftPlans)} draft`,
            }),
        ]),

        summary.recentPlans.length ? recentPlansCard(summary.recentPlans) : null,
        disclaimer(summary.disclaimer),
    ];
}

function recentPlansCard(plans) {
    return el('div.uc-card', { style: { marginTop: 'var(--uc-s5)' } }, [
        el('div.uc-card-head', null, [
            el('h2.uc-card-title', { text: 'Recently updated' }),
            el('span.uc-spacer'),
            el('a.uc-btn.uc-btn--sm.uc-btn--ghost', { href: '/plans.html', text: 'All plans →' }),
        ]),
        el('div.uc-tablewrap', null, [
            el('table.uc-table', null, [
                el('thead', null, [
                    el('tr', null, [
                        el('th', { text: 'Plan' }),
                        el('th', { text: 'Purpose' }),
                        el('th', { text: 'Status' }),
                        el('th.uc-right', { text: 'Trees' }),
                        el('th.uc-right', { text: 'Updated' }),
                    ]),
                ]),
                el('tbody', null, plans.map((plan) => el('tr', null, [
                    el('td', null, [
                        el('a', { href: `/plan-results.html?id=${plan.id}`, text: plan.title }),
                        plan.siteName ? el('div.uc-small.uc-muted', { text: plan.siteName }) : null,
                    ]),
                    el('td.uc-small', { text: plan.purpose.label }),
                    el('td', null, [statusBadge(plan.status)]),
                    el('td.uc-right', { text: fmt.int(plan.totalTrees) }),
                    el('td.uc-right.uc-small.uc-muted', { text: fmt.relative(plan.updatedAt) }),
                ]))),
            ]),
        ]),
    ]);
}

// --------------------------------------------------------------------------- charts

async function renderCharts() {
    const grid = el('div.uc-editor', { style: { marginTop: 'var(--uc-s5)' } });
    mount(chartsRegion, grid);

    // Sequentially rather than in parallel: each request re-evaluates every plan through the
    // engine, and four at once would contend for the same connections to no benefit on a page
    // that is already showing its headline figures.
    for (const { key, form } of CHARTS) {
        const slot = el('div');
        grid.appendChild(slot);

        await loadInto(
            slot,
            () => api.get(`/dashboard/charts/${key}`, { quiet: true }),
            (chartData) => (chartData.points.length
                ? renderChartCard(chartData, form)
                : emptyChartCard(chartData.title, 'No data for this chart yet.')),
            { rows: 3, isEmpty: () => false },
        );
    }
}

// ---------------------------------------------------------------------------- admin

function renderAdmin(admin) {
    mount(adminRegion, [
        el('h2', { text: 'System-wide', style: { marginTop: 'var(--uc-s6)' } }),
        el('p.uc-small.uc-muted', { text: 'Visible to administrators only.' }),

        el('div.uc-kpis', { style: { marginTop: 'var(--uc-s4)' } }, [
            kpi({ label: 'Users', value: fmt.int(admin.totalUsers), note: `${fmt.int(admin.activeUsers)} enabled` }),
            kpi({ label: 'Species', value: fmt.int(admin.totalSpecies), note: `${fmt.int(admin.activeSpecies)} active` }),
            kpi({ label: 'Plans', value: fmt.int(admin.totalPlans), note: 'across all planners' }),
        ]),

        admin.topSpecies.length ? el('div.uc-card', { style: { marginTop: 'var(--uc-s4)' } }, [
            el('h3.uc-card-title', { text: 'Most-used species' }),
            el('div.uc-tablewrap', { style: { marginTop: 'var(--uc-s3)' } }, [
                el('table.uc-table', null, [
                    el('thead', null, [
                        el('tr', null, [
                            el('th', { text: 'Species' }),
                            el('th.uc-right', { text: 'Plans' }),
                            el('th.uc-right', { text: 'Trees' }),
                        ]),
                    ]),
                    el('tbody', null, admin.topSpecies.map((row) => el('tr', null, [
                        el('td', { text: row.commonName }),
                        el('td.uc-right', { text: fmt.int(row.planCount) }),
                        el('td.uc-right', { text: fmt.int(row.totalTrees) }),
                    ]))),
                ]),
            ]),
        ]) : null,
    ]);
}

init();
