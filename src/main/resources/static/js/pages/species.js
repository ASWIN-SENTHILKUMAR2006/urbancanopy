/**
 * Species catalogue (FR-SPEC-08 … FR-SPEC-10, UC-04).
 *
 * Public: browsing the reference data before signing up is exactly the behaviour the
 * requirements describe, so this page never demands an identity.
 */

import { el, mount, debounce } from '../core/dom.js';
import { api } from '../core/api.js';
import * as fmt from '../core/format.js';
import { renderShell, pageHead } from '../components/shell.js';
import { loadInto } from '../components/states.js';
import { badge, pager } from '../components/widgets.js';

const state = {
    search: '',
    growthRate: '',
    waterRequirement: '',
    climateZone: '',
    soilType: '',
    nativeOnly: false,
    sort: 'commonName,asc',
    page: 0,
    size: 12,
};

let filterOptions = {};
const resultsRegion = el('div');

async function init() {
    await renderShell('/species.html');

    // Filter values come from the server's enums, so adding a climate zone updates this
    // form automatically instead of showing a raw constant until a second file is edited.
    filterOptions = await api.get('/tree-species/filters', { quiet: true }).catch(() => ({}));

    mount('#content', [
        pageHead('Tree species catalogue', 'Reference values with their published sources. Every figure is a per-tree annual average.'),
        buildFilters(),
        resultsRegion,
    ]);

    await load();
}

// ------------------------------------------------------------------------- filters

function buildFilters() {
    const onSearch = debounce((value) => {
        state.search = value;
        state.page = 0;
        load();
    }, 300);

    const select = (name, label, options, extra = []) =>
        el('div.uc-field', null, [
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
                ...(options || []).map((option) =>
                    el('option', { value: option.value, text: option.label })),
                ...extra,
            ]),
        ]);

    return el('div.uc-filters', null, [
        el('div.uc-field.uc-filter-grow', null, [
            el('label.uc-label', { for: 'f-search', text: 'Search' }),
            el('input.uc-input', {
                id: 'f-search',
                type: 'search',
                placeholder: 'Common, scientific or local name…',
                oninput: (event) => onSearch(event.target.value),
            }),
        ]),
        select('growthRate', 'Growth', filterOptions.growthRate),
        select('waterRequirement', 'Water', filterOptions.waterRequirement),
        select('climateZone', 'Climate', filterOptions.climateZone),
        select('soilType', 'Soil', filterOptions.soilType),
        el('div.uc-field', null, [
            el('label.uc-label', { for: 'f-sort', text: 'Sort' }),
            el('select.uc-select', {
                id: 'f-sort',
                onchange: (event) => {
                    state.sort = event.target.value;
                    state.page = 0;
                    load();
                },
            }, [
                el('option', { value: 'commonName,asc', text: 'Name A→Z' }),
                el('option', { value: 'co2SequestrationKgPerYear,desc', text: 'CO₂ high→low' }),
                el('option', { value: 'oxygenProductionKgPerYear,desc', text: 'O₂ high→low' }),
                el('option', { value: 'canopyRadiusMetres,desc', text: 'Canopy widest' }),
            ]),
        ]),
        el('div.uc-field', null, [
            el('label.uc-label', { text: 'Origin' }),
            el('label.uc-row', { style: { gap: 'var(--uc-s2)', cursor: 'pointer' } }, [
                el('input', {
                    type: 'checkbox',
                    onchange: (event) => {
                        state.nativeOnly = event.target.checked;
                        state.page = 0;
                        load();
                    },
                }),
                el('span.uc-small', { text: 'Native only' }),
            ]),
        ]),
    ]);
}

// -------------------------------------------------------------------------- results

async function load() {
    await loadInto(
        resultsRegion,
        () => api.get('/tree-species', {
            quiet: true,
            query: {
                search: state.search,
                growthRate: state.growthRate,
                waterRequirement: state.waterRequirement,
                climateZone: state.climateZone,
                soilType: state.soilType,
                nativeOnly: state.nativeOnly || '',
                page: state.page,
                size: state.size,
                sort: state.sort,
            },
        }),
        renderResults,
        {
            rows: 4,
            empty: {
                icon: '🔍',
                title: 'No species match those filters',
                body: 'Try widening the search, or clearing a filter.',
                action: el('button.uc-btn', {
                    type: 'button',
                    text: 'Clear filters',
                    onclick: () => window.location.reload(),
                }),
            },
        },
    );
}

function renderResults(page) {
    return [
        el('p.uc-small.uc-muted', {
            text: `${page.totalElements} species · values are per tree per year`,
        }),
        el('div.uc-grid', null, page.content.map(speciesCard)),
        pager(page, (target) => {
            state.page = target;
            load();
            window.scrollTo({ top: 0, behavior: 'smooth' });
        }),
    ];
}

function speciesCard(species) {
    return el('button.uc-speciescard', {
        type: 'button',
        'aria-label': `${species.commonName} — view detail`,
        onclick: () => openDetail(species.id),
    }, [
        el('div.uc-speciesimg', null, [
            species.imageUrl
                ? el('img', { src: species.imageUrl, alt: '', loading: 'lazy' })
                : el('span', { text: '🌳', 'aria-hidden': 'true' }),
        ]),
        el('div.uc-speciesbody', null, [
            el('div.uc-speciesname', null, [
                el('span.uc-truncate', { text: species.commonName }),
                species.nativeSpecies ? badge('Native', 'uc-badge--success') : null,
            ]),
            el('div.uc-sci', { text: species.scientificName }),
            el('div.uc-speciesstats', null, [
                el('span', {
                    text: `◉ ${fmt.num(species.co2SequestrationKgPerYear, 1)} kg CO₂`,
                    style: { color: 'var(--uc-m-carbon)', fontWeight: '600' },
                }),
                el('span', { text: `⚡ ${species.growthRate.label}` }),
                el('span', { text: `⌾ ${fmt.num(species.canopyRadiusMetres, 1)} m` }),
                el('span', { text: `💧 ${species.waterRequirement.label}` }),
            ]),
        ]),
    ]);
}

// --------------------------------------------------------------------------- detail

/**
 * Species detail, in a focus-trapped dialog.
 *
 * The citation is not an afterthought at the bottom — it is the reason the figures above it
 * can be quoted at all (BR-19).
 */
async function openDetail(id) {
    const species = await api.get(`/tree-species/${id}`).catch(() => null);
    if (!species) return;

    const close = () => {
        backdrop.remove();
        document.removeEventListener('keydown', onKey);
        lastFocused?.focus();
    };
    const onKey = (event) => {
        if (event.key === 'Escape') close();
    };
    const lastFocused = document.activeElement;

    const row = (label, value) => el('tr', null, [
        el('th', { text: label, scope: 'row', style: { textTransform: 'none', background: 'transparent' } }),
        el('td.uc-right', { text: value }),
    ]);

    const dialog = el('div.uc-modal', { role: 'dialog', 'aria-modal': 'true', 'aria-labelledby': 'sp-title' }, [
        el('div.uc-row', null, [
            el('div', null, [
                el('h2', { id: 'sp-title', text: species.commonName, style: { margin: '0' } }),
                el('div.uc-sci', { text: species.scientificName }),
            ]),
            el('span.uc-spacer'),
            species.nativeSpecies ? badge('Native', 'uc-badge--success') : null,
            species.active ? null : badge('Withdrawn', 'uc-badge--warning'),
            el('button.uc-iconbtn', { type: 'button', text: '×', 'aria-label': 'Close', onclick: close }),
        ]),

        species.description
            ? el('p.uc-muted', { text: species.description, style: { marginTop: 'var(--uc-s3)' } })
            : null,

        el('h3', { text: 'Environmental performance', style: { marginTop: 'var(--uc-s4)' } }),
        el('div.uc-tablewrap', null, [
            el('table.uc-table', null, [
                el('tbody', null, [
                    row('CO₂ sequestration', fmt.unit(species.co2SequestrationKgPerYear, 'kg/tree/yr', 3)),
                    row('O₂ generation', fmt.unit(species.oxygenProductionKgPerYear, 'kg/tree/yr', 3)),
                    row('PM2.5 removal', fmt.unit(species.pm25RemovalGramsPerYear, 'g/tree/yr', 3)),
                    row('PM10 removal', fmt.unit(species.pm10RemovalGramsPerYear, 'g/tree/yr', 3)),
                    row('NO₂ removal', fmt.unit(species.no2RemovalGramsPerYear, 'g/tree/yr', 3)),
                    row('SO₂ removal', fmt.unit(species.so2RemovalGramsPerYear, 'g/tree/yr', 3)),
                    row('CO removal', fmt.unit(species.coRemovalGramsPerYear, 'g/tree/yr', 3)),
                    row('Total pollutants', fmt.unit(species.totalPollutantRemovalGramsPerYear, 'g/tree/yr', 3)),
                ]),
            ]),
        ]),

        el('h3', { text: 'Horticultural profile', style: { marginTop: 'var(--uc-s4)' } }),
        el('div.uc-tablewrap', null, [
            el('table.uc-table', null, [
                el('tbody', null, [
                    row('Growth rate', species.growthRate.label),
                    row('Water requirement', species.waterRequirement.label),
                    row('Climate zone', species.climateZone.label),
                    row('Soil type', species.soilType.label),
                    row('Canopy radius', fmt.unit(species.canopyRadiusMetres, 'm')),
                    row('Canopy area at maturity', fmt.unit(species.canopyAreaSquareMetres, 'm²')),
                    row('Mature height', fmt.isMissing(species.matureHeightMetres) ? '—' : fmt.unit(species.matureHeightMetres, 'm')),
                    row('Recommended spacing', fmt.unit(species.spacingMetres, 'm')),
                    row('Life span', `${fmt.int(species.lifeSpanYears)} years`),
                    row('Used by plans', fmt.int(species.usedByPlanCount)),
                ]),
            ]),
        ]),

        el('h3', { text: 'Source', style: { marginTop: 'var(--uc-s4)' } }),
        el('p.uc-small.uc-muted', { text: species.sourceCitation }),
    ]);

    const backdrop = el('div.uc-modal-backdrop', {
        onclick: (event) => {
            if (event.target === backdrop) close();
        },
    }, [dialog]);

    document.body.appendChild(backdrop);
    document.addEventListener('keydown', onKey);
    dialog.querySelector('button')?.focus();
}

init();
