/**
 * Small shared pieces used by more than one screen: KPI cards, meters, bar rows,
 * verdict badges and the pager.
 *
 * Each keeps its metric colour as a CSS custom property rather than a class per metric, so
 * a metric is the same colour in a card, a bar and (in Phase 11) a PDF — one definition,
 * in tokens.css.
 */

import { el } from '../core/dom.js';
import * as fmt from '../core/format.js';

/**
 * A headline figure.
 *
 * `unit` is a separate element rather than part of the string: the number is the hero and
 * the unit is its context, and they are sized differently for that reason.
 */
export function kpi({ label, value, unit = '', note = null, token = null }) {
    return el('div.uc-kpi', { style: token ? { '--uc-metric': `var(${token})` } : null }, [
        el('div.uc-kpi-label', { text: label }),
        el('div.uc-kpi-value', null, [
            value,
            unit ? el('span.uc-kpi-unit', { text: unit }) : null,
        ]),
        note ? el('div.uc-kpi-note', { text: note }) : null,
    ]);
}

/** A 0–100 bar. Values above the maximum are clamped for width but not for the label. */
export function meter(percentValue, token = null) {
    const raw = fmt.toNumber(percentValue);
    const width = Number.isNaN(raw) ? 0 : Math.max(0, Math.min(100, raw));
    return el('div.uc-meter', {
        role: 'img',
        'aria-label': `${fmt.num(percentValue, 1)} percent`,
        style: token ? { '--uc-metric': `var(${token})` } : null,
    }, [
        el('div.uc-meter-fill', { style: { width: `${width}%` } }),
    ]);
}

/** One row of a horizontal bar comparison. */
export function barRow({ label, value, max, display, token }) {
    const raw = fmt.toNumber(value);
    const ceiling = fmt.toNumber(max);
    const width = !ceiling || Number.isNaN(raw) ? 0 : Math.max(0, Math.min(100, (raw / ceiling) * 100));

    return el('div.uc-bar-row', null, [
        el('span.uc-small', { text: label }),
        el('div.uc-bar-track', { style: token ? { '--uc-metric': `var(${token})` } : null }, [
            el('div.uc-bar-fill', { style: { width: `${width}%` } }),
        ]),
        el('span.uc-small.uc-right', { text: display }),
    ]);
}

/**
 * A verdict or status badge.
 *
 * Colour is always paired with text, and where a verdict carries weight an icon too —
 * colour alone is not information a colour-blind reader receives (design system §8).
 */
export function badge(text, modifier = 'uc-badge--muted', icon = null) {
    return el(`span.uc-badge.${modifier}`, null, [
        icon ? el('span', { text: icon, 'aria-hidden': 'true' }) : null,
        el('span', { text }),
    ]);
}

const VERDICT_ICON = {
    OPTIMAL: '●',
    UNDER_UTILISED: '○',
    TIGHT: '◐',
    OVERCROWDED: '⚠',
    NOT_APPLICABLE: '–',
};

/** Feasibility verdict, styled from the severity the server supplies. */
export function verdictBadge(verdict, severity, label) {
    return badge(label || verdict, `uc-badge--${severity || 'muted'}`, VERDICT_ICON[verdict] || null);
}

/** Plan status badge, consistent across list, editor and results. */
export function statusBadge(status) {
    if (!status) return badge('—');
    return badge(status.label, fmt.statusClass(status.value));
}

/**
 * Pagination controls.
 *
 * Shows which page of how many, because "‹ ›" alone leaves a user unable to tell whether
 * they are near the end of 3 pages or 300.
 */
export function pager(page, onChange) {
    if (!page || page.totalPages <= 1) return null;

    const button = (text, targetPage, disabled, label) =>
        el('button.uc-btn.uc-btn--sm', {
            type: 'button',
            text,
            disabled,
            'aria-label': label,
            onclick: () => onChange(targetPage),
        });

    return el('nav.uc-pager', { 'aria-label': 'Pagination' }, [
        button('‹', page.page - 1, page.first, 'Previous page'),
        el('span', { text: `Page ${page.page + 1} of ${page.totalPages} · ${page.totalElements} total` }),
        button('›', page.page + 1, page.last, 'Next page'),
    ]);
}

/** A definition row — label on the left, figure on the right. */
export function statRow(label, value) {
    return el('div.uc-row', null, [
        el('span.uc-muted.uc-small', { text: label }),
        el('span.uc-spacer'),
        el('span', { text: value, class: 'uc-num' }),
    ]);
}

/*
 * There is deliberately no per-pollutant colour map here.
 *
 * The five metric colours were validated in Phase 10 with the data-visualisation validator and
 * fail as a categorical set: PM2.5 against PM10 measures ΔE 9.4 for normal vision against a
 * floor of 15, and PM10, NO₂ and CO fall below the chroma floor — they read as grey. Every
 * place those five would have appeared together is a magnitude comparison across labelled
 * categories, where one hue is the correct encoding and the label carries identity. The tokens
 * remain in tokens.css as single accents; they are not a chart palette.
 */
