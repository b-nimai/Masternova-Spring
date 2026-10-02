# API conventions

**Last updated:** 2026-10-02 · **Status:** §1 (2.2), §2 + §5 (Phase 5), §4 (2.6) and §13 (Phase 3) implemented; the rest decided up front, carried over from the NestJS
Masternova and adapted to Spring. Each rule is *enforced in code* by the phase named in the
table, and that phase updates this file with the real class names.

All routes are under the **`/api/v1`** prefix. The version is in the path: it's visible in
logs, trivially routable by nginx or an ingress, and cacheable. A breaking change ships as
`/api/v2` beside v1.

| # | Rule | Enforced from |
|---|---|---|
| 1 | Error envelope = RFC 9457 Problem Details | ✅ Phase 2.2 |
| 2 | Cursor (keyset) pagination, no `total` | ✅ Phase 5 |
| 3 | Optimistic concurrency on content writes | Phase 6 |
| 4 | `Idempotency-Key` on unsafe, unversioned writes | ✅ Phase 2.6 |
| 5 | Money in minor units + currency | ✅ Phase 5 |
| 6 | Commands as request bodies (sealed union) | Phase 6 |
| 7 | 64-bit integers as strings | Phase 7 |
| 8 | Client-driven uploads return a plan, not a stream | Phase 7 |
| 9 | Long-running work streams over SSE | Phase 7 |
| 10 | Authorization denials carry a reason code | Phase 8 |
| 11 | Media is bought with a short-lived token | Phase 8 |
| 12 | Payment webhooks answer 200 for almost everything | Phase 9 |
| 13 | Authentication: deny by default, Bearer access token, refresh by cookie | ✅ Phase 3 |

## 1. Error envelope: RFC 9457 Problem Details

Every error is `application/problem+json`. Spring builds it natively (`ProblemDetail`), and
`GlobalExceptionHandler` (in the `platform` module) is the only place that shapes errors.
**Implemented in Phase 2.2.** Modules throw one of the sealed `DomainException` kinds from
`com.masternova.api.platform`; they never build responses themselves.

| Kind (throw this) | Status | `code` | Extension members |
|---|---|---|---|
| `NotFoundException(resource, id)` | 404 | `NOT_FOUND` | `resource` |
| `ValidationException` (or a failed `@Valid`) | 400 | `VALIDATION_FAILED` | `errors[]`: `{field, code, message}`, sorted |
| `ForbiddenException(reason, msg)` | 403 | `FORBIDDEN` | `reason` (e.g. `NO_ENTITLEMENT`) |
| `ConflictException` / `ConflictException.versionConflict(e, a)` | 409 | module-specific / `VERSION_CONFLICT` | e.g. `expectedVersion`, `currentVersion` |
| `RuleViolationException(code, msg)` | 422 | the rule's own (`COUPON_EXPIRED`) | optional |
| Spring MVC's own (malformed JSON, 405, 415, no route) | as Spring decides | the status name (`BAD_REQUEST`, `METHOD_NOT_ALLOWED`…) | — |
| anything else (a bug) | 500 | `INTERNAL` | none. Logged with the stack trace; the message is never sent. |

`type` is always `https://masternova.dev/problems/<code-in-kebab-case>`, and `instance` is the
request path. Proven by `GlobalExceptionHandlerTest`.

```jsonc
{
  "type": "https://masternova.dev/problems/version-conflict", // stable, machine-readable
  "title": "Conflict",
  "status": 409,
  "detail": "This course was changed elsewhere. Reload and try again.", // human sentence
  "instance": "/api/v1/instructor/courses/abc/curriculum",
  "code": "VERSION_CONFLICT",                       // extension members: what a client branches on
  "expectedVersion": 7,
  "currentVersion": 8
}
```

- `detail` is **copy** and will be reworded. A client must never parse it.
- Anything a client branches on is an **extension member with a stable code**, set with
  `problem.setProperty("code", …)`.
- Validation failures (`@Valid`) list each field: `"errors": [{ "field": "title", "code": "NotBlank" }]`.
- A 500 never leaks the exception message. It's logged with the trace id, and the client gets
  a generic sentence.

| Status | Means |
|---|---|
| 400 | Malformed request (bad JSON, failed bean validation) |
| 401 | Not signed in |
| 403 | Signed in, not permitted |
| 404 | Not found **or not visible to you**. Deliberately the same answer, so an id can't be probed. |
| 409 | The resource's *state* forbids this: version conflict, illegal transition |
| 422 | Well-formed and permitted, but the *content* isn't ready (publish gate) |

The 409/422 split exists because they send a client down different recovery paths: 409 means
"you are racing someone, so reload", 422 means "you are not finished yet, so fix it".

## 2. Cursor pagination

Every list returns `{ "items": [...], "nextCursor": "opaque" | null }` with **no `total`**.
Counting the whole matching set on every page is exactly the cost keyset pagination avoids.
The cursor is opaque (base64 of the sort key + id); a client that parses it breaks the day the
sort changes.

```
GET /api/v1/courses?sort=NEWEST&limit=20&cursor=<opaque>
```

**Implemented in Phase 5** ([ADR-0009](../adr/0009-keyset-pagination-over-offset.md),
`CourseCursor`, `CatalogApiIT`):

- `limit` is 1–50 (default 20). Every sort ends in the id, so pages never overlap.
- The cursor is base64url of `SORT|key|id`. It's bound to its sort: reusing it with another
  `sort`, or sending anything we didn't issue, is 400 `VALIDATION_FAILED` with
  `errors[0] = {"field": "cursor", "code": "INVALID_CURSOR"}`. The client restarts from page 1.
- The envelope is `CursorPage<T>`; it moves to `platform` when a second module pages a list.

## 3. Optimistic concurrency

Content writes take `expectedVersion` in the body, and responses carry the current `version`.
The JPA entity uses `@Version`. A mismatch (`ObjectOptimisticLockingFailureException`) is 409
with `expectedVersion` / `currentVersion` members. Lifecycle transitions take no version:
they re-read the aggregate and re-run their guards.

## 4. Idempotency

Unsafe writes with no version to guard them (duplicate course, undo, complete upload, checkout)
**require** an `Idempotency-Key` header: annotate the controller method with
`@IdempotencyKeyRequired`. **Implemented in Phase 2.6** (`IdempotencyFilter`, `IdempotencyIT`):

| Situation | Response |
|---|---|
| first request with a key | runs normally; the response is stored for 24 h (`masternova.idempotency.retention`) |
| retry, same key, same method + path + body | the stored response again, byte-for-byte, with its `Location` header (since 5.6, `V7`) and `Idempotent-Replayed: true` |
| same key, different body | 422 `IDEMPOTENCY_KEY_REUSED` |
| same key while the first request is still running | 409 `IDEMPOTENCY_IN_PROGRESS` |
| the first request ended in a 5xx | the key is released, so the retry really runs |
| `@IdempotencyKeyRequired` endpoint without the header | 400 `VALIDATION_FAILED` (`errors[0].field = "Idempotency-Key"`) |
| empty key or key longer than 200 chars | 400 `IDEMPOTENCY_KEY_INVALID` |

Any POST/PUT/PATCH/DELETE that *carries* the header is handled this way, annotated or not. A repeat with the same key returns the stored
response. The same key with a different body is 422. A request with the key still in flight
is 409. Keys are scoped **per caller**, never global.

## 5. Money

Money is an integer in **minor units** plus a currency: `{ "priceMinor": 149900, "currency": "INR" }`.
In Java it's the kernel's `record Money(long amountMinor, Currency currency)`, never `double`,
stored as an `@Embeddable` (two columns). Formatting belongs to the client. **Implemented in
Phase 5** (`CourseSummary`, `CourseDetailResponse`).

## 6. Commands as request bodies

Curriculum edits are `POST …/curriculum` with a discriminated union on `kind`. In Java that's
a `sealed interface CurriculumCommand` with record implementations, and Jackson polymorphism
uses `@JsonTypeInfo(property = "kind")`. Adding an edit type adds a record, not a route, and
each command is storable and invertible, which is what makes undo possible.

## 7. 64-bit integers as strings

Values that can exceed 2^53 (`sizeBytes`, `BIGINT` ids) are JSON strings, because JavaScript
numbers lose precision there. Money in minor units is the deliberate exception.

## 8. Uploads return a plan, not a stream

`POST /api/v1/media/uploads` returns part boundaries plus presigned URLs, at most 100 per
response. The bytes go browser → S3/MinIO directly and never touch the API.
`GET /api/v1/media/uploads/{id}` is both progress and resume: it asks the storage provider
what it actually holds.

## 9. Long-running work: SSE

Transcode progress is exposed two ways: a one-shot `GET` for clients that poll, and a
`GET …/stream` (`SseEmitter`, `text/event-stream`) that emits only on change and closes at a
terminal state. Always send `Cache-Control: no-cache` and `X-Accel-Buffering: no`, because
nginx and ALBs buffer by default.

## 10. Denials carry a reason

A 403 from the entitlement engine sets a `reason` member: `NO_ENTITLEMENT`,
`ENTITLEMENT_REVOKED`, `COURSE_NOT_PUBLISHED`, `ENTITLEMENT_EXPIRED`. The client shows a buy
button, a support link, or "no longer available" without parsing English.

## 11. Media tokens, not the session

`<video>` sends no `Authorization` header. `GET …/playback/lectures/{id}/grant` runs the
entitlement chain and returns a 5-minute HMAC token. The manifest endpoint takes only that
token.

## 12. Payment webhooks

`POST /api/v1/webhooks/payments` authenticates by **signature over the raw body**. Once the
signature verifies, it answers 200 for `processed`, `duplicate`, `ignored` and `deferred`
alike; all of them mean "stop retrying". A bad signature is 400: terminal for the provider,
and it would fail on retry anyway.

## 13. Authentication

**Deny by default.** Every route needs `Authorization: Bearer <access token>` unless a module
declares it public through a `PublicEndpoints` bean (platform API). Today the public routes are
`/api/v1/auth/**`, `POST /api/v1/notifications/unsubscribe/**` (§14), `/api/v1/meta/**` and
the health endpoints. An unknown route without a token
is 401, not 404, so the API reveals nothing about what exists.

| Endpoint | Body | Answer |
|---|---|---|
| `POST /auth/signup` | `{email, displayName, password}` | 201 `UserResponse`; 409 `EMAIL_TAKEN`; 400 field errors |
| `POST /auth/verify-email` | `{token}` | 204; 422 `VERIFICATION_TOKEN_INVALID` |
| `POST /auth/login` | `{email, password}` | 200 `TokenResponse` + `Set-Cookie: mn_refresh`; 401 `INVALID_CREDENTIALS` |
| `POST /auth/refresh` | none (the cookie) | 200 `TokenResponse` + a **rotated** cookie; 401 `SESSION_EXPIRED` / `SESSION_REVOKED` |
| `POST /auth/logout` | none (the cookie) | 204; clears the cookie |
| `GET /me` | — | 200 `UserResponse` |

- **Access token:** a 15-minute HS256 JWT with claims `sub` (user id), `roles`, `email_verified`
  and `sid`. Clients keep it **in memory**.
- **Refresh token:** only ever in the `mn_refresh` cookie: `HttpOnly`, `Secure` (outside dev),
  `SameSite=Strict`, `Path=/api/v1/auth`. It is single-use. Presenting a used one revokes the
  session ([ADR-0006](../adr/0006-rotating-refresh-tokens-over-stateless-jwt.md)), so clients
  must refresh **single-flight**.
- **Errors from the security chain** use the same Problem Details shape: 401 `UNAUTHENTICATED`
  (with `WWW-Authenticate: Bearer`), 403 `FORBIDDEN` with `reason: INSUFFICIENT_ROLE`.
- **Roles** live in the token, so a role change reaches the client at its next refresh.

Implemented by `identity.infrastructure.security.SecurityConfig`, `ProblemSecurityHandlers` and
`identity.web.AuthController`. Design: [`docs/lld/identity.md`](../lld/identity.md).

## 14. Notification consent

| Endpoint | Auth | Body | Answer |
|---|---|---|---|
| `GET /me/notification-preferences` | user | — | 200 `[{category, enabled, mandatory}]`, **every** category in a fixed order |
| `PUT /me/notification-preferences/{category}` | user | `{enabled}` (required, `false` ≠ missing) | 200 `{category, enabled, mandatory}`; 422 `CATEGORY_MANDATORY`; 400 for an unknown category |
| `POST /notifications/unsubscribe` | public | `{token}` | 200 `{category}`; 422 `UNSUBSCRIBE_TOKEN_INVALID` |
| `POST /notifications/unsubscribe/one-click?token=…` | public | form `List-Unsubscribe=One-Click` (RFC 8058) | 200 empty |

- **Categories** are the kernel enum `NotificationCategory`. `ACCOUNT_SECURITY` and `PURCHASE`
  are mandatory: always `enabled: true`, never changeable.
- **No row means subscribed.** Only changed categories are stored.
- **The token is the credential** for the public endpoints: an HMAC-signed `userId + category +
  expiry` (30 days), issued by the worker into each opt-out-able email. Forged, expired,
  malformed and mandatory-category tokens all get the same 422, so a forger learns nothing.
- **Unsubscribing is idempotent:** a second click answers 200 again.
- **Never a GET that changes state.** The email's link opens the web page `/unsubscribe?token=…`,
  which POSTs; mail scanners that prefetch links therefore can't unsubscribe anyone.

Implemented by `notification.web.NotificationPreferencesController` and `UnsubscribeController`.
Design: [`docs/lld/notification.md`](../lld/notification.md).
