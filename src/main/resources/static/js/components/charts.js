/**
 * Chart rendering — one place, so every chart in the application obeys the same rules.
 *
 * <h2>Why every chart here is a single hue</h2>
 * Each dashboard chart answers "compare these magnitudes", and for that job the reader takes
 * identity from the category label, not from a colour. Giving each pollutant its own hue would
 * repeat what the axis already says — and the project's own metric palette cannot carry it:
 * measured with the data-visualisation validator, PM2.5 against PM10 scores ΔE 9.4 for normal
 * vision against a floor of 15, and three of the five read as grey. Length does the work
 * instead. Colour returns as a carrier of identity only where the series genuinely are the
 * subject.
 *
 * <h2>Canvas needs real colours</h2>
 * Chart.js draws to a canvas, which cannot read `var(--uc-m-carbon)`. Tokens are therefore
 * resolved to concrete values at render time — and re-resolved when the theme changes, which
 * is why {@link renderChartCard} re-reads them on `uc:themechange` rather than caching.
 *
 * <h2>The table is not optional</h2>
 * NFR-07: a chart is never the sole carrier of its information. Every chart ships with an
 * adjacent, toggleable data table built from the same numbers.
 */

import { el, mount } from '../core/dom.js';
import * as fmt from '../core/format.js';

/** Resolves a CSS custom property to the concrete colour the canvas needs. */
function token(name, fallback = '#1B6B47') {
    const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
    return value || fallback;
}

/** The recessive chrome every chart shares: hairline grid, muted ticks, text-token labels. */
function chrome() {
    return {
        grid: token('--uc-border', '#DCE4DD'),
        tick: token('--uc-ink-muted', '#788A7F'),
        ink: token('--uc-ink', '#132119'),
        surface: token('--uc-surface', '#FFFFFF'),
    };
}

function baseOptions({ horizontal = false, unit = '', wholeNumbers = false } = {}) {
    const c = chrome();

    // A count axis must not offer 0.2 of a plan. Chart.js will happily interpolate ticks
    // between 0 and 2, and the result reads as a precision the data does not have.
    const valueTicks = { color: c.tick, font: { size: 11 } };
    if (wholeNumbers) valueTicks.precision = 0;

    return {
        indexAxis: horizontal ? 'y' : 'x',
        responsive: true,
        maintainAspectRatio: false,
        animation: { duration: 220 },
        // A single series is named by the chart's own title; a legend box would repeat it.
        plugins: {
            legend: { display: false },
            tooltip: {
                backgroundColor: c.surface,
                titleColor: c.ink,
                bodyColor: c.ink,
                borderColor: c.grid,
                borderWidth: 1,
                padding: 10,
                displayColors: false,
                callbacks: {
                    label: (context) => `${fmt.num(context.parsed[horizontal ? 'x' : 'y'], 2)} ${unit}`,
                },
            },
        },
        scales: {
            x: {
                grid: { color: c.grid, drawBorder: false, display: !horizontal ? false : true },
                ticks: horizontal ? valueTicks : { color: c.tick, font: { size: 11 } },
                border: { color: c.grid },
                beginAtZero: horizontal,
            },
            y: {
                grid: { color: c.grid, drawBorder: false, display: horizontal ? false : true },
                ticks: horizontal ? { color: c.tick, font: { size: 11 } } : valueTicks,
                border: { color: c.grid },
                beginAtZero: true,
            },
        },
    };
}

/**
 * A bar chart.
 *
 * Thin marks with a 4px rounded end on the data end only — the end anchored to the baseline
 * stays square, because rounding it would lift the bar off its own zero and misreport small
 * values. A 2px surface-coloured border separates adjacent bars.
 */
function barConfig(chartData, { horizontal }) {
    const colour = token(chartData.colourToken);
    const c = chrome();
    const values = chartData.points.map((point) => fmt.toNumber(point.value));
    const wholeNumbers = values.every((value) => Number.isInteger(value));

    return {
        type: 'bar',
        data: {
            labels: chartData.points.map((point) => point.label),
            datasets: [{
                data: values,
                backgroundColor: colour,
                hoverBackgroundColor: colour,
                borderColor: c.surface,
                borderWidth: 2,
                borderRadius: { topLeft: 0, topRight: 4, bottomLeft: 0, bottomRight: 4 },
                borderSkipped: horizontal ? 'left' : 'bottom',
                barPercentage: 0.7,
                categoryPercentage: 0.85,
                // Without a ceiling, a two-category chart draws two 90px slabs: Chart.js
                // divides the plot height between the categories it has. Thin marks are a
                // property of the mark, not of how many happen to be on screen.
                maxBarThickness: 26,
            }],
        },
        options: baseOptions({ horizontal, unit: chartData.unit, wholeNumbers }),
    };
}

/** A line chart: 2px stroke, 8px markers, a faint fill to weight the area under the curve. */
function lineConfig(chartData) {
    const colour = token(chartData.colourToken);
    const c = chrome();

    return {
        type: 'line',
        data: {
            labels: chartData.points.map((point) => point.label),
            datasets: [{
                data: chartData.points.map((point) => fmt.toNumber(point.value)),
                borderColor: colour,
                backgroundColor: withAlpha(colour, 0.12),
                borderWidth: 2,
                fill: true,
                tension: 0.25,
                pointRadius: 4,
                pointHoverRadius: 6,
                pointBackgroundColor: colour,
                pointBorderColor: c.surface,
                pointBorderWidth: 2,
            }],
        },
        options: {
            ...baseOptions({ unit: chartData.unit }),
            interaction: { mode: 'index', intersect: false },
        },
    };
}

/** Applies alpha to a hex colour, for the area fill under a line. */
function withAlpha(hex, alpha) {
    const clean = hex.replace('#', '');
    if (clean.length !== 6) return hex;
    const r = parseInt(clean.slice(0, 2), 16);
    const g = parseInt(clean.slice(2, 4), 16);
    const b = parseInt(clean.slice(4, 6), 16);
    return `rgba(${r}, ${g}, ${b}, ${alpha})`;
}

/**
 * The data table that accompanies every chart (NFR-07).
 *
 * Built from the same numbers the chart is drawn from, so the two cannot disagree.
 */
function dataTable(chartData) {
    const total = chartData.points.reduce((sum, point) => sum + fmt.toNumber(point.value), 0);

    return el('div.uc-tablewrap', null, [
        el('table.uc-table', null, [
            el('thead', null, [
                el('tr', null, [
                    el('th', { text: 'Category' }),
                    el('th.uc-right', { text: chartData.unit }),
                    el('th.uc-right', { text: 'Share' }),
                ]),
            ]),
            el('tbody', null, chartData.points.map((point) => el('tr', null, [
                el('td', null, [
                    el('div', { text: point.label }),
                    point.secondaryLabel ? el('div.uc-sci', { text: point.secondaryLabel }) : null,
                ]),
                el('td.uc-right', { text: fmt.num(point.value, 2) }),
                el('td.uc-right', {
                    text: total ? fmt.percent((fmt.toNumber(point.value) / total) * 100) : '—',
                }),
            ]))),
        ]),
    ]);
}

/**
 * Renders a complete chart card: title, chart, table toggle, table, footnote.
 *
 * @param {object} chartData the server's `ChartData`
 * @param {'bar'|'hbar'|'line'} form which mark to draw
 * @returns {Element} the card, with its own re-render bound to theme changes
 */
export function renderChartCard(chartData, form = 'bar') {
    const canvas = el('canvas', { role: 'img', 'aria-label': `${chartData.title}. A data table follows.` });
    const plot = el('div', { style: { height: '260px', position: 'relative' } }, [canvas]);

    const table = dataTable(chartData);
    table.classList.add('uc-hidden');

    const toggle = el('button.uc-btn.uc-btn--sm.uc-btn--ghost', {
        type: 'button',
        text: 'table ⇄ chart',
        'aria-expanded': 'false',
        onclick: () => {
            const tableHidden = table.classList.toggle('uc-hidden');
            plot.classList.toggle('uc-hidden', !tableHidden);
            toggle.setAttribute('aria-expanded', String(!tableHidden));
        },
    });

    const card = el('div.uc-card', null, [
        el('div.uc-card-head', null, [
            el('div', null, [
                el('h2.uc-card-title', { text: chartData.title }),
                chartData.subtitle ? el('p.uc-small.uc-muted', { text: chartData.subtitle, style: { margin: '0' } }) : null,
            ]),
            el('span.uc-spacer'),
            toggle,
        ]),
        plot,
        table,
        chartData.footnote
            ? el('p.uc-small.uc-muted', { text: chartData.footnote, style: { marginTop: 'var(--uc-s2)' } })
            : null,
    ]);

    let chart = null;
    const draw = () => {
        chart?.destroy();
        const config = form === 'line' ? lineConfig(chartData) : barConfig(chartData, { horizontal: form === 'hbar' });
        // eslint-disable-next-line no-undef
        chart = new Chart(canvas, config);
    };

    draw();
    // Canvas colours are baked at draw time, so a theme change means a redraw.
    document.addEventListener('uc:themechange', draw);

    return card;
}

/** An empty-state card for a chart with nothing to show (FR-DASH-09). */
export function emptyChartCard(title, message) {
    return el('div.uc-card', null, [
        el('h2.uc-card-title', { text: title }),
        el('div.uc-state', null, [
            el('div.uc-state-icon', { text: '🌱', 'aria-hidden': 'true' }),
            el('p.uc-state-body', { text: message }),
        ]),
    ]);
}

export { mount };
