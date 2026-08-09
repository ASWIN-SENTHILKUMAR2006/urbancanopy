/**
 * Transient notifications, announced to assistive technology (FR-SYS-04, NFR-07).
 *
 * The container is an `aria-live` region, so a screen-reader user hears "Plan completed"
 * at the same moment a sighted user sees it. Errors use `assertive` because they interrupt
 * what the user was doing; confirmations use `polite` because they do not.
 */

const AUTO_DISMISS_MS = { success: 4000, info: 4000, warning: 6000, error: 8000 };

let container = null;

function ensureContainer() {
    if (container && document.body.contains(container)) return container;

    container = document.createElement('div');
    container.className = 'uc-toasts';
    container.setAttribute('role', 'status');
    container.setAttribute('aria-live', 'polite');
    container.setAttribute('aria-atomic', 'false');
    document.body.appendChild(container);
    return container;
}

function show(kind, message) {
    if (!message) return;

    const host = ensureContainer();
    // An error must interrupt; a confirmation must not. The politeness is set per message
    // because the same region carries both.
    host.setAttribute('aria-live', kind === 'error' ? 'assertive' : 'polite');

    const toastEl = document.createElement('div');
    toastEl.className = `uc-toast uc-toast--${kind}`;

    const body = document.createElement('div');
    body.className = 'uc-toast-body';
    body.textContent = message;

    const close = document.createElement('button');
    close.type = 'button';
    close.className = 'uc-toast-close';
    close.setAttribute('aria-label', 'Dismiss notification');
    close.textContent = '×';
    close.addEventListener('click', () => toastEl.remove());

    toastEl.append(body, close);
    host.appendChild(toastEl);

    const timeout = AUTO_DISMISS_MS[kind] ?? 4000;
    setTimeout(() => toastEl.remove(), timeout);
}

export const toast = {
    success: (message) => show('success', message),
    error: (message) => show('error', message),
    warning: (message) => show('warning', message),
    info: (message) => show('info', message),
};
