/**
 * Landing page.
 *
 * Static apart from one thing: the species count in the call to action is read from the
 * catalogue rather than written into the markup, so it cannot quietly become wrong the day
 * an administrator adds a species.
 */

import { api } from '../core/api.js';
import { applyStoredTheme } from '../components/shell.js';

applyStoredTheme();

(async () => {
    const page = await api.get('/tree-species', { quiet: true, query: { size: 1 } }).catch(() => null);
    if (!page) return;

    const link = document.querySelector('a[href="/species.html"].uc-btn:not(.uc-btn--primary)');
    if (link && page.totalElements) {
        link.textContent = `Browse ${page.totalElements} species`;
    }
})();
