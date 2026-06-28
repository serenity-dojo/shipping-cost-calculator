# API Key Authentication

**As a platform administrator, I want the shipping API secured with API key authentication, so that only authorised clients can access the service.**

## Rules and Examples

### Rule: Must reject any request to a protected endpoint that lacks a valid API key, returning 401

Authentication is presented via the `X-API-Key` request header. A request is authenticated only when the header value exactly matches a configured key.

- **Example:** The one where a client POSTs to `/api/shipping/calculate` with no `X-API-Key` header → 401, JSON error body.
- **Counter-example:** The one where the header is present but matches no configured key → still 401 (the caller was never authenticated, so it is not a 403).

---

### Rule: Must grant access according to the role attached to the presented key

Every key is bound to exactly one role. **ADMIN inherits USER access** — an ADMIN key may call everything a USER may, plus admin-only endpoints.

| API Key | Role | Endpoint | Outcome |
|---|---|---|---|
| valid | USER | POST /api/shipping/calculate | 200 allowed |
| valid | ADMIN | POST /api/shipping/calculate | 200 allowed |
| valid | ADMIN | GET /api/admin/rates | 200 allowed |
| valid | USER | GET /api/admin/rates | 403 forbidden, JSON error body |

The USER-on-admin row is the boundary: an authenticated key with insufficient role gets **403 Forbidden**, distinct from the unauthenticated **401** above.

---

### Rule: Must keep the API documentation reachable without an API key

- **Example:** The one where `/swagger-ui.html` is opened with no header and the page loads normally.
- **Counter-example:** The one where `/api/admin/rates` is opened with no key → 401 (docs are open; business endpoints are not).

---

## Resolved decisions (for implementation)

- **Role hierarchy:** ADMIN inherits USER; ADMIN keys are accepted on USER endpoints.
- **Error responses:** 401 and 403 return a JSON error object (e.g. `{ "status": 401, "error": "Unauthorized", "message": "..." }`), consistent with the JSON API contract.
- **Key configuration:** Indexed list in `application.properties`, supporting multiple keys per role — e.g. `api.keys[0].key=...`, `api.keys[0].role=ADMIN`.
- **Out of scope:** Key rotation/expiry, rate limiting, per-client keys beyond role mapping, and the business logic of `GET /api/admin/rates` (this story secures it; its rate-listing behaviour is a separate feature).