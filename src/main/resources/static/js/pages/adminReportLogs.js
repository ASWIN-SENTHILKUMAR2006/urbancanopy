/**
 * Generated-report log (FR-RPT-06).
 *
 * Read-only, like the audit trail and for the same reason: entries are written as a side
 * effect of producing a document, and a screen that could edit one would defeat the point of
 * keeping the record.
 *
 * The log outlives its subjects — a plan can be soft-deleted after its report was generated —
 * so a row whose plan is gone still shows what was produced.
 */

import { el, mount } from '../core/dom.js';
import { api } from '../core/api.js';
import * as fmt from '../core/format.js';
import { pageHead } from '../components/shell.js';
import { loadInto } from '../components/states.js';
import { badge, pager } from '../components/widgets.js';
import { startAdminPage } from '../components/admin.js';

const state = { reportType: '', page: 0, size: 25 };
let reportTypes = [];
const region = el('div');

async function init() {
    const permitted = await startAdminPage(
        '/admin/report-logs.html', 'Report log', 'Documents generated from this system.');
    if (!permitted) return;

    reportTypes = await api.get('/admin/report-logs/types', { quiet: true }).catch(() => []);

    mount('#content', [
        pageHead('Report log',
            'Every PDF this system has produced: who generated it, when, and how large it was.'),
        buildFilters(),
        region,
    ]);

    await load();
}

function buildFilters() {
    return el('div.uc-filters', null, [
        el('div.uc-field', null, [
            el('label.uc-label', { for: 'f-type', text: 'Report type' }),
            el('select.uc-select', {
                id: 'f-type',
                onchange: (event) => {
                    state.reportType = event.target.value;
                    state.page = 0;
                    load();
                },
            }, [
                el('option', { value: '', text: 'All types' }),
                ...reportTypes.map((type) => el('option', { value: type.value, text: type.label })),
            ]),
        ]),
        el('div.uc-field', null, [
            el('label.uc-label', { text: ' ' }),
            el('button.uc-btn', { type: 'button', text: '↻ Refresh', onclick: () => load() }),
        ]),
    ]);
}

async function load() {
    await loadInto(
        region,
        () => api.get('/admin/report-logs', {
            quiet: true,
            query: {
                reportType: state.reportType,
                page: state.page,
                size: state.size,
                sort: 'generatedAt,desc',
            },
        }),
        render,
        {
            rows: 5,
            empty: {
                icon: '📄',
                title: 'No reports generated yet',
                body: 'Entries appear here when a planner downloads a plan or comparison report.',
            },
        },
    );
}

function render(page) {
    const totalBytes = page.content.reduce((sum, row) => sum + (row.fileSizeBytes || 0), 0);

    return [
        el('p.uc-small.uc-muted', {
            text: `${page.totalElements} documents · ${formatBytes(totalBytes)} on this page`,
        }),
        el('div.uc-tablewrap', null, [
            el('table.uc-table', null, [
                el('thead', null, [
                    el('tr', null, [
                        el('th', { text: 'Generated' }),
                        el('th', { text: 'Type' }),
                        el('th', { text: 'By' }),
                        el('th', { text: 'Subject' }),
                        el('th', { text: 'File' }),
                        el('th.uc-right', { text: 'Size' }),
                    ]),
                ]),
                el('tbody', null, page.content.map(logRow)),
            ]),
        ]),
        pager(page, (target) => {
            state.page = target;
            load();
        }),
    ];
}

function logRow(row) {
    return el('tr', null, [
        el('td.uc-small', null, [
            el('div', { text: fmt.dateTime(row.generatedAt) }),
            el('div.uc-muted', { text: fmt.relative(row.generatedAt) }),
        ]),
        el('td', null, [
            badge(row.reportType.label,
                row.reportType.value === 'PLAN_COMPARISON' ? 'uc-badge--info' : 'uc-badge--primary'),
        ]),
        el('td.uc-small', { text: row.userName || '(removed account)' }),
        el('td.uc-small', null, [
            // A plan report names its plan; a comparison names the set it covered, which is
            // the only surviving record of what was compared.
            row.planTitle
                ? el('a', { href: `/plan-results.html?id=${row.planId}`, text: row.planTitle })
                : el('span.uc-muted', { text: row.planIds ? `Plans ${row.planIds}` : '—' }),
        ]),
        el('td.uc-small.uc-mono.uc-truncate', { text: row.fileName, title: row.fileName }),
        el('td.uc-right.uc-small', { text: formatBytes(row.fileSizeBytes) }),
    ]);
}

function formatBytes(bytes) {
    if (bytes === null || bytes === undefined) return '—';
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${fmt.num(bytes / 1024, 1)} KB`;
    return `${fmt.num(bytes / (1024 * 1024), 2)} MB`;
}

init();
