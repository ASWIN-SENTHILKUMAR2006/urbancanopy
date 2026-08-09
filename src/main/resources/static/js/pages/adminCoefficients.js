/**
 * Environmental coefficient registry (FR-ADM-04 … FR-ADM-06).
 *
 * This is the most consequential screen in the application. Every value here feeds every
 * calculation, so a mistyped survival rate silently changes every estimate the system will
 * produce from that moment on. Three things follow from that, and they shape the design:
 *
 *   1. **The permitted range is shown beside the input, always.** The server rejects an
 *      out-of-range value, but a rule the user only learns about by violating it is a poor
 *      rule. The range is stated before the edit, not after.
 *   2. **What a change does — and does not — affect is stated on the page.** New estimates
 *      move; plans already completed keep their figures, because their snapshot froze the
 *      constants that produced them (FR-CALC-14). Without saying so, this screen looks far
 *      more destructive than it is.
 *   3. **A changed value is visibly marked and reversible.** Every row carries its seeded
 *      default and a one-click restore (FR-ADM-06).
 */

import { el, mount, $ } from '../core/dom.js';
import { api } from '../core/api.js';
import * as fmt from '../core/format.js';
import { toast } from '../core/toast.js';
import { pageHead } from '../components/shell.js';
import { loadInto } from '../components/states.js';
import { badge } from '../components/widgets.js';
import { startAdminPage, confirmAction } from '../components/admin.js';

const region = el('div');
let activeCategory = 'ALL';

async function init() {
    const permitted = await startAdminPage(
        '/admin/coefficients.html',
        'Environmental coefficients',
        'The constants behind every calculation.',
    );
    if (!permitted) return;

    mount('#content', [
        pageHead('Environmental coefficients', 'The constants behind every calculation.'),

        el('div.uc-disclaimer', { style: { marginBottom: 'var(--uc-s4)' } }, [
            el('span', { text: '⚠', 'aria-hidden': 'true' }),
            el('p', { style: { margin: '0' } }, [
                el('strong', { text: 'Changes take effect immediately, without a restart. ' }),
                'Every new estimate will use the revised value. Plans already marked completed keep '
                + 'their figures — their snapshot froze the constants that produced them.',
            ]),
        ]),

        region,
    ]);

    await load();
}

async function load() {
    await loadInto(
        region,
        () => api.get('/admin/coefficients', { quiet: true }),
        render,
        { rows: 6 },
    );
}

function render(coefficients) {
    const categories = [...new Set(coefficients.map((row) => row.category.value))];
    const visible = activeCategory === 'ALL'
        ? coefficients
        : coefficients.filter((row) => row.category.value === activeCategory);

    const changedCount = coefficients.filter((row) => row.modified).length;

    return [
        el('div.uc-row', { style: { marginBottom: 'var(--uc-s4)' } }, [
            tab('ALL', `All (${coefficients.length})`),
            ...categories.map((value) => {
                const label = coefficients.find((row) => row.category.value === value).category.label;
                return tab(value, label);
            }),
            el('span.uc-spacer'),
            changedCount
                ? badge(`${changedCount} changed from default`, 'uc-badge--warning')
                : badge('All at published defaults', 'uc-badge--success'),
        ]),

        el('div.uc-tablewrap', null, [
            el('table.uc-table', null, [
                el('thead', null, [
                    el('tr', null, [
                        el('th', { text: 'Coefficient' }),
                        el('th', { text: 'Value' }),
                        el('th', { text: 'Unit' }),
                        el('th', { text: 'Permitted range' }),
                        el('th', { text: '', 'aria-label': 'Actions' }),
                    ]),
                ]),
                el('tbody', null, visible.map(coefficientRow)),
            ]),
        ]),
    ];
}

function tab(value, label) {
    const isActive = activeCategory === value;
    return el('button.uc-btn.uc-btn--sm', {
        type: 'button',
        text: label,
        'aria-pressed': String(isActive),
        style: isActive
            ? { background: 'var(--uc-primary)', borderColor: 'var(--uc-primary)', color: 'var(--uc-on-primary)' }
            : null,
        onclick: () => {
            activeCategory = value;
            load();
        },
    });
}

function coefficientRow(coefficient) {
    const input = el('input.uc-input', {
        type: 'number',
        step: 'any',
        value: trimDecimal(coefficient.value),
        'aria-label': `Value for ${coefficient.label}`,
        style: { width: '9rem', textAlign: 'right' },
        id: `coef-${coefficient.id}`,
    });

    // Enter saves, so a keyboard user need not reach for the button.
    input.addEventListener('keydown', (event) => {
        if (event.key === 'Enter') {
            event.preventDefault();
            save(coefficient, input);
        }
    });

    return el('tr', null, [
        el('td', null, [
            el('div', { text: coefficient.label, style: { fontWeight: '600' } }),
            el('div.uc-small.uc-mono.uc-muted', { text: coefficient.coefficientKey }),
            coefficient.description ? el('div.uc-small.uc-muted', { text: coefficient.description }) : null,
            coefficient.source
                ? el('div.uc-small.uc-muted', { text: `Source: ${coefficient.source}`, style: { marginTop: '2px' } })
                : null,
        ]),
        el('td', null, [
            el('div.uc-row', { style: { flexWrap: 'nowrap' } }, [
                input,
                coefficient.modified ? badge('Changed', 'uc-badge--warning') : null,
            ]),
            coefficient.modified
                ? el('div.uc-small.uc-muted', { text: `Default ${trimDecimal(coefficient.defaultValue)}` })
                : null,
            coefficient.updatedBy
                ? el('div.uc-small.uc-muted', {
                    text: `Last changed ${fmt.relative(coefficient.updatedAt)} by ${coefficient.updatedBy}`,
                })
                : null,
        ]),
        el('td.uc-small', { text: coefficient.unit || '—' }),
        el('td.uc-small.uc-muted', { text: coefficient.permittedRange }),
        el('td', null, [
            el('div.uc-row', { style: { gap: 'var(--uc-s1)', flexWrap: 'nowrap' } }, [
                el('button.uc-btn.uc-btn--sm.uc-btn--primary', {
                    type: 'button',
                    text: 'Save',
                    onclick: () => save(coefficient, input),
                }),
                el('button.uc-btn.uc-btn--sm.uc-btn--ghost', {
                    type: 'button',
                    text: '⟲',
                    title: `Restore the published default (${trimDecimal(coefficient.defaultValue)})`,
                    'aria-label': `Restore default for ${coefficient.label}`,
                    disabled: !coefficient.modified,
                    onclick: () => restore(coefficient),
                }),
            ]),
        ]),
    ]);
}

async function save(coefficient, input) {
    const value = input.value.trim();
    if (value === '') {
        toast.warning('Enter a value.');
        input.focus();
        return;
    }

    // The server is the authority on the range; this check only saves a round trip and
    // gives the message next to the field instead of in a toast.
    const numeric = Number(value);
    const min = fmt.toNumber(coefficient.minValue);
    const max = fmt.toNumber(coefficient.maxValue);
    if ((!Number.isNaN(min) && numeric < min) || (!Number.isNaN(max) && numeric > max)) {
        toast.warning(`${coefficient.label} must be within ${coefficient.permittedRange}.`);
        input.focus();
        return;
    }

    const updated = await api.put(`/admin/coefficients/${coefficient.id}`, { value: numeric }).catch(() => null);
    if (updated) await load();
}

async function restore(coefficient) {
    const confirmed = confirmAction(
        `Restore "${coefficient.label}" to its published default of ${trimDecimal(coefficient.defaultValue)}?\n\n`
        + `It is currently ${trimDecimal(coefficient.value)}. New estimates will use the default from now on.`);
    if (!confirmed) return;

    const restored = await api.post(`/admin/coefficients/${coefficient.id}/restore-default`).catch(() => null);
    if (restored) await load();
}

/** 0.850000 → 0.85. The stored scale is a database concern, not something to read. */
function trimDecimal(value) {
    if (value === null || value === undefined || value === '') return '';
    const asNumber = Number(value);
    return Number.isNaN(asNumber) ? String(value) : String(asNumber);
}

init();
