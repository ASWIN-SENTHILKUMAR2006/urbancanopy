/**
 * Plan editor — the core screen (UC-05, FR-PLAN-01…05, FR-PLAN-13).
 *
 * Two columns: the composition on the left, a live estimate on the right that updates as
 * the planner types and never reloads the page.
 *
 * The estimate panel always calls `POST /estimates/preview`, whether or not the plan has
 * been saved. That is the single decision that makes this screen work:
 *
 *   - a brand-new plan can be evaluated before it exists, so nothing half-built is written
 *     to the database on a keystroke and "undo" does not mean "delete";
 *   - a saved plan is evaluated against what is *currently in the form*, so a land area the
 *     planner has typed but not yet saved is reflected immediately rather than showing
 *     figures for the previous value.
 *
 * Persisted item changes still go to the server as they happen, because an item is a
 * sub-resource with its own rules (BR-08's increment, the per-plan limits) and the server
 * owns those.
 */

import { el, mount, debounce, $ } from '../core/dom.js';
import { api } from '../core/api.js';
import * as fmt from '../core/format.js';
import { toast } from '../core/toast.js';
import { validateForm, applyServerErrors } from '../core/validate.js';
import { renderShell, pageHead, disclaimer } from '../components/shell.js';
import { kpi, meter, badge, verdictBadge, statusBadge, statRow } from '../components/widgets.js';

const params = new URLSearchParams(window.location.search);

const state = {
    planId: params.get('id') ? Number(params.get('id')) : null,
    plan: null,
    /** Composition. For an unsaved plan these live only here until the first save. */
    items: [],
    catalogue: [],
    options: {},
    saving: false,
};

const panel = el('div.uc-card.uc-estimate-panel');
const itemsRegion = el('div');
const headRegion = el('div');

// ------------------------------------------------------------------------ bootstrap

async function init() {
    await renderShell('/plans.html');

    // The picker filters locally against the full active catalogue: 25 rows is far cheaper
    // to hold than a request per keystroke, and the result feels instant.
    const [options, catalogue] = await Promise.all([
        api.get('/plantation-plans/options', { quiet: true }).catch(() => ({})),
        api.get('/tree-species/options', { quiet: true }).catch(() => []),
    ]);
    state.options = options || {};
    state.catalogue = catalogue || [];

    if (state.planId) {
        state.plan = await api.get(`/plantation-plans/${state.planId}`, { quiet: true }).catch(() => null);
        if (!state.plan) {
            mount('#content', [pageHead('Plan not found', 'It may have been deleted.'),
                el('a.uc-btn', { href: '/plans.html', text: '← Back to my plans' })]);
            return;
        }
        state.items = state.plan.items.map(toLocalItem);
    }

    render();
    schedulePreview.flush();
}

function toLocalItem(item) {
    return {
        itemId: item.id,
        speciesId: item.speciesId,
        commonName: item.commonName,
        scientificName: item.scientificName,
        quantity: item.quantity,
        co2: item.co2KgPerTreeYear,
        canopyRadius: item.canopyRadiusMetres,
    };
}

// --------------------------------------------------------------------------- render

function render() {
    mount('#content', [
        headRegion,
        el('div.uc-editor', null, [
            el('div.uc-stack', null, [buildDetailsCard(), buildCompositionCard()]),
            panel,
        ]),
    ]);
    renderHead();
    renderItems();
}

function renderHead() {
    const plan = state.plan;
    mount(headRegion, pageHead(
        plan ? plan.title : 'New plantation plan',
        plan ? `${plan.purpose.label} · ${fmt.area(plan.landAreaSquareMetres)} · ${fmt.int(plan.totalTrees)} trees`
             : 'Describe the site, then compose the planting. The estimate updates as you type.',
        [
            plan ? statusBadge(plan.status) : badge('Unsaved', 'uc-badge--muted'),
            plan ? el('a.uc-btn', { href: `/plan-results.html?id=${plan.id}`, text: 'Full results' }) : null,
            el('button.uc-btn', { type: 'button', id: 'btn-save', text: 'Save', onclick: () => save(false) }),
            el('button.uc-btn.uc-btn--primary', {
                type: 'button', id: 'btn-complete', text: 'Save & complete', onclick: () => save(true),
            }),
        ].filter(Boolean),
    ));
}

/** ① Plan details. */
function buildDetailsCard() {
    const plan = state.plan;
    const onInput = () => schedulePreview();

    const field = (name, label, control, hint = null) => el('div.uc-field', null, [
        el('label.uc-label', { for: name }, [label, el('span.uc-req', { text: ' *' })]),
        control,
        hint ? el('span.uc-hint', { text: hint }) : null,
    ]);

    const optional = (name, label, control) => el('div.uc-field', null, [
        el('label.uc-label', { for: name, text: label }),
        control,
    ]);

    const selectFrom = (name, list, selected) => el('select.uc-select', {
        id: name, name, oninput: onInput,
    }, (list || []).map((option) => el('option', {
        value: option.value,
        text: option.label,
        selected: option.value === selected,
    })));

    return el('form.uc-card', { id: 'plan-form', onsubmit: (e) => { e.preventDefault(); save(false); } }, [
        el('div.uc-section-label', { text: '① Plan details' }),

        field('title', 'Title', el('input.uc-input', {
            id: 'title', name: 'title', maxlength: '150', required: true,
            value: plan?.title || '', placeholder: 'Anna Nagar Avenue Drive',
        })),

        optional('siteName', 'Site name', el('input.uc-input', {
            id: 'siteName', name: 'siteName', maxlength: '180',
            value: plan?.siteName || '', placeholder: 'Anna Nagar 2nd Avenue',
        })),

        field('purpose', 'Purpose', selectFrom('purpose', state.options.purpose, plan?.purpose?.value)),

        el('div.uc-field', null, [
            el('label.uc-label', { for: 'landAreaInput' }, ['Available land', el('span.uc-req', { text: ' *' })]),
            el('div.uc-inline', null, [
                el('input.uc-input', {
                    id: 'landAreaInput', name: 'landAreaInput', type: 'number', step: 'any', min: '0.0001',
                    required: true, value: plan?.landAreaInput ?? '', oninput: onInput, style: { flex: '2' },
                }),
                el('select.uc-select', {
                    id: 'landAreaUnit', name: 'landAreaUnit', oninput: onInput, style: { flex: '1' },
                }, (state.options.landAreaUnit || []).map((option) => el('option', {
                    value: option.value,
                    text: `${option.label} (${option.description})`,
                    selected: option.value === (plan?.landAreaUnit?.value || 'SQUARE_METRE'),
                }))),
            ]),
            el('span.uc-hint', { id: 'area-normalised', text: '' }),
        ]),

        el('div.uc-inline', null, [
            el('div.uc-field', { style: { flex: '1' } }, [
                el('label.uc-label', { for: 'targetStartYear' }, ['Start year', el('span.uc-req', { text: ' *' })]),
                el('input.uc-input', {
                    id: 'targetStartYear', name: 'targetStartYear', type: 'number', min: '1900', max: '2200',
                    required: true, value: plan?.targetStartYear ?? new Date().getFullYear(), oninput: onInput,
                }),
            ]),
            el('div.uc-field', { style: { flex: '1' } }, [
                el('label.uc-label', { for: 'projectionYears' }, ['Projection (years)', el('span.uc-req', { text: ' *' })]),
                el('input.uc-input', {
                    id: 'projectionYears', name: 'projectionYears', type: 'number', min: '1', max: '50',
                    required: true, value: plan?.projectionYears ?? 10, oninput: onInput,
                }),
            ]),
        ]),

        optional('description', 'Description', el('textarea.uc-textarea', {
            id: 'description', name: 'description', maxlength: '4000',
            text: plan?.description || '',
        })),
    ]);
}

/** ② Species composition. */
function buildCompositionCard() {
    const search = el('input.uc-input', {
        id: 'species-search',
        type: 'search',
        placeholder: 'Search the catalogue to add a species…',
        autocomplete: 'off',
        oninput: (event) => renderSuggestions(event.target.value),
        onfocus: (event) => renderSuggestions(event.target.value),
    });

    return el('div.uc-card', null, [
        el('div.uc-section-label', { text: '② Species composition' }),
        el('div.uc-field', null, [search, el('div', { id: 'species-suggestions' })]),
        itemsRegion,
    ]);
}

function renderSuggestions(term) {
    const box = $('#species-suggestions');
    if (!box) return;

    const query = (term || '').trim().toLowerCase();
    if (!query) {
        box.replaceChildren();
        return;
    }

    const chosen = new Set(state.items.map((item) => item.speciesId));
    const matches = state.catalogue
        .filter((species) =>
            species.commonName.toLowerCase().includes(query)
            || species.scientificName.toLowerCase().includes(query)
            || (species.localName || '').toLowerCase().includes(query))
        .slice(0, 6);

    mount(box, matches.length ? matches.map((species) => el('button.uc-btn', {
        type: 'button',
        style: { width: '100%', justifyContent: 'flex-start', marginTop: 'var(--uc-s1)' },
        onclick: () => addSpecies(species),
    }, [
        el('span', { text: '🌳', 'aria-hidden': 'true' }),
        el('span', null, [
            el('div', { text: species.commonName, style: { fontWeight: '600' } }),
            el('div.uc-small.uc-muted', {
                text: `${species.scientificName} · ${fmt.num(species.co2SequestrationKgPerYear, 1)} kg CO₂/yr · ${species.growthRate.label}`,
            }),
        ]),
        chosen.has(species.id) ? badge('Already in plan', 'uc-badge--info') : null,
    ])) : el('p.uc-small.uc-muted', { text: 'No species match that search.', style: { marginTop: 'var(--uc-s2)' } }));
}

function renderItems() {
    if (!state.items.length) {
        mount(itemsRegion, el('div.uc-state', null, [
            el('div.uc-state-icon', { text: '🌱', 'aria-hidden': 'true' }),
            el('p.uc-state-body', { text: 'No species yet. Search above to add the first one.' }),
        ]));
        return;
    }

    const totalTrees = state.items.reduce((sum, item) => sum + item.quantity, 0);

    mount(itemsRegion, el('div.uc-tablewrap', null, [
        el('table.uc-table', null, [
            el('thead', null, [
                el('tr', null, [
                    el('th', { text: 'Species' }),
                    el('th.uc-right', { text: 'Quantity' }),
                    el('th.uc-right', { text: 'CO₂ / tree / yr' }),
                    el('th', { text: '', 'aria-label': 'Remove' }),
                ]),
            ]),
            el('tbody', null, state.items.map(itemRow)),
            el('tfoot', null, [
                el('tr', null, [
                    el('td', { text: `${state.items.length} species` }),
                    el('td.uc-right', { text: fmt.int(totalTrees) }),
                    el('td', { text: '' }),
                    el('td', { text: '' }),
                ]),
            ]),
        ]),
    ]));
}

function itemRow(item) {
    const quantityInput = el('input.uc-input', {
        type: 'number', min: '1', step: '1', value: item.quantity,
        'aria-label': `Quantity of ${item.commonName}`,
        style: { width: '7rem', textAlign: 'right' },
    });

    // Debounced per row: holding the up-arrow should be one request, not thirty.
    const commit = debounce(async () => {
        const quantity = Number(quantityInput.value);
        if (!Number.isInteger(quantity) || quantity < 1) {
            quantityInput.value = item.quantity;
            toast.warning('Quantity must be at least 1 tree. Remove the species instead.');
            return;
        }
        await setQuantity(item, quantity);
    }, 400);

    quantityInput.addEventListener('input', commit);

    return el('tr', null, [
        el('td', null, [
            el('div', { text: item.commonName, style: { fontWeight: '600' } }),
            el('div.uc-sci', { text: item.scientificName }),
        ]),
        el('td.uc-right', null, [quantityInput]),
        el('td.uc-right', { text: fmt.num(item.co2, 2) }),
        el('td.uc-right', null, [
            el('button.uc-btn.uc-btn--sm.uc-btn--ghost', {
                type: 'button', text: '×',
                'aria-label': `Remove ${item.commonName}`,
                onclick: () => removeItem(item),
            }),
        ]),
    ]);
}

// ---------------------------------------------------------------- composition edits

async function addSpecies(species) {
    const existing = state.items.find((item) => item.speciesId === species.id);

    if (state.planId) {
        // The server owns BR-08: adding a species already present increments it.
        const updated = await api.post(`/plantation-plans/${state.planId}/items`,
            { speciesId: species.id, quantity: existing ? 1 : 100 }).catch(() => null);
        if (!updated) return;
        applyServerPlan(updated);
    } else {
        if (existing) {
            existing.quantity += 1;
            toast.info(`${species.commonName} is already in this plan — its quantity was increased.`);
        } else {
            state.items.push({
                itemId: null,
                speciesId: species.id,
                commonName: species.commonName,
                scientificName: species.scientificName,
                quantity: 100,
                co2: species.co2SequestrationKgPerYear,
                canopyRadius: species.canopyRadiusMetres,
            });
        }
        renderItems();
    }

    const search = $('#species-search');
    if (search) {
        search.value = '';
        $('#species-suggestions')?.replaceChildren();
        search.focus();
    }
    schedulePreview();
}

async function setQuantity(item, quantity) {
    if (state.planId && item.itemId) {
        const updated = await api.put(
            `/plantation-plans/${state.planId}/items/${item.itemId}`, { quantity }, { quiet: true },
        ).catch(() => null);
        if (!updated) return;
        applyServerPlan(updated);
    } else {
        item.quantity = quantity;
        renderItems();
    }
    schedulePreview();
}

async function removeItem(item) {
    if (state.planId && item.itemId) {
        const updated = await api.del(`/plantation-plans/${state.planId}/items/${item.itemId}`).catch(() => null);
        if (!updated) return;
        applyServerPlan(updated);
    } else {
        state.items = state.items.filter((candidate) => candidate !== item);
        renderItems();
    }
    schedulePreview();
}

/** Re-seats local state from a server response, so the two never drift. */
function applyServerPlan(plan) {
    state.plan = plan;
    state.planId = plan.id;
    state.items = plan.items.map(toLocalItem);
    renderItems();
    renderHead();
}

// ---------------------------------------------------------------------- live estimate

/**
 * Reads the form without validating it.
 *
 * The live panel must tolerate a half-typed form — a planner mid-way through "12" has
 * momentarily entered "1", and an estimate that threw at that moment would flicker an
 * error on every keystroke.
 */
function readForm() {
    const form = $('#plan-form');
    if (!form) return null;
    const value = (name) => form.elements.namedItem(name)?.value ?? '';

    return {
        title: value('title').trim(),
        siteName: value('siteName').trim() || null,
        description: value('description').trim() || null,
        landAreaInput: Number(value('landAreaInput')) || 0,
        landAreaUnit: value('landAreaUnit') || 'SQUARE_METRE',
        purpose: value('purpose'),
        targetStartYear: Number(value('targetStartYear')) || new Date().getFullYear(),
        projectionYears: Number(value('projectionYears')) || 1,
    };
}

const schedulePreview = debounce(runPreview, 300);

async function runPreview() {
    const form = readForm();
    if (!form) return;

    const chip = $('#live-chip');
    chip?.classList.add('is-busy');

    const started = performance.now();
    const estimate = await api.post('/estimates/preview', {
        landAreaInput: form.landAreaInput,
        landAreaUnit: form.landAreaUnit,
        projectionYears: form.projectionYears,
        targetStartYear: form.targetStartYear,
        items: state.items.map((item) => ({ speciesId: item.speciesId, quantity: item.quantity })),
    }, { quiet: true }).catch((error) => ({ __error: error }));

    const elapsed = Math.round(performance.now() - started);
    renderPanel(estimate, elapsed);
    renderNormalisedArea(estimate);
}

/** Shows "= 12,000 m²" under the land area, so a unit conversion is never a surprise. */
function renderNormalisedArea(estimate) {
    const hint = $('#area-normalised');
    if (!hint) return;
    hint.textContent = estimate && !estimate.__error && estimate.landAreaSquareMetres
        ? `= ${fmt.num(estimate.landAreaSquareMetres, 0)} m²`
        : '';
}

function renderPanel(estimate, elapsedMs) {
    const chip = el('span.uc-livechip', { id: 'live-chip' }, [
        el('span', { text: '⟳', 'aria-hidden': 'true' }),
        el('span', { text: `${elapsedMs} ms` }),
    ]);

    if (!estimate || estimate.__error) {
        mount(panel, [
            el('div.uc-card-head', null, [el('h2.uc-card-title', { text: 'Live estimate' }), el('span.uc-spacer'), chip]),
            el('p.uc-small.uc-muted', {
                text: estimate?.__error?.message || 'The estimate could not be computed.',
            }),
        ]);
        return;
    }

    const result = estimate.result;

    if (!result.sufficientData) {
        mount(panel, [
            el('div.uc-card-head', null, [el('h2.uc-card-title', { text: 'Live estimate' }), el('span.uc-spacer'), chip]),
            el('div.uc-state', null, [
                el('div.uc-state-icon', { text: '🌱', 'aria-hidden': 'true' }),
                el('p.uc-state-body', { text: 'Add a species to see what this planting would do.' }),
            ]),
        ]);
        return;
    }

    const carbon = result.carbon;
    const equivalence = result.equivalences?.[0];

    mount(panel, [
        el('div.uc-card-head', null, [
            el('h2.uc-card-title', { text: 'Live estimate' }),
            el('span.uc-spacer'),
            chip,
        ]),

        el('div.uc-stack', null, [
            kpi({
                label: 'CO₂ sequestered / year',
                value: fmt.num(carbon.annualTonnes, 2),
                unit: 't/yr',
                // The label matters as much as the figure: "≈ 65,288 km" alone leaves the
                // reader to guess km of what.
                note: equivalence
                    ? `≈ ${fmt.num(equivalence.value, 0)} ${equivalence.unit} · ${equivalence.label.toLowerCase()}`
                    : null,
                token: '--uc-m-carbon',
            }),

            el('div.uc-row', null, [
                el('span.uc-small', null, [
                    el('strong', { text: fmt.num(result.oxygen.annualKg, 0), style: { color: 'var(--uc-m-oxygen)' } }),
                    ' kg O₂/yr',
                ]),
                el('span.uc-spacer'),
                el('span.uc-small', null, [
                    '☁ ',
                    el('strong', { text: fmt.num(result.pollutants.totalKg, 1), style: { color: 'var(--uc-m-pm25)' } }),
                    ' kg pollutants/yr',
                ]),
            ]),

            el('div', null, [
                el('div.uc-section-label', { text: 'Canopy' }),
                statRow('At maturity', fmt.area(result.canopy.totalAreaSquareMetres)),
                meter(result.canopy.coveragePercent, '--uc-m-canopy'),
                el('p.uc-small.uc-muted', {
                    text: fmt.isMissing(result.canopy.coveragePercent)
                        ? 'No land area given, so coverage is not applicable.'
                        : `${fmt.percent(result.canopy.coveragePercent)} of the site`
                          + (fmt.toNumber(result.canopy.uncappedCoveragePercent) > 100
                              ? ` (${fmt.percent(result.canopy.uncappedCoveragePercent)} before overlap is capped)` : ''),
                    style: { marginTop: 'var(--uc-s1)' },
                }),
            ]),

            el('div', null, [
                el('div.uc-section-label', { text: 'Space feasibility' }),
                el('div.uc-row', null, [
                    verdictBadge(result.feasibility.verdict, verdictSeverity(result.feasibility.verdict),
                        verdictLabel(result.feasibility.verdict)),
                    el('span.uc-spacer'),
                    el('span.uc-small.uc-muted', {
                        text: fmt.isMissing(result.feasibility.utilisationPercent)
                            ? '—' : `${fmt.percent(result.feasibility.utilisationPercent)} utilised`,
                    }),
                ]),
                el('p.uc-small.uc-muted', {
                    text: feasibilityNote(result.feasibility),
                    style: { marginTop: 'var(--uc-s2)' },
                }),
            ]),

            el('div', null, [
                el('div.uc-section-label', { text: 'Impact score' }),
                el('div.uc-row', null, [
                    el('strong', { text: `${fmt.num(result.impactScore.overall, 0)}/100`, style: { fontSize: '1.25rem' } }),
                    el('span.uc-spacer'),
                    el('span.uc-small.uc-muted', { text: `Diversity H′ ${fmt.num(result.diversity.shannonIndex, 2)}` }),
                ]),
                meter(result.impactScore.overall),
            ]),

            ...(result.warnings || []).map((warning) =>
                el('div.uc-badge.uc-badge--warning', {
                    style: { display: 'block', whiteSpace: 'normal', padding: 'var(--uc-s2) var(--uc-s3)' },
                    text: `⚠ ${warning}`,
                })),

            disclaimer(result.disclaimer),
        ]),
    ]);
}

function verdictSeverity(verdict) {
    return { OPTIMAL: 'success', TIGHT: 'warning', OVERCROWDED: 'danger', UNDER_UTILISED: 'info' }[verdict] || 'muted';
}

function verdictLabel(verdict) {
    return { OPTIMAL: 'Optimal', TIGHT: 'Tight', OVERCROWDED: 'Overcrowded', UNDER_UTILISED: 'Under-utilised' }[verdict]
        || 'Not applicable';
}

function feasibilityNote(feasibility) {
    if (fmt.isMissing(feasibility.utilisationPercent)) {
        return 'Enter a land area to assess whether the planting fits.';
    }
    const surplus = fmt.toNumber(feasibility.surplusAreaSquareMetres);
    const max = feasibility.maxPlantableTrees;
    // Advisory, never blocking (BR-13) — the note explains, it does not forbid.
    return surplus < 0
        ? `${fmt.num(Math.abs(surplus), 0)} m² short at the recommended spacing. About ${fmt.int(max)} trees would fit.`
        : `${fmt.num(surplus, 0)} m² spare · about ${fmt.int(max)} trees would fit at this mix.`;
}

// ------------------------------------------------------------------------- persistence

const SCHEMA = {
    title: { required: true, maxLength: 150, requiredMessage: 'A plan title is required.' },
    landAreaInput: { required: true, positive: true, requiredMessage: 'Available land area is required.' },
    targetStartYear: { required: true, min: 1900, max: 2200, integer: true },
    projectionYears: { required: true, min: 1, max: 50, integer: true },
};

async function save(alsoComplete) {
    if (state.saving) return;

    const form = $('#plan-form');
    if (!validateForm(form, SCHEMA)) {
        toast.warning('Please correct the highlighted fields.');
        return;
    }

    setSaving(true);
    try {
        const body = readForm();
        let plan;

        if (state.planId) {
            plan = await api.put(`/plantation-plans/${state.planId}`, body, { quiet: true });
        } else {
            plan = await api.post('/plantation-plans', body, { quiet: true });
            state.planId = plan.id;

            // The composition was held locally until the plan existed; persist it now.
            for (const item of state.items) {
                plan = await api.post(`/plantation-plans/${plan.id}/items`,
                    { speciesId: item.speciesId, quantity: item.quantity }, { quiet: true });
            }
        }

        applyServerPlan(plan);

        if (alsoComplete) {
            const completed = await api.patch(`/plantation-plans/${state.planId}/status`, { status: 'COMPLETED' });
            applyServerPlan(completed);
            window.location.href = `/plan-results.html?id=${state.planId}`;
            return;
        }

        toast.success('Your plan has been saved.');
        // Reflect the id in the URL so a refresh reopens the saved plan rather than a blank one.
        window.history.replaceState({}, '', `/plan-editor.html?id=${state.planId}`);
    } catch (error) {
        if (error.isValidation) applyServerErrors(form, error);
        // Anything else has already been surfaced as a toast by the api module.
    } finally {
        setSaving(false);
    }
}

function setSaving(busy) {
    state.saving = busy;
    for (const id of ['#btn-save', '#btn-complete']) {
        const button = $(id);
        if (button) button.disabled = busy;
    }
}

init();
