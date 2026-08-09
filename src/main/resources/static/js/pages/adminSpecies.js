/**
 * Species curation (FR-SPEC-01 … FR-SPEC-07, FR-SPEC-11, BR-18).
 *
 * The rule that shapes this screen is BR-18: a species any plan depends on is
 * **deactivated, never deleted**. Deleting it would either break every plan that used it or
 * silently change their figures. So deletion is offered only where it is safe, the delete
 * control is disabled with a reason where it is not, and the server refuses regardless.
 *
 * Deactivated species stay visible here. A curation screen that hid them would make them
 * unreachable and therefore unrecoverable, which is the opposite of what deactivation is
 * for.
 */

import { el, mount, debounce, $ } from '../core/dom.js';
import { api } from '../core/api.js';
import * as fmt from '../core/format.js';
import { toast } from '../core/toast.js';
import { validateForm, applyServerErrors } from '../core/validate.js';
import { pageHead } from '../components/shell.js';
import { loadInto } from '../components/states.js';
import { badge, pager } from '../components/widgets.js';
import {
    startAdminPage, confirmAction, openModal, field, selectField, textareaField, checkboxField, formSection,
} from '../components/admin.js';

const state = { search: '', growthRate: '', page: 0, size: 15 };
let enums = {};
const region = el('div');

async function init() {
    const permitted = await startAdminPage('/admin/species.html', 'Species curation', 'The reference catalogue.');
    if (!permitted) return;

    enums = await api.get('/tree-species/filters', { quiet: true }).catch(() => ({}));

    mount('#content', [
        pageHead('Species curation', 'Reference values used by every calculation. Deactivated species stay listed here.', [
            el('button.uc-btn.uc-btn--primary', { type: 'button', text: '+ New species', onclick: () => openForm(null) }),
        ]),
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
                placeholder: 'Common, scientific or local name…',
                oninput: (event) => onSearch(event.target.value),
            }),
        ]),
        el('div.uc-field', null, [
            el('label.uc-label', { for: 'f-growth', text: 'Growth' }),
            el('select.uc-select', {
                id: 'f-growth',
                onchange: (event) => {
                    state.growthRate = event.target.value;
                    state.page = 0;
                    load();
                },
            }, [
                el('option', { value: '', text: 'All' }),
                ...(enums.growthRate || []).map((o) => el('option', { value: o.value, text: o.label })),
            ]),
        ]),
    ]);
}

async function load() {
    await loadInto(
        region,
        () => api.get('/admin/tree-species', {
            quiet: true,
            query: {
                search: state.search,
                growthRate: state.growthRate,
                page: state.page,
                size: state.size,
                sort: 'commonName,asc',
            },
        }),
        render,
        {
            rows: 5,
            empty: {
                icon: '🌱',
                title: 'No species match',
                body: 'Try clearing the search, or add a new species.',
                action: el('button.uc-btn.uc-btn--primary', {
                    type: 'button', text: '+ New species', onclick: () => openForm(null),
                }),
            },
        },
    );
}

function render(page) {
    return [
        el('div.uc-tablewrap', null, [
            el('table.uc-table', null, [
                el('thead', null, [
                    el('tr', null, [
                        el('th', { text: 'Species' }),
                        el('th.uc-right', { text: 'CO₂ kg/yr' }),
                        el('th.uc-right', { text: 'O₂ kg/yr' }),
                        el('th.uc-right', { text: 'Pollutants g/yr' }),
                        el('th', { text: 'Growth' }),
                        el('th', { text: 'Status' }),
                        el('th', { text: '', 'aria-label': 'Actions' }),
                    ]),
                ]),
                el('tbody', null, page.content.map(speciesRow)),
            ]),
        ]),
        pager(page, (target) => {
            state.page = target;
            load();
        }),
    ];
}

function speciesRow(species) {
    return el('tr', null, [
        el('td', null, [
            el('div', { style: { fontWeight: '600' } }, [
                el('span', { text: species.commonName }),
                species.nativeSpecies ? badge('Native', 'uc-badge--success') : null,
            ]),
            el('div.uc-sci', { text: species.scientificName }),
        ]),
        el('td.uc-right', { text: fmt.num(species.co2SequestrationKgPerYear, 2) }),
        el('td.uc-right', { text: fmt.num(species.oxygenProductionKgPerYear, 2) }),
        el('td.uc-right', { text: fmt.num(species.totalPollutantRemovalGramsPerYear, 1) }),
        el('td.uc-small', { text: species.growthRate.label }),
        el('td', null, [
            species.active ? badge('Active', 'uc-badge--success') : badge('Withdrawn', 'uc-badge--warning'),
        ]),
        el('td', null, [
            el('div.uc-row', { style: { gap: 'var(--uc-s1)', flexWrap: 'nowrap' } }, [
                el('button.uc-btn.uc-btn--sm.uc-btn--ghost', {
                    type: 'button', text: 'Edit', onclick: () => openForm(species.id),
                }),
                el('button.uc-btn.uc-btn--sm.uc-btn--ghost', {
                    type: 'button',
                    text: species.active ? 'Deactivate' : 'Activate',
                    title: species.active
                        ? 'Remove from new plans; existing plans keep working'
                        : 'Offer this species in new plans again',
                    onclick: () => setActive(species),
                }),
                el('button.uc-btn.uc-btn--sm.uc-btn--ghost', {
                    type: 'button', text: '🗑', 'aria-label': `Delete ${species.commonName}`,
                    onclick: () => remove(species),
                }),
            ]),
        ]),
    ]);
}

// ------------------------------------------------------------------------- mutations

async function setActive(species) {
    if (species.active) {
        const confirmed = confirmAction(
            `Deactivate "${species.commonName}"?\n\nIt will no longer appear when composing a new plan. `
            + 'Plans that already include it keep working and keep their figures.');
        if (!confirmed) return;
    }

    const updated = await api.patch(`/admin/tree-species/${species.id}/status`, { active: !species.active })
        .catch(() => null);
    if (updated) await load();
}

/**
 * Deletion, which usually is not permitted.
 *
 * The species is fetched first so the plan-dependency count can be shown in the prompt. The
 * server refuses with 422 and an explanation if anything depends on it, so this is a
 * courtesy rather than the enforcement.
 */
async function remove(species) {
    const detail = await api.get(`/tree-species/${species.id}`, { quiet: true }).catch(() => null);
    const usedBy = detail?.usedByPlanCount ?? 0;

    if (usedBy > 0) {
        toast.warning(
            `"${species.commonName}" is used by ${usedBy} plan(s) and cannot be deleted. `
            + 'Deactivate it instead — it will disappear from new plans while existing plans keep working.');
        return;
    }

    const confirmed = confirmAction(
        `Delete "${species.commonName}" permanently?\n\nNo plan uses it, so nothing will break. `
        + 'This cannot be undone — deactivating instead is reversible.');
    if (!confirmed) return;

    await api.del(`/admin/tree-species/${species.id}`).catch(() => null);
    await load();
}

// ------------------------------------------------------------------------- the form

const SCHEMA = {
    commonName: { required: true, maxLength: 120 },
    scientificName: { required: true, maxLength: 160 },
    co2SequestrationKgPerYear: { required: true, min: 0 },
    oxygenProductionKgPerYear: { required: true, min: 0 },
    pm25RemovalGramsPerYear: { required: true, min: 0 },
    pm10RemovalGramsPerYear: { required: true, min: 0 },
    no2RemovalGramsPerYear: { required: true, min: 0 },
    so2RemovalGramsPerYear: { required: true, min: 0 },
    coRemovalGramsPerYear: { required: true, min: 0 },
    canopyRadiusMetres: { required: true, positive: true },
    lifeSpanYears: { required: true, min: 1, integer: true },
    spacingMetres: { required: true, positive: true },
    sourceCitation: { required: true, maxLength: 500 },
};

async function openForm(id) {
    const species = id ? await api.get(`/tree-species/${id}`, { quiet: true }).catch(() => null) : null;
    if (id && !species) return;

    const number = (name, label, value, step = '0.001', hint = null) =>
        field(name, label, { type: 'number', step, min: '0', value: value ?? '' }, { required: true, hint });

    const form = el('form', { id: 'species-form', onsubmit: (event) => event.preventDefault() }, [
        formSection('Identity'),
        field('commonName', 'Common name', { value: species?.commonName || '', maxlength: '120' }, { required: true }),
        field('scientificName', 'Scientific name', { value: species?.scientificName || '', maxlength: '160' }, { required: true }),
        el('div.uc-inline', null, [
            el('div', { style: { flex: '1' } }, [field('family', 'Family', { value: species?.family || '', maxlength: '100' })]),
            el('div', { style: { flex: '1' } }, [field('localName', 'Local name', { value: species?.localName || '', maxlength: '120' })]),
        ]),
        textareaField('description', 'Description', species?.description),

        formSection('Environmental performance — per tree, per year'),
        el('div.uc-inline', null, [
            el('div', { style: { flex: '1' } }, [number('co2SequestrationKgPerYear', 'CO₂ (kg)', species?.co2SequestrationKgPerYear)]),
            el('div', { style: { flex: '1' } }, [number('oxygenProductionKgPerYear', 'O₂ (kg)', species?.oxygenProductionKgPerYear)]),
        ]),
        el('div.uc-inline', null, [
            el('div', { style: { flex: '1' } }, [number('pm25RemovalGramsPerYear', 'PM2.5 (g)', species?.pm25RemovalGramsPerYear)]),
            el('div', { style: { flex: '1' } }, [number('pm10RemovalGramsPerYear', 'PM10 (g)', species?.pm10RemovalGramsPerYear)]),
        ]),
        el('div.uc-inline', null, [
            el('div', { style: { flex: '1' } }, [number('no2RemovalGramsPerYear', 'NO₂ (g)', species?.no2RemovalGramsPerYear)]),
            el('div', { style: { flex: '1' } }, [number('so2RemovalGramsPerYear', 'SO₂ (g)', species?.so2RemovalGramsPerYear)]),
        ]),
        number('coRemovalGramsPerYear', 'CO (g)', species?.coRemovalGramsPerYear),

        formSection('Horticultural profile'),
        el('div.uc-inline', null, [
            el('div', { style: { flex: '1' } }, [selectField('growthRate', 'Growth rate', enums.growthRate, species?.growthRate?.value, { required: true })]),
            el('div', { style: { flex: '1' } }, [selectField('waterRequirement', 'Water', enums.waterRequirement, species?.waterRequirement?.value, { required: true })]),
        ]),
        el('div.uc-inline', null, [
            el('div', { style: { flex: '1' } }, [selectField('climateZone', 'Climate zone', enums.climateZone, species?.climateZone?.value, { required: true })]),
            el('div', { style: { flex: '1' } }, [selectField('soilType', 'Soil type', enums.soilType, species?.soilType?.value, { required: true })]),
        ]),
        el('div.uc-inline', null, [
            el('div', { style: { flex: '1' } }, [number('canopyRadiusMetres', 'Canopy radius (m)', species?.canopyRadiusMetres, '0.1')]),
            el('div', { style: { flex: '1' } }, [number('spacingMetres', 'Spacing (m)', species?.spacingMetres, '0.1')]),
        ]),
        el('div.uc-inline', null, [
            el('div', { style: { flex: '1' } }, [
                field('matureHeightMetres', 'Mature height (m)', {
                    type: 'number', step: '0.1', min: '0', value: species?.matureHeightMetres ?? '',
                }),
            ]),
            el('div', { style: { flex: '1' } }, [number('lifeSpanYears', 'Life span (years)', species?.lifeSpanYears, '1')]),
        ]),
        checkboxField('nativeSpecies', 'Native species', species?.nativeSpecies),

        formSection('Provenance'),
        field('sourceCitation', 'Literature source', {
            value: species?.sourceCitation || '', maxlength: '500',
        }, {
            required: true,
            // BR-19: an unsourced reference value is not a reference value.
            hint: 'Every reference value must be traceable to a published source.',
        }),
        field('imageUrl', 'Image URL', { type: 'url', value: species?.imageUrl || '', maxlength: '500' }),

        ...(species ? [
            formSection('Photograph'),
            el('div.uc-row', null, [
                el('input', { type: 'file', id: 'image-file', accept: 'image/jpeg,image/png,image/webp' }),
                el('button.uc-btn.uc-btn--sm', {
                    type: 'button', text: 'Upload', onclick: () => uploadImage(species.id),
                }),
            ]),
            el('span.uc-hint', { text: 'JPEG, PNG or WebP, up to 2 MB. Replaces any existing image.' }),
        ] : []),
    ]);

    const saveButton = el('button.uc-btn.uc-btn--primary', {
        type: 'button',
        text: species ? 'Save changes' : 'Create species',
        onclick: () => submit(species, modal.close, saveButton),
    });

    const modal = openModal({
        title: species ? `Edit ${species.commonName}` : 'New species',
        body: form,
        footer: [
            saveButton,
            el('button.uc-btn', { type: 'button', text: 'Cancel', onclick: () => modal.close() }),
            species?.usedByPlanCount
                ? el('span.uc-small.uc-muted', {
                    text: `Used by ${species.usedByPlanCount} plan(s) — changes will revise their estimates.`,
                })
                : null,
        ],
    });
}

async function submit(species, close, button) {
    const form = $('#species-form');
    if (!validateForm(form, SCHEMA)) {
        toast.warning('Please correct the highlighted fields.');
        return;
    }

    const value = (name) => form.elements.namedItem(name)?.value?.trim() ?? '';
    const numeric = (name) => {
        const raw = value(name);
        return raw === '' ? null : Number(raw);
    };

    const body = {
        commonName: value('commonName'),
        scientificName: value('scientificName'),
        family: value('family') || null,
        localName: value('localName') || null,
        description: value('description') || null,
        imageUrl: value('imageUrl') || null,
        co2SequestrationKgPerYear: numeric('co2SequestrationKgPerYear'),
        oxygenProductionKgPerYear: numeric('oxygenProductionKgPerYear'),
        pm25RemovalGramsPerYear: numeric('pm25RemovalGramsPerYear'),
        pm10RemovalGramsPerYear: numeric('pm10RemovalGramsPerYear'),
        no2RemovalGramsPerYear: numeric('no2RemovalGramsPerYear'),
        so2RemovalGramsPerYear: numeric('so2RemovalGramsPerYear'),
        coRemovalGramsPerYear: numeric('coRemovalGramsPerYear'),
        growthRate: value('growthRate'),
        waterRequirement: value('waterRequirement'),
        climateZone: value('climateZone'),
        soilType: value('soilType'),
        canopyRadiusMetres: numeric('canopyRadiusMetres'),
        matureHeightMetres: numeric('matureHeightMetres'),
        lifeSpanYears: numeric('lifeSpanYears'),
        spacingMetres: numeric('spacingMetres'),
        nativeSpecies: form.elements.namedItem('nativeSpecies')?.checked ?? false,
        sourceCitation: value('sourceCitation'),
    };

    button.disabled = true;
    try {
        if (species) {
            await api.put(`/admin/tree-species/${species.id}`, body, { quiet: false });
        } else {
            await api.post('/admin/tree-species', body, { quiet: false });
        }
        close();
        await load();
    } catch (error) {
        // 400 validation and 409 duplicate-name both attach to a field.
        if (error.isValidation || error.status === 409) {
            applyServerErrors(form, error);
            if (error.status === 409) toast.warning(error.message);
        }
    } finally {
        button.disabled = false;
    }
}

async function uploadImage(id) {
    const input = $('#image-file');
    const file = input?.files?.[0];
    if (!file) {
        toast.warning('Choose an image file first.');
        return;
    }

    await api.upload(`/admin/tree-species/${id}/image`, file).catch(() => null);
    await load();
}

init();
