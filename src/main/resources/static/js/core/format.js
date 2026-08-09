/**
 * The one place a figure becomes display text.
 *
 * Every `BigDecimal` arrives from the server as a JSON **string**, deliberately: a
 * lifetime-carbon figure such as 3571428.571428571 loses precision the instant JavaScript
 * parses it as a number, and the loss would then appear in a PDF that claims to be a
 * scientific estimate (architecture §9, JacksonConfig).
 *
 * So the rule this module exists to enforce is: parse a figure to a Number only at the
 * moment of formatting it for a human, or when a chart genuinely needs arithmetic — where
 * the loss is invisible. Never store the parsed value back, and never send it back.
 */

const LOCALE = undefined; // the browser's own locale

/** Parses a server decimal string. Returns NaN for null/blank, never 0 — see `isMissing`. */
export function toNumber(value) {
    if (value === null || value === undefined || value === '') return NaN;
    return typeof value === 'number' ? value : Number(value);
}

/**
 * True when a figure is genuinely absent rather than zero.
 *
 * The distinction is the whole reason the API sends `null` for a coverage percentage when
 * no land area was given: "not applicable" and "zero" are different answers and the
 * interface must not conflate them.
 */
export function isMissing(value) {
    return value === null || value === undefined || value === '' || Number.isNaN(toNumber(value));
}

/** A decimal figure with thousands separators. */
export function num(value, decimals = 2) {
    if (isMissing(value)) return '—';
    return toNumber(value).toLocaleString(LOCALE, {
        minimumFractionDigits: decimals,
        maximumFractionDigits: decimals,
    });
}

/** A whole number — counts of trees, species, plans. */
export function int(value) {
    if (isMissing(value)) return '—';
    return Math.round(toNumber(value)).toLocaleString(LOCALE);
}

/**
 * A figure with its unit. Never used for a bare number — principle 2 of the design system
 * is "never a bare number", and pairing them here makes that the path of least effort.
 */
export function unit(value, suffix, decimals = 2) {
    if (isMissing(value)) return '—';
    return `${num(value, decimals)} ${suffix}`;
}

export function percent(value, decimals = 1) {
    if (isMissing(value)) return '—';
    return `${num(value, decimals)} %`;
}

/**
 * Chooses a sensible scale so a KPI card never reads "0.00" or "12345678.00".
 *
 * Returns `{ value, unit }` so the caller can style the unit separately, which is what
 * lets the number stay the hero.
 */
export function scaleMass(kilograms, { base = 'kg' } = {}) {
    if (isMissing(kilograms)) return { value: '—', unit: base };

    const kg = toNumber(kilograms);
    const magnitude = Math.abs(kg);

    if (magnitude >= 1000) return { value: num(kg / 1000, 2), unit: base === 'kg' ? 't' : `k${base}` };
    if (magnitude > 0 && magnitude < 1) return { value: num(kg * 1000, 0), unit: base === 'kg' ? 'g' : `m${base}` };
    return { value: num(kg, 2), unit: base };
}

/** Area, switching to hectares once square metres stop being readable. */
export function area(squareMetres) {
    if (isMissing(squareMetres)) return '—';
    const sqm = toNumber(squareMetres);
    return sqm >= 100000 ? `${num(sqm / 10000, 2)} ha` : `${num(sqm, 0)} m²`;
}

/** A date, in the reader's locale. */
export function date(iso) {
    if (!iso) return '—';
    return new Date(iso).toLocaleDateString(LOCALE, { year: 'numeric', month: 'short', day: 'numeric' });
}

/** A date and time, for audit rows and snapshot timestamps. */
export function dateTime(iso) {
    if (!iso) return '—';
    return new Date(iso).toLocaleString(LOCALE, {
        year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit',
    });
}

/** Relative wording for "updated 3 days ago" in list rows. */
export function relative(iso) {
    if (!iso) return '—';
    const then = new Date(iso).getTime();
    const seconds = Math.round((then - Date.now()) / 1000);
    const units = [
        ['year', 31536000], ['month', 2592000], ['week', 604800],
        ['day', 86400], ['hour', 3600], ['minute', 60],
    ];
    const formatter = new Intl.RelativeTimeFormat(LOCALE, { numeric: 'auto' });
    for (const [unitName, size] of units) {
        if (Math.abs(seconds) >= size) return formatter.format(Math.round(seconds / size), unitName);
    }
    return formatter.format(Math.round(seconds), 'second');
}

/** The design-system token for a feasibility verdict's severity. */
export function severityClass(severity) {
    const map = {
        success: 'uc-badge--success',
        warning: 'uc-badge--warning',
        danger: 'uc-badge--danger',
        info: 'uc-badge--info',
        muted: 'uc-badge--muted',
    };
    return map[severity] || 'uc-badge--muted';
}

/** Plan status → badge modifier, so a status looks the same on every screen. */
export function statusClass(statusValue) {
    switch (statusValue) {
        case 'COMPLETED': return 'uc-badge--success';
        case 'ARCHIVED': return 'uc-badge--muted';
        default: return 'uc-badge--primary';
    }
}
