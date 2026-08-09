/**
 * Tiny DOM construction helpers.
 *
 * The application builds its interface by creating elements and assigning `textContent`,
 * never by concatenating strings into `innerHTML`. This is an architectural decision
 * (architecture §12.4), not a style rule: a species description, a plan title and a site
 * name are all user-supplied, and string templating puts them one stray `<img onerror>`
 * away from executing. Constructing nodes closes that hole by construction — there is no
 * parser to trick.
 */

/**
 * Creates an element.
 *
 * @param {string} tag           tag name, optionally with `.class` suffixes: `div.uc-card`
 * @param {object|null} attrs    attributes; `class`, `text`, `html` (rejected), `dataset`,
 *                               `style` and `on*` handlers are treated specially
 * @param {Array|string} kids    child nodes or strings
 */
export function el(tag, attrs = null, kids = []) {
    const [name, ...classes] = String(tag).split('.');
    const node = document.createElement(name || 'div');

    if (classes.length) node.classList.add(...classes);

    if (attrs) {
        for (const [key, value] of Object.entries(attrs)) {
            if (value === undefined || value === null || value === false) continue;

            if (key === 'class') {
                node.classList.add(...String(value).split(/\s+/).filter(Boolean));
            } else if (key === 'text') {
                node.textContent = String(value);
            } else if (key === 'dataset') {
                Object.assign(node.dataset, value);
            } else if (key === 'style' && typeof value === 'object') {
                applyStyle(node, value);
            } else if (key.startsWith('on') && typeof value === 'function') {
                node.addEventListener(key.slice(2).toLowerCase(), value);
            } else if (value === true) {
                node.setAttribute(key, '');
            } else {
                node.setAttribute(key, String(value));
            }
        }
    }

    append(node, kids);
    return node;
}

/**
 * Applies a style object.
 *
 * Custom properties must go through `setProperty`: `style['--uc-metric'] = …` and
 * `Object.assign(style, …)` both fail *silently* on a CSSStyleDeclaration, because a
 * custom property is not a named member of it. That silence is the trap — the assignment
 * looks correct, throws nothing, and every metric quietly renders in the fallback colour.
 */
function applyStyle(node, styles) {
    for (const [property, value] of Object.entries(styles)) {
        if (value === undefined || value === null) continue;
        if (property.startsWith('--')) {
            node.style.setProperty(property, String(value));
        } else {
            node.style[property] = value;
        }
    }
}

/** Appends children, flattening arrays and ignoring null/undefined/false. */
export function append(parent, kids) {
    const list = Array.isArray(kids) ? kids : [kids];
    for (const kid of list) {
        if (kid === null || kid === undefined || kid === false) continue;
        if (Array.isArray(kid)) {
            append(parent, kid);
        } else if (kid instanceof Node) {
            parent.appendChild(kid);
        } else {
            parent.appendChild(document.createTextNode(String(kid)));
        }
    }
    return parent;
}

/** Replaces a container's contents in one operation. */
export function mount(target, kids) {
    const node = resolve(target);
    if (!node) return null;
    node.replaceChildren();
    append(node, kids);
    return node;
}

export function clear(target) {
    const node = resolve(target);
    if (node) node.replaceChildren();
    return node;
}

export function $(selector, scope = document) {
    return typeof selector === 'string' ? scope.querySelector(selector) : selector;
}

export function $$(selector, scope = document) {
    return Array.from(scope.querySelectorAll(selector));
}

function resolve(target) {
    return typeof target === 'string' ? document.querySelector(target) : target;
}

/**
 * Event delegation — one listener for a list rather than one per row.
 *
 * Rows are re-rendered on every change; per-row listeners would either leak or have to be
 * re-attached each time, and forgetting once produces a button that silently does nothing.
 */
export function delegate(root, eventName, selector, handler) {
    const node = resolve(root);
    if (!node) return;
    node.addEventListener(eventName, (event) => {
        const match = event.target.closest(selector);
        if (match && node.contains(match)) handler(event, match);
    });
}

/** Shows exactly one of a set of sibling regions — the four-state contract. */
export function showOnly(regions, key) {
    for (const [name, node] of Object.entries(regions)) {
        if (!node) continue;
        node.classList.toggle('uc-hidden', name !== key);
    }
}

/**
 * Debounce — used by the live estimate so a keystroke does not become a request.
 *
 * 300 ms is the figure the design system specifies; it is short enough to feel live and
 * long enough that typing "1200" is one call rather than four.
 */
export function debounce(fn, wait = 300) {
    let timer = null;
    const debounced = (...args) => {
        clearTimeout(timer);
        timer = setTimeout(() => fn(...args), wait);
    };
    debounced.cancel = () => clearTimeout(timer);
    debounced.flush = (...args) => {
        clearTimeout(timer);
        fn(...args);
    };
    return debounced;
}

/** Reads the query string as a plain object. */
export function queryParams() {
    return Object.fromEntries(new URLSearchParams(window.location.search));
}
