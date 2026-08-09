/**
 * The application shell: topbar, sidebar, theme toggle and identity chip.
 *
 * Rendered by JavaScript rather than copied into eight HTML files, because navigation that
 * is duplicated per page is navigation that drifts per page — a new screen appears in six
 * menus and is missing from two. Each page supplies only its own content.
 */

import { el, mount } from '../core/dom.js';
import { api } from '../core/api.js';

const THEME_KEY = 'uc.theme';

const NAV = [
    { group: null, items: [
        { href: '/dashboard.html', icon: '⌂', label: 'Dashboard' },
        { href: '/plans.html', icon: '▤', label: 'My Plans' },
        { href: '/species.html', icon: '🌿', label: 'Species' },
        { href: '/compare.html', icon: '⇄', label: 'Compare' },
    ]},
    { group: 'Admin', adminOnly: true, items: [
        { href: '/admin/species.html', icon: '🌱', label: 'Species' },
        { href: '/admin/users.html', icon: '👥', label: 'Users' },
        { href: '/admin/coefficients.html', icon: '⚖', label: 'Coefficients' },
        { href: '/admin/report-logs.html', icon: '📄', label: 'Report log' },
        { href: '/admin/audit.html', icon: '🛡', label: 'Audit log' },
    ]},
    { group: null, items: [
        { href: '/profile.html', icon: '◍', label: 'Profile' },
    ]},
];

// ------------------------------------------------------------------------- theme

/**
 * Applies the stored theme.
 *
 * Called before first paint from each page's <head> so the user never sees a light flash
 * before a dark theme is applied. With no stored choice, nothing is stamped and the OS
 * preference decides — which is why tokens.css defines dark under both a media query and
 * an explicit attribute.
 */
export function applyStoredTheme() {
    const stored = localStorage.getItem(THEME_KEY);
    if (stored === 'dark' || stored === 'light') {
        document.documentElement.setAttribute('data-theme', stored);
    }
}

function currentTheme() {
    const stamped = document.documentElement.getAttribute('data-theme');
    if (stamped) return stamped;
    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

function toggleTheme(button) {
    const next = currentTheme() === 'dark' ? 'light' : 'dark';
    document.documentElement.setAttribute('data-theme', next);
    localStorage.setItem(THEME_KEY, next);
    paintThemeButton(button);

    // CSS follows the token swap by itself. A canvas does not: its colours were baked when it
    // was drawn, so anything on a canvas has to be told to redraw.
    document.dispatchEvent(new CustomEvent('uc:themechange', { detail: { theme: next } }));
}

function paintThemeButton(button) {
    const dark = currentTheme() === 'dark';
    button.textContent = dark ? '☀' : '☾';
    button.setAttribute('aria-label', dark ? 'Switch to light theme' : 'Switch to dark theme');
    button.title = button.getAttribute('aria-label');
}

// ------------------------------------------------------------------------- render

/**
 * Builds the shell around the page's own <main>.
 *
 * @param {string} activePath which nav item is current, e.g. `/plans.html`
 * @returns {Promise<object|null>} the signed-in user, or null when unidentified
 */
export async function renderShell(activePath) {
    const user = await loadUser();

    document.body.prepend(buildTopbar(user));
    buildSidebar(activePath, Boolean(user?.admin));

    return user;
}

async function loadUser() {
    try {
        // The one call that must NOT redirect on 401: this is the shell asking who the
        // visitor is, and "nobody" is a perfectly ordinary answer on a public page. Every
        // other 401 in the application does send the user to sign in.
        return await api.get('/auth/me', { quiet: true, allowAnonymous: true });
    } catch {
        return null;
    }
}

function buildTopbar(user) {
    const themeButton = el('button.uc-iconbtn', { type: 'button' });
    themeButton.addEventListener('click', () => toggleTheme(themeButton));
    paintThemeButton(themeButton);

    const menuButton = el('button.uc-iconbtn.uc-menu-toggle', {
        type: 'button',
        'aria-label': 'Toggle navigation',
        text: '☰',
        onclick: () => document.querySelector('.uc-sidebar')?.classList.toggle('is-open'),
    });

    return el('header.uc-topbar', null, [
        menuButton,
        el('a.uc-brand', { href: '/' }, [
            el('span.uc-brand-mark', { text: '🌳' }),
            el('span', { text: 'UrbanCanopy' }),
        ]),
        el('div.uc-topbar-spacer'),
        themeButton,
        user
            ? el('a.uc-userchip', { href: '/profile.html', title: user.email }, [
                el('span', { text: '◍' }),
                el('span', { text: user.fullName }),
            ])
            : el('a.uc-btn.uc-btn--sm.uc-btn--primary', { href: '/login.html', text: 'Sign in' }),
        user ? signOutButton() : null,
    ]);
}

/**
 * Sign out (FR-AUTH-06).
 *
 * A button issuing a POST, never a link. Logout is a state change, and one reachable by GET
 * can be triggered by any image tag on any page — an annoyance rather than a breach, but an
 * avoidable one. Spring Security rejects a GET logout while CSRF is enabled anyway.
 */
function signOutButton() {
    return el('button.uc-iconbtn', {
        type: 'button',
        text: '⎋',
        title: 'Sign out',
        'aria-label': 'Sign out',
        onclick: async () => {
            await api.logout();
            window.location.href = '/login.html';
        },
    });
}

function buildSidebar(activePath, isAdmin) {
    const main = document.querySelector('main');
    if (!main) return;

    const sidebar = el('nav.uc-sidebar', { 'aria-label': 'Primary' });

    for (const section of NAV) {
        if (section.adminOnly && !isAdmin) continue;
        if (section.group) sidebar.appendChild(el('div.uc-navgroup', { text: section.group }));

        for (const item of section.items) {
            sidebar.appendChild(el('a.uc-navlink', {
                href: item.href,
                'aria-current': item.href === activePath ? 'page' : null,
            }, [
                el('span.uc-navicon', { text: item.icon, 'aria-hidden': 'true' }),
                el('span', { text: item.label }),
            ]));
        }
    }

    const layout = el('div.uc-layout');
    main.replaceWith(layout);
    layout.append(sidebar, main);
    main.classList.add('uc-main');
}

/** Standard page heading with an optional action area. */
export function pageHead(title, subtitle, actions = []) {
    return el('div.uc-pagehead', null, [
        el('div.uc-pagehead-text', null, [
            el('h1', { text: title }),
            subtitle ? el('p.uc-pagesub', { text: subtitle }) : null,
        ]),
        actions.length ? el('div.uc-row', null, actions) : null,
    ]);
}

/** The mandatory estimation disclaimer (FR-CALC-08). */
export function disclaimer(text) {
    return el('div.uc-disclaimer', null, [
        el('span', { text: 'ⓘ', 'aria-hidden': 'true' }),
        el('p', { text: text, style: { margin: '0' } }),
    ]);
}

export { mount };
