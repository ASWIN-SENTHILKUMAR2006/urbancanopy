/**
 * Plan results — the full impact breakdown (UC-06, FR-CALC-01…15).
 *
 * Progressive disclosure: KPIs first, the per-species table second, the calculation trace
 * on demand. A results screen that opened with eighteen figures at equal weight would be
 * accurate and unreadable.
 */

import { el, mount } from '../core/dom.js';
import { api } from '../core/api.js';
import * as fmt from '../core/format.js';
import { renderShell, pageHead, disclaimer } from '../components/shell.js';
import { loadInto } from '../components/states.js';
import { kpi, meter, barRow, badge, verdictBadge, statusBadge, statRow } from '../components/widgets.js';

const planId = new URLSearchParams(window.location.search).get('id');
const region = el('div');

async function init() {
    await renderShell('/plans.html');

    if (!planId) {
        mount('#content', [
            pageHead('No plan selected', 'Choose a plan to see its results.'),
            el('a.uc-btn.uc-btn--primary', { href: '/plans.html', text: '← My plans' }),
        ]);
        return;
    }

    mount('#content', [region]);

    await loadInto(
        region,
        () => api.get(`/plantation-plans/${planId}/estimate`, { quiet: true }),
        render,
        { rows: 5, isEmpty: () => false },
    );
}

function render(estimate) {
    const result = estimate.result;

    const head = pageHead(
        estimate.planTitle,
        [
            estimate.siteName,
            estimate.purpose?.label,
            fmt.area(estimate.landAreaSquareMetres),
            `${fmt.int(result.totalTrees)} trees`,
            `from ${estimate.targetStartYear}`,
        ].filter(Boolean).join(' · '),
        [
            statusBadge(estimate.status),
            el('a.uc-btn', { href: `/compare.html?planIds=${planId}`, text: '⇄ Compare' }),
            // A plain link, not a fetch: the response is a PDF, so the browser's own download
            // handling is exactly right and api.js has no part to play.
            el('a.uc-btn.uc-btn--primary', {
                href: `/api/v1/reports/plans/${planId}/pdf`,
                text: '⬇ PDF report',
                title: 'Download the full impact report',
            }),
            el('a.uc-btn', { href: `/plan-editor.html?id=${planId}`, text: 'Edit' }),
        ],
    );

    if (!result.sufficientData) {
        return [head, el('div.uc-state', null, [
            el('div.uc-state-icon', { text: '🌱', 'aria-hidden': 'true' }),
            el('h2.uc-state-title', { text: 'This plan has no species yet' }),
            el('p.uc-state-body', { text: 'Add at least one species to produce an estimate.' }),
            el('a.uc-btn.uc-btn--primary', { href: `/plan-editor.html?id=${planId}`, text: 'Open the editor' }),
        ])];
    }

    return [
        head,
        provenanceNote(estimate),
        kpiRow(result),
        contributionsCard(result),
        el('div.uc-editor', null, [pollutantCard(result), canopyCard(result)]),
        el('div.uc-editor', null, [projectionCard(result, estimate), equivalenceCard(result)]),
        ...(result.warnings || []).map((warning) =>
            el('div.uc-badge.uc-badge--warning', {
                style: { display: 'block', whiteSpace: 'normal', padding: 'var(--uc-s3) var(--uc-s4)' },
                text: `⚠ ${warning}`,
            })),
        traceCard(result),
        disclaimer(result.disclaimer),
    ];
}

/**
 * States plainly which coefficient set produced these figures.
 *
 * A reader is entitled to know whether they are looking at numbers frozen when the plan was
 * completed or numbers that will move the next time an administrator revises a constant.
 */
function provenanceNote(estimate) {
    return el('p.uc-small.uc-muted', {
        text: estimate.fromSnapshot
            ? `Figures fixed when this plan was completed on ${fmt.dateTime(estimate.snapshotComputedAt)}, `
              + `using engine ${estimate.engineVersion}. Later changes to the coefficient registry do not affect them.`
            : `Computed just now against the current coefficient registry, using engine ${estimate.engineVersion}. `
              + 'Completing the plan will fix these figures.',
    });
}

function kpiRow(result) {
    return el('div.uc-kpis', null, [
        kpi({
            label: 'CO₂ / year', value: fmt.num(result.carbon.annualTonnes, 2), unit: 't',
            note: `${fmt.num(result.carbon.horizonTonnes, 1)} t over the horizon`, token: '--uc-m-carbon',
        }),
        kpi({
            label: 'O₂ / year', value: fmt.num(result.oxygen.annualKg, 0), unit: 'kg', token: '--uc-m-oxygen',
        }),
        kpi({
            label: 'Pollutants / year', value: fmt.num(result.pollutants.totalKg, 1), unit: 'kg',
            note: 'PM2.5, PM10, NO₂, SO₂, CO', token: '--uc-m-pm25',
        }),
        kpi({
            label: 'Canopy coverage',
            value: fmt.isMissing(result.canopy.coveragePercent) ? '—' : fmt.num(result.canopy.coveragePercent, 1),
            unit: '%', note: fmt.area(result.canopy.totalAreaSquareMetres), token: '--uc-m-canopy',
        }),
        kpi({
            label: 'Impact score', value: fmt.num(result.impactScore.overall, 0), unit: '/100',
            note: `Diversity H′ ${fmt.num(result.diversity.shannonIndex, 2)}`,
        }),
    ]);
}

/** Per-species contribution — where the plan's carbon actually comes from. */
function contributionsCard(result) {
    const totals = result.contributions.reduce((acc, row) => ({
        quantity: acc.quantity + row.quantity,
        effective: acc.effective + fmt.toNumber(row.effectiveTrees),
        co2: acc.co2 + fmt.toNumber(row.annualCo2Kg),
        o2: acc.o2 + fmt.toNumber(row.annualO2Kg),
        pm25: acc.pm25 + fmt.toNumber(row.annualPm25Grams),
        canopy: acc.canopy + fmt.toNumber(row.canopyAreaSquareMetres),
    }), { quantity: 0, effective: 0, co2: 0, o2: 0, pm25: 0, canopy: 0 });

    return el('div.uc-card', null, [
        el('div.uc-card-head', null, [
            el('h2.uc-card-title', { text: 'Species contribution' }),
            el('span.uc-spacer'),
            el('span.uc-small.uc-muted', { text: 'Effective trees = planted × survival × maturity' }),
        ]),
        el('div.uc-tablewrap', null, [
            el('table.uc-table', null, [
                el('thead', null, [
                    el('tr', null, [
                        el('th', { text: 'Species' }),
                        el('th.uc-right', { text: 'Qty' }),
                        el('th.uc-right', { text: 'Effective' }),
                        el('th.uc-right', { text: 'CO₂ t/yr' }),
                        el('th.uc-right', { text: 'O₂ kg/yr' }),
                        el('th.uc-right', { text: 'PM2.5 g/yr' }),
                        el('th.uc-right', { text: 'Canopy m²' }),
                        el('th.uc-right', { text: 'Share' }),
                    ]),
                ]),
                el('tbody', null, result.contributions.map((row) => el('tr', null, [
                    el('td', null, [
                        el('div', { text: row.commonName, style: { fontWeight: '600' } }),
                        el('div.uc-sci', { text: row.scientificName }),
                    ]),
                    el('td.uc-right', { text: fmt.int(row.quantity) }),
                    el('td.uc-right', { text: fmt.num(row.effectiveTrees, 0) }),
                    el('td.uc-right', { text: fmt.num(fmt.toNumber(row.annualCo2Kg) / 1000, 2) }),
                    el('td.uc-right', { text: fmt.num(row.annualO2Kg, 0) }),
                    el('td.uc-right', { text: fmt.num(row.annualPm25Grams, 0) }),
                    el('td.uc-right', { text: fmt.num(row.canopyAreaSquareMetres, 0) }),
                    el('td.uc-right', { text: fmt.percent(row.co2SharePercent) }),
                ]))),
                el('tfoot', null, [
                    el('tr', null, [
                        el('td', { text: 'Total' }),
                        el('td.uc-right', { text: fmt.int(totals.quantity) }),
                        el('td.uc-right', { text: fmt.num(totals.effective, 0) }),
                        el('td.uc-right', { text: fmt.num(totals.co2 / 1000, 2) }),
                        el('td.uc-right', { text: fmt.num(totals.o2, 0) }),
                        el('td.uc-right', { text: fmt.num(totals.pm25, 0) }),
                        el('td.uc-right', { text: fmt.num(totals.canopy, 0) }),
                        el('td.uc-right', { text: '100 %' }),
                    ]),
                ]),
            ]),
        ]),
    ]);
}

/**
 * Pollutant breakdown as CSS bars.
 *
 * <p>One hue for all five bars, not one per pollutant. The reader's job here is to compare
 * magnitudes across categories that are already named on the axis, so a colour per pollutant
 * would encode nothing the label does not — and the project's five metric colours cannot carry
 * it anyway: measured with the data-visualisation validator, PM2.5 against PM10 scores ΔE 9.4
 * for normal vision against a floor of 15, and three of the five fall below the chroma floor
 * (they read as grey). Length carries the comparison; the figure beside each bar means the bar
 * is never the sole carrier of the information (NFR-07).</p>
 */
function pollutantCard(result) {
    const rows = result.pollutants.breakdown || [];
    const max = Math.max(...rows.map((row) => fmt.toNumber(row.grams)), 0);

    return el('div.uc-card', null, [
        el('h2.uc-card-title', { text: 'Pollutant removal' }),
        el('p.uc-small.uc-muted', { text: 'Grams removed per year, at maturity.' }),
        el('div.uc-bars', { style: { marginTop: 'var(--uc-s3)' } }, rows.map((row) => barRow({
            label: row.label,
            value: row.grams,
            max,
            display: `${fmt.num(row.kilograms, 1)} kg`,
            token: '--uc-m-pm25',
        }))),
    ]);
}

function canopyCard(result) {
    const feasibility = result.feasibility;

    return el('div.uc-card', null, [
        el('h2.uc-card-title', { text: 'Canopy & space assessment' }),
        el('div.uc-stack--tight', { style: { marginTop: 'var(--uc-s3)' } }, [
            statRow('Land available', fmt.area(result.canopy.landAreaSquareMetres)),
            statRow('Canopy at maturity', fmt.area(result.canopy.totalAreaSquareMetres)),
            meter(result.canopy.coveragePercent, '--uc-m-canopy'),
            statRow('Coverage', fmt.isMissing(result.canopy.coveragePercent)
                ? 'Not applicable' : fmt.percent(result.canopy.coveragePercent)),
            statRow('Area required at spacing', fmt.area(feasibility.requiredAreaSquareMetres)),
            statRow('Space utilised', fmt.isMissing(feasibility.utilisationPercent)
                ? 'Not applicable' : fmt.percent(feasibility.utilisationPercent)),
            el('div.uc-row', { style: { marginTop: 'var(--uc-s2)' } }, [
                verdictBadge(feasibility.verdict,
                    { OPTIMAL: 'success', TIGHT: 'warning', OVERCROWDED: 'danger', UNDER_UTILISED: 'info' }[feasibility.verdict] || 'muted',
                    { OPTIMAL: 'Optimal', TIGHT: 'Tight', OVERCROWDED: 'Overcrowded', UNDER_UTILISED: 'Under-utilised' }[feasibility.verdict] || 'Not applicable'),
                el('span.uc-spacer'),
                el('span.uc-small.uc-muted', {
                    text: feasibility.maxPlantableTrees === null || feasibility.maxPlantableTrees === undefined
                        ? '' : `max ${fmt.int(feasibility.maxPlantableTrees)} trees at this mix`,
                }),
            ]),
            el('p.uc-small.uc-muted', {
                text: 'This assessment is advisory. A dense planting may still be the right choice — '
                    + 'it simply will not reach the canopy this estimate assumes.',
            }),
        ]),
    ]);
}

/** Multi-year projection, as a table. The line chart arrives with Chart.js in Phase 10. */
function projectionCard(result, estimate) {
    const points = result.projection || [];
    const peak = Math.max(...points.map((point) => fmt.toNumber(point.cumulativeCo2Kg)), 0);

    return el('div.uc-card', null, [
        el('h2.uc-card-title', { text: `${estimate.projectionYears}-year cumulative CO₂` }),
        el('p.uc-small.uc-muted', { text: 'Annual rate rises as the planting matures.' }),
        el('div.uc-bars', { style: { marginTop: 'var(--uc-s3)' } }, points.map((point) => barRow({
            label: String(point.calendarYear),
            value: point.cumulativeCo2Kg,
            max: peak,
            display: `${fmt.num(fmt.toNumber(point.cumulativeCo2Kg) / 1000, 1)} t`,
            token: '--uc-m-carbon',
        }))),
    ]);
}

function equivalenceCard(result) {
    const items = result.equivalences || [];
    if (!items.length) return el('div');

    return el('div.uc-card', null, [
        el('h2.uc-card-title', { text: 'Equivalent to, each year' }),
        el('div.uc-stack--tight', { style: { marginTop: 'var(--uc-s3)' } },
            items.map((item) => el('div', null, [
                el('div.uc-row', null, [
                    el('span', { text: item.label }),
                    el('span.uc-spacer'),
                    el('strong.uc-num', { text: `${fmt.num(item.value, 0)} ${item.unit}` }),
                ]),
                item.description ? el('div.uc-small.uc-muted', { text: item.description }) : null,
            ]))),
    ]);
}

/**
 * "How is this calculated?" (FR-CALC-15).
 *
 * Collapsed by default and complete when opened. It is the strongest available answer to
 * "is this just a black box?", which is the question every estimate invites.
 */
function traceCard(result) {
    const entries = result.trace || [];
    if (!entries.length) return el('div');

    const body = el('div.uc-tablewrap.uc-hidden', null, [
        el('table.uc-table', null, [
            el('thead', null, [
                el('tr', null, [
                    el('th', { text: 'Figure' }),
                    el('th', { text: 'Arithmetic' }),
                    el('th.uc-right', { text: 'Result' }),
                ]),
            ]),
            el('tbody', null, entries.map((entry) => el('tr', null, [
                el('td', { text: entry.label }),
                el('td.uc-small.uc-mono', { text: entry.expression }),
                el('td.uc-right', { text: entry.result }),
            ]))),
        ]),
    ]);

    const toggle = el('button.uc-btn', {
        type: 'button',
        text: `ⓘ How is this calculated? (${entries.length} steps)`,
        'aria-expanded': 'false',
        onclick: () => {
            const hidden = body.classList.toggle('uc-hidden');
            toggle.setAttribute('aria-expanded', String(!hidden));
        },
    });

    return el('div.uc-card', null, [toggle, el('div', { style: { marginTop: 'var(--uc-s3)' } }, [body])]);
}

init();
