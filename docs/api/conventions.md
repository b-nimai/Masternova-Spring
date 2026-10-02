# API conventions

**Last updated:** 2026-10-02 · **Status:** §1 implemented (2.2); the rest decided up front, carried over from the NestJS
Masternova and adapted to Spring. Each rule is *enforced in code* by the phase named in the
table, and that phase updates this file with the real class names.

All routes are under the **`/api/v1`** prefix. The version is in the path: it's visible in
logs, trivially routable by nginx or an ingress, and cacheable. A breaking change ships as
`/api/v2` beside v1.

| # | Rule | Enforced from |
|---|---|---|
| 1 | Error envelope = RFC 9457 Problem Details | ✅ Phase 2.2 |
| 2 | Cursor (keyset) pagination, no `total` | Phase 5 |
| 3 | Optimistic concurrency on content writes | Phase 6 |
| 4 | `Idempotency-Key` on unsafe, unversioned writes | Phase 2 |
| 5 | Money in minor units + currency | Phase 5 |
| 6 | Commands as request bodies (sealed union) | Phase 6 |
| 7 | 64-bit integers as strings | Phase 7 |
| 8 | Client-driven uploads return a plan, not a stream | Phase 7 |
| 9 | Long-running work streams over SSE | Phase 7 |
| 10 | Authorization denials carry a reason code | Phase 8 |
| 11 | Media is bought with a short-lived token | Phase 8 |
| 12 | Payment webhooks answer 200 for almost everything | Phase 9 |

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

## 3. Optimistic concurrency

Content writes take `expectedVersion` in the body, and responses carry the current `version`.
The JPA entity uses `@Version`. A mismatch (`ObjectOptimisticLockingFailureException`) is 409
with `expectedVersion` / `currentVersion` members. Lifecycle transitions take no version:
they re-read the aggregate and re-run their guards.

## 4. Idempotency

Unsafe writes with no version to guard them (duplicate course, undo, complete upload, checkout)
**require** an `Idempotency-Key` header. A repeat with the same key returns the stored
response. The same key with a different body is 422. A request with the key still in flight
is 409. Keys are scoped **per caller**, never global.

## 5. Money

Money is an integer in **minor units** plus a currency: `{ "priceMinor": 149900, "currency": "INR" }`.
In Java it's a `record Money(long minor, Currency currency)`, never `double`.
Formatting belongs to the client.

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
