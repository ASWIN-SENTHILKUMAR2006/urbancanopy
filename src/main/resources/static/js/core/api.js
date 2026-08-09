/**
 * The single network seam.
 *
 * Every request the browser makes goes through this module. That is not a stylistic
 * preference — it is the client-side half of the deferred-authentication design
 * (architecture §6). When Phase 12 adds CSRF tokens and 401 → login handling, those are
 * changes to *this file*, not to nine page modules that each rolled their own fetch.
 *
 * Three other things are centralised here because doing them per call site guarantees
 * they will eventually be done inconsistently:
 *
 *   1. Unwrapping the `ApiResponse` envelope, so pages receive payloads.
 *   2. Turning the server's `ErrorResponse` into a thrown `ApiError` that carries the
 *      status, the user-facing message and any field errors.
 *   3. Surfacing the server's `message` as a toast, so the server decides what the user
 *      is told and the client never invents success wording.
 */

import { toast } from './toast.js';

const BASE = '/api/v1';

/**
 * A failed request, as the rest of the application sees it.
 *
 * Carries `fieldErrors` so a form can attach messages to the right inputs rather than
 * showing a page-level banner for a problem with one field.
 */
export class ApiError extends Error {
    constructor(status, body) {
        super(body?.message || 'The request could not be completed.');
        this.name = 'ApiError';
        this.status = status;
        this.errorCode = body?.errorCode || null;
        this.fieldErrors = body?.fieldErrors || [];
        this.reference = body?.reference || null;
    }

    /** True when the caller is not signed in — Phase 12 will redirect on this. */
    get isUnauthenticated() {
        return this.status === 401;
    }

    get isForbidden() {
        return this.status === 403;
    }

    /** True when the payload was rejected — a form should render field messages. */
    get isValidation() {
        return this.status === 400 || this.status === 422;
    }

    /** Field errors keyed by field name, for convenient form binding. */
    get fieldMap() {
        return Object.fromEntries(this.fieldErrors.map((e) => [e.field, e.message]));
    }
}

/**
 * CSRF (FR-AUTH-12).
 *
 * Spring Security issues the token in a cookie this script can read, and expects it echoed in
 * a header. That round trip is what a cross-site request cannot complete: another origin can
 * make the browser *send* our cookies, but the same-origin policy stops it from *reading*
 * them, so it cannot produce the matching header.
 *
 * Read fresh on each request rather than cached: the token is rotated on sign-in, and a stale
 * copy would make the first write after login fail for no visible reason.
 */
const CSRF_COOKIE = 'XSRF-TOKEN';
const CSRF_HEADER = 'X-XSRF-TOKEN';

function csrfToken() {
    const match = document.cookie.match(new RegExp(`(?:^|;\\s*)${CSRF_COOKIE}=([^;]*)`));
    return match ? decodeURIComponent(match[1]) : null;
}

/** Where to send someone who is not signed in, remembering where they were going. */
function redirectToLogin() {
    const here = window.location.pathname + window.location.search;
    const isAuthPage = window.location.pathname.startsWith('/login')
        || window.location.pathname.startsWith('/register');

    if (isAuthPage) return;
    window.location.href = `/login.html?next=${encodeURIComponent(here)}`;
}

/**
 * @param {boolean} quiet          suppress the success/error toast — for background loads
 *                                 whose outcome the page renders itself
 * @param {boolean} allowAnonymous do NOT redirect on 401. Only for probes where being
 *                                 signed out is an ordinary answer rather than a problem —
 *                                 the shell asking "who am I?" on a public page. Everything
 *                                 else gets sent to sign in, `quiet` or not: suppressing a
 *                                 toast and suppressing a redirect are different intentions
 *                                 and conflating them leaves a visitor staring at an error
 *                                 panel that a sign-in would have fixed.
 */
async function request(method, path, { body, query, quiet = false, allowAnonymous = false } = {}) {
    const url = new URL(BASE + path, window.location.origin);

    if (query) {
        for (const [key, value] of Object.entries(query)) {
            // An absent filter must not become `?growthRate=` — the server would then
            // receive an empty string where it expects nothing at all.
            if (value === undefined || value === null || value === '') continue;
            url.searchParams.set(key, value);
        }
    }

    const headers = { Accept: 'application/json' };
    if (body !== undefined) headers['Content-Type'] = 'application/json';

    // GET and HEAD change nothing, so they need no token; sending one anyway would just be
    // noise on the public catalogue requests an anonymous visitor makes.
    if (!['GET', 'HEAD'].includes(method)) {
        const token = csrfToken();
        if (token) headers[CSRF_HEADER] = token;
    }

    let response;
    try {
        response = await fetch(url, {
            method,
            headers,
            body: body === undefined ? undefined : JSON.stringify(body),
            credentials: 'same-origin',
        });
    } catch (networkError) {
        // fetch() rejects only when the request never completed — offline, DNS, CORS.
        const error = new ApiError(0, {
            message: 'The server could not be reached. Check that the application is running.',
        });
        if (!quiet) toast.error(error.message);
        throw error;
    }

    if (response.status === 204) return null;

    const payload = await readJson(response);

    if (!response.ok) {
        const error = new ApiError(response.status, payload);

        // A session that has expired, or was never established, is not an error the user can
        // act on — send them to sign in, remembering where they were.
        if (error.isUnauthenticated && !allowAnonymous) {
            redirectToLogin();
            throw error;
        }

        // Validation failures are rendered against the fields that caused them, so a
        // toast would duplicate what the form is already saying.
        if (!quiet && !error.isValidation) toast.error(error.message);
        throw error;
    }

    if (payload?.message && !quiet) toast.success(payload.message);
    return payload?.data ?? null;
}

async function readJson(response) {
    const text = await response.text();
    if (!text) return null;
    try {
        return JSON.parse(text);
    } catch {
        // A non-JSON body from an API endpoint means something upstream failed in a way
        // the server did not model. Say so rather than showing raw HTML to the user.
        return { message: 'The server returned an unexpected response.' };
    }
}

/** Uploads a file as multipart. Kept separate because it must NOT set Content-Type. */
async function upload(path, file, field = 'file') {
    const form = new FormData();
    form.append(field, file);

    const headers = { Accept: 'application/json' };
    const token = csrfToken();
    if (token) headers[CSRF_HEADER] = token;

    const response = await fetch(BASE + path, { method: 'POST', headers, body: form, credentials: 'same-origin' });
    const payload = await readJson(response);

    if (!response.ok) {
        const error = new ApiError(response.status, payload);
        toast.error(error.message);
        throw error;
    }
    if (payload?.message) toast.success(payload.message);
    return payload?.data ?? null;
}

/**
 * Signs in.
 *
 * Form-encoded, not JSON: the request is consumed by Spring Security's own authentication
 * filter, which reads request parameters. It never reaches a controller — establishing a
 * session and rotating its id against fixation is filter-level work.
 */
async function login(email, password) {
    const form = new URLSearchParams({ email, password });

    const headers = {
        Accept: 'application/json',
        'Content-Type': 'application/x-www-form-urlencoded',
    };
    const token = csrfToken();
    if (token) headers[CSRF_HEADER] = token;

    const response = await fetch(`${BASE}/auth/login`, {
        method: 'POST',
        headers,
        body: form.toString(),
        credentials: 'same-origin',
    });

    const payload = await readJson(response);
    if (!response.ok) {
        throw new ApiError(response.status, payload);
    }
    return payload?.data ?? null;
}

async function logout() {
    const headers = { Accept: 'application/json' };
    const token = csrfToken();
    if (token) headers[CSRF_HEADER] = token;

    await fetch(`${BASE}/auth/logout`, { method: 'POST', headers, credentials: 'same-origin' });
}

export const api = {
    get: (path, options) => request('GET', path, options),
    post: (path, body, options) => request('POST', path, { ...options, body }),
    put: (path, body, options) => request('PUT', path, { ...options, body }),
    patch: (path, body, options) => request('PATCH', path, { ...options, body }),
    del: (path, options) => request('DELETE', path, options),
    upload,
    login,
    logout,
};
