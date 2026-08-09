/**
 * The four-state contract (design system §7).
 *
 * Every region that shows server data renders exactly one of: loading, empty, error,
 * content. Designing the unhappy three up front is what stops them being discovered during
 * testing as a blank panel or a spinner that never stops.
 */

import { el, mount } from '../core/dom.js';

/** Skeleton rows — shown in the region that is loading, never as a whole-page spinner. */
export function loadingState(rows = 3) {
    return el('div.uc-stack', { 'aria-busy': 'true', 'aria-live': 'polite' }, [
        el('span.uc-sr-only', { text: 'Loading…' }),
        ...Array.from({ length: rows }, (_, i) =>
            el('div.uc-skeleton', { style: { height: i === 0 ? '2rem' : '1rem' } })),
    ]);
}

/**
 * A designed empty state with a way forward.
 *
 * Never a blank panel: an empty region that offers no action reads as a broken one
 * (FR-DASH-09).
 */
export function emptyState({ icon = '🌱', title, body, action = null }) {
    return el('div.uc-state', null, [
        el('div.uc-state-icon', { text: icon, 'aria-hidden': 'true' }),
        el('h2.uc-state-title', { text: title }),
        body ? el('p.uc-state-body', { text: body }) : null,
        action,
    ]);
}

/**
 * A failure the user can act on.
 *
 * Carries the server's own message plus a retry, because "something went wrong" with no
 * route forward is the least useful thing an interface can say.
 */
export function errorState(error, onRetry) {
    const reference = error?.reference;
    return el('div.uc-state', { role: 'alert' }, [
        el('div.uc-state-icon', { text: '⚠', 'aria-hidden': 'true' }),
        el('h2.uc-state-title', { text: 'This could not be loaded' }),
        el('p.uc-state-body', { text: error?.message || 'An unexpected error occurred.' }),
        reference ? el('p.uc-small.uc-mono.uc-muted', { text: `Reference ${reference}` }) : null,
        onRetry ? el('button.uc-btn', { type: 'button', text: 'Try again', onclick: onRetry }) : null,
    ]);
}

/**
 * Runs an async load into a container, rendering all four states for the caller.
 *
 * Every list on every page goes through this, which is what makes the contract actually
 * hold rather than being a rule each page remembers separately.
 *
 * @param {Element|string} target  container to render into
 * @param {Function} load          async function returning the data
 * @param {Function} render        turns data into nodes
 * @param {object} options         `empty` describes the empty state; `rows` sizes the skeleton
 * @returns {Promise<*>} the loaded data, or `undefined` if the load failed. It does not
 *          reject: the failure has already been rendered where the user can see it, and a
 *          rethrow here would additionally require every call site to attach a `.catch`
 *          purely to keep the console clean.
 */
export async function loadInto(target, load, render, options = {}) {
    const retry = () => loadInto(target, load, render, options);

    mount(target, loadingState(options.rows ?? 3));

    try {
        const data = await load();

        if (options.isEmpty ? options.isEmpty(data) : isEmptyDefault(data)) {
            mount(target, emptyState(options.empty || {
                title: 'Nothing here yet',
                body: 'There is no data to show.',
            }));
            return data;
        }

        mount(target, render(data));
        return data;
    } catch (error) {
        mount(target, errorState(error, retry));
        return undefined;
    }
}

function isEmptyDefault(data) {
    if (!data) return true;
    if (Array.isArray(data)) return data.length === 0;
    if (Array.isArray(data.content)) return data.content.length === 0;
    return false;
}
