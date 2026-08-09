# Phase 14 — API Reference

**Project:** UrbanCanopy
**Base path:** `/api/v1`

Every endpoint the application exposes: 47 mapped by controllers, plus `login` and `logout`,
which are handled by Spring Security's own filters rather than by a controller.

---

## 1. Conventions

### 1.1 Response envelopes

Every JSON endpoint answers in one of three shapes, so the client has exactly one unwrapping
routine rather than a per-endpoint guess about where the payload lives.

**Success**

```json
{
  "success": true,
  "message": "Your plan has been saved.",
  "data": { },
  "timestamp": "2026-08-09T10:15:00Z"
}
```

`message` is the text the interface turns into a toast. The server decides what the user is
told; the client never invents success wording. It is `null` for plain reads.

**Page** — inside `data`, for any paginated endpoint:

```json
{
  "content": [],
  "page": 0,
  "size": 10,
  "totalElements": 42,
  "totalPages": 5,
  "first": true,
  "last": false
}
```

Spring's own `Page` is deliberately not serialised: its shape is large, unstable between
versions, and leaks internal structure.

**Error**

```json
{
  "timestamp": "2026-08-09T10:15:00Z",
  "status": 422,
  "error": "Unprocessable Entity",
  "errorCode": "BUSINESS_RULE_VIOLATION",
  "message": "A plan needs at least one species before it can be completed.",
  "path": "/api/v1/plantation-plans/5/status",
  "fieldErrors": [ { "field": "title", "message": "A plan title is required.", "rejectedValue": "" } ],
  "reference": "E95580EF"
}
```

`fieldErrors` is present only when individual fields were rejected. `reference` appears only on
unexpected failures — it correlates the user's screen with a log line, and the underlying
detail is never sent to the client.

### 1.2 Numbers

**Every `BigDecimal` is serialised as a JSON string.** JavaScript numbers are IEEE-754 doubles:
a figure such as `3571428.571428571` loses precision the moment it is parsed as a number, and
the loss then appears in a PDF that claims to be a scientific estimate. Clients format the
string for display and convert to a number only where arithmetic is genuinely needed, such as
chart scaling, and the loss is harmless.

Every numeric field either names its unit (`annualCo2Kg`, `landAreaSquareMetres`) or is paired
with a `unit` field.

A figure that does not apply is `null`, never `0`. Canopy coverage with no land area given is
"not applicable", which is a different statement from "zero".

### 1.3 Pagination and sorting

`?page=0&size=10&sort=commonName,asc`. Defaults and the maximum page size are configured under
`app.pagination.*`, not hardcoded per controller.

### 1.4 Authentication and CSRF

Session-based, established by `POST /auth/login`. Every state-changing request must carry the
CSRF token: the server issues it in the `XSRF-TOKEN` cookie and expects it echoed in the
`X-XSRF-TOKEN` header. `GET` and `HEAD` are exempt because they change nothing.

Failures are always JSON with a status code, never a redirect to a login page — a `fetch`
answered with a 302 produces a confusing success where the caller receives HTML instead of the
JSON it asked for.

| Status | `errorCode` | Meaning |
|---|---|---|
| 400 | `VALIDATION_FAILED` | Bean Validation rejected the payload; see `fieldErrors` |
| 400 | `MALFORMED_REQUEST` | The body could not be parsed |
| 401 | `UNAUTHENTICATED` | Not signed in |
| 401 | `AUTHENTICATION_FAILED` | Sign-in was refused |
| 403 | `ACCESS_DENIED` | Signed in, but not permitted |
| 403 | `CSRF_TOKEN_INVALID` | Token missing or stale — reload, do not ask for permissions |
| 404 | `RESOURCE_NOT_FOUND` | No such resource |
| 409 | `DUPLICATE_RESOURCE` | A uniqueness rule would be broken; `field` names the offender |
| 422 | `BUSINESS_RULE_VIOLATION` | Well-formed, but would break a domain rule |
| 500 | `INTERNAL_ERROR` | Unanticipated; quote `reference` |

The 400/422 split is deliberate: 400 means "fix the payload", 422 means "the payload was fine,
the intent was not".

### 1.5 Access levels

| Level | Meaning |
|---|---|
| **Public** | No sign-in required |
| **Auth** | Any signed-in user |
| **Owner** | The planner who created the resource. An administrator may **read** but never write |
| **Admin** | `ROLE_ADMIN` only |

---

## 2. Authentication

| Method | Path | Access | Purpose |
|---|---|---|---|
| `POST` | `/auth/register` | Public | Create a planner account. Always `ROLE_USER` — the payload has no role field |
| `POST` | `/auth/login` | Public | Sign in. **Form-encoded** (`email`, `password`), not JSON — consumed by the authentication filter |
| `POST` | `/auth/logout` | Auth | Sign out, invalidating the session. POST only |
| `GET` | `/auth/me` | Auth | The current account. 401 when signed out |
| `POST` | `/auth/password` | Auth | Change own password; requires the current one |

`login` and `logout` are not controller methods. A controller cannot establish a session,
rotate its id against fixation, or clear the security context — writing one that appeared to
would be an imitation of authentication rather than the thing itself.

**Registration does not sign you in.** Sending the new user to the sign-in page proves the
credentials they just chose actually work.

---

## 3. Species catalogue

| Method | Path | Access | Purpose |
|---|---|---|---|
| `GET` | `/tree-species` | Public | Paginated, searchable, filterable catalogue |
| `GET` | `/tree-species/{id}` | Public | Full detail, including how many live plans depend on it |
| `GET` | `/tree-species/filters` | Public | Every value each filter can take, from the enums |
| `GET` | `/tree-species/options` | Auth | Lightweight list for the plan editor's picker — active species only |

**Filters:** `search`, `growthRate`, `waterRequirement`, `climateZone`, `soilType`,
`nativeOnly`, `activeOnly` (defaults to `true`; the public catalogue must not offer a withdrawn
species).

Filter values are served from the enums rather than duplicated in JavaScript, so adding a
climate zone updates the interface automatically instead of showing a raw constant name until a
second file is remembered.

---

## 4. Plantation plans

| Method | Path | Access | Purpose |
|---|---|---|---|
| `GET` | `/plantation-plans` | Auth | The caller's own plans, paginated |
| `GET` | `/plantation-plans/options` | Auth | Enum options for the editor's selects |
| `POST` | `/plantation-plans` | Auth | Create as a draft |
| `GET` | `/plantation-plans/{id}` | Owner | Plan and composition |
| `PUT` | `/plantation-plans/{id}` | Owner | Update the header |
| `DELETE` | `/plantation-plans/{id}` | Owner | Soft delete |
| `POST` | `/plantation-plans/{id}/duplicate` | Owner | Clone as a new draft |
| `PATCH` | `/plantation-plans/{id}/status` | Owner | Lifecycle transition |
| `POST` | `/plantation-plans/{id}/items` | Owner | Add a species, or increase it if present |
| `PUT` | `/plantation-plans/{id}/items/{itemId}` | Owner | Set a quantity absolutely |
| `DELETE` | `/plantation-plans/{id}/items/{itemId}` | Owner | Remove a species |
| `GET` | `/plantation-plans/{id}/estimate` | Owner | The full calculation result |

**Items are sub-resources**, because a plan item has no independent existence and its ownership
is the plan's ownership. A top-level `/plan-items/{id}` would make that a lookup rather than a
fact of the URL.

**Every mutating item endpoint returns the whole plan.** The totals, the species count and the
`calculable` flag all move when one quantity changes; returning the item alone would leave the
client to recompute them or fetch again.

**Adding a species already present increments it** rather than creating a duplicate (BR-08). A
planner who adds 50 Neem to a plan holding 100 means 150 — not an error, and not two rows a
report would later have to reconcile.

**Status transitions** are declared on the `PlanStatus` enum: `DRAFT ⇄ COMPLETED → ARCHIVED`,
and archived returns only to completed. Completing requires at least one species and a positive
land area, and writes a **result snapshot** freezing the coefficients in force (FR-CALC-14).
Reopening as a draft discards it.

---

## 5. Estimates and comparison

| Method | Path | Access | Purpose |
|---|---|---|---|
| `POST` | `/estimates/preview` | Auth | Evaluate a transient composition without saving anything |
| `GET` | `/comparisons?planIds=5,6,7` | Owner | Compare 2–4 plans |

`POST /estimates/preview` is why the live editor works. It accepts
`{landAreaInput, landAreaUnit, projectionYears, targetStartYear, items[]}` and returns a full
result, persisting nothing — the calculation engine's purity made visible at the API surface.
`POST` rather than `GET` because the composition is a structured body, and a URL long enough to
hold fifty species and quantities is a URL something will truncate.

**Which coefficients apply, and why it differs by endpoint:**

| Endpoint | Basis | Reason |
|---|---|---|
| `/plantation-plans/{id}/estimate` | The plan's frozen snapshot, if it has one | A completed plan must keep reporting what its report said (FR-CALC-14) |
| `/comparisons` | The live registry, for every plan | A comparison is only meaningful if every column was computed the same way |
| `/dashboard/*` | The live registry | A total summed from figures computed under different constants is not a total of anything |

The comparison returns **metrics × plans**, not plans each carrying their own metrics — the
shape the table and the charts both need. Normalised per-tree and per-100 m² rows are included
because a 2,400-tree plan beats a 480-tree plan on every total regardless of which is the better
design.

---

## 6. Dashboard

| Method | Path | Access | Purpose |
|---|---|---|---|
| `GET` | `/dashboard/summary` | Auth | KPI figures, recent plans, and — for an administrator — system-wide totals |
| `GET` | `/dashboard/charts/{chartKey}` | Auth | One chart's data |

**Chart keys:** `co2-by-species`, `pollutant-breakdown`, `co2-projection`, `plans-by-purpose`.
An unknown key is a 404 rather than an empty chart, so a client typo reads as the mistake it is
instead of as "you have no data".

Charts are separate requests from the summary: the KPI row depends on nothing else and should
not wait behind four aggregations, and a chart that fails renders its own error state without
taking the headline figures down with it.

Every chart carries a single `colourToken`. These are magnitude comparisons across labelled
categories, where the label carries identity and one hue is the correct encoding — see
`docs/04` §2.3 for the measured reason the per-metric palette is not used as a chart palette.

---

## 7. Reports

| Method | Path | Access | Purpose |
|---|---|---|---|
| `GET` | `/reports/plans/{planId}/pdf` | Owner | Full impact report |
| `GET` | `/reports/comparison/pdf?planIds=5,6` | Owner | Comparison report |

The only endpoints that do not return the JSON envelope — the body is a PDF. Responses carry
`Content-Type: application/pdf`, `Content-Length`, `Cache-Control: no-store`, and a
`Content-Disposition` filename slugified to ASCII (a plan title is user input heading for a
header and a filesystem).

Every generation is recorded in the report log after the bytes exist, so a failed render leaves
no entry claiming a document that was never produced.

---

## 8. Profile

| Method | Path | Access | Purpose |
|---|---|---|---|
| `GET` | `/profile` | Auth | The caller's own profile |
| `PUT` | `/profile` | Auth | Update name, organisation, designation |

No id in the path. An id would imply some other value could be supplied, and the endpoint would
then have to prove it refuses one; without it, editing another user's profile is not a request
this API can express. Email and role are absent from the payload, so it cannot be used to
self-promote.

---

## 9. Administration

All require `ROLE_ADMIN`, enforced by one rule over `/api/v1/admin/**` rather than an annotation
per method — a rule that cannot be forgotten on a new endpoint.

### 9.1 Species curation

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/admin/tree-species` | Catalogue including deactivated species |
| `POST` | `/admin/tree-species` | Create |
| `PUT` | `/admin/tree-species/{id}` | Update |
| `PATCH` | `/admin/tree-species/{id}/status` | Activate or deactivate |
| `DELETE` | `/admin/tree-species/{id}` | Delete — only if no plan uses it |
| `POST` | `/admin/tree-species/{id}/image` | Upload a photograph (multipart) |

**BR-18:** a species any plan depends on is deactivated, never deleted. Deleting would break
those plans or silently change their figures; deletion returns 422 naming how many plans depend
on it and offering deactivation.

Updating a species **changes every plan that uses it** — intended, since correcting a
sequestration rate should correct every estimate derived from it. Plans that must not move are
pinned by their snapshot instead.

### 9.2 Users

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/admin/users` | Search accounts |
| `GET` | `/admin/users/roles` | Role options |
| `GET` | `/admin/users/{id}` | One account |
| `PATCH` | `/admin/users/{id}/status` | Enable or disable |
| `PATCH` | `/admin/users/{id}/role` | Change role |

**There is no delete.** Accounts own plans and appear as the actor on audit entries; destroying
one would orphan that history or cascade it away, and an audit trail erasable by removing its
subject is not evidence.

Two refusals: an administrator cannot disable or demote **their own** account (FR-ADM-03), and
the last enabled administrator cannot be removed by either route (BR-22).

### 9.3 Coefficients

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/admin/coefficients` | The whole registry |
| `GET` | `/admin/coefficients/{id}` | One coefficient |
| `PUT` | `/admin/coefficients/{id}` | Revise its value |
| `POST` | `/admin/coefficients/{id}/restore-default` | Restore the seeded default |

No create and no delete: the registry's membership is defined by migration and verified at
start-up — a missing key stops the application from starting — so allowing rows to be added or
removed over HTTP would only create a way to break that check.

Values are validated against each coefficient's own declared range, and the four scoring weights
must still sum to 1.0 (BR-20). Changes take effect immediately, without a restart.

### 9.4 Logs and oversight

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/admin/audit-events` | The audit trail |
| `GET` | `/admin/audit-events/types` | Event-type options |
| `GET` | `/admin/report-logs` | Generated documents |
| `GET` | `/admin/report-logs/types` | Report-type options |
| `GET` | `/admin/plantation-plans` | Every planner's plans, read-only |
| `GET` | `/admin/plantation-plans/{id}` | One plan, read-only |

Both logs are read-only by construction. Entries appear as a side effect of the operations they
describe; an endpoint that could add, edit or remove one would make the tables unusable as
evidence.

Administrator access to a plan is **read-only and recorded**. Each detail read leaves an
`ADMIN_VIEWED_PLAN` entry — the record is what makes support access accountable rather than
merely available.

---

**End of Phase 14 API reference.** Implementation and requirement traceability follow in
[`06-implementation-and-traceability.md`](06-implementation-and-traceability.md).
