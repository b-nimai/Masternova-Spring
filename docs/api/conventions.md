# API conventions

**Last updated:** 2026-10-23 · **Status:** §1 (2.2), §2 + §5 (Phase 5), §3 + §6 (Phase 6), §4 (2.6), §7 + §8 (7.3), §9 (7.7), §12 (9.6), §13 (Phase 3), §15 (Phase 10) and §16 + §17 (Phase 11) implemented; the rest decided up front, carried over from the NestJS
Masternova and adapted to Spring. Each rule is *enforced in code* by the phase named in the
table, and that phase updates this file with the real class names.

All routes are under the **`/api/v1`** prefix. The version is in the path: it's visible in
logs, trivially routable by nginx or an ingress, and cacheable. A breaking change ships as
`/api/v2` beside v1.

| # | Rule | Enforced from |
|---|---|---|
| 1 | Error envelope = RFC 9457 Problem Details | ✅ Phase 2.2 |
| 2 | Cursor (keyset) pagination, no `total` | ✅ Phase 5 |
| 3 | Optimistic concurrency on content writes | ✅ Phase 6 |
| 4 | `Idempotency-Key` on unsafe, unversioned writes | ✅ Phase 2.6 |
| 5 | Money in minor units + currency | ✅ Phase 5 |
| 6 | Commands as request bodies (sealed union) | ✅ Phase 6 |
| 7 | 64-bit integers as strings | ✅ Phase 7.3 (byte sizes are bounded, so numbers) |
| 8 | Client-driven uploads return a plan, not a stream | ✅ Phase 7.3 |
| 9 | Long-running work streams over SSE | ✅ Phase 7.7 |
| 10 | Authorization denials carry a reason code | Phase 8 |
| 11 | Media is played with a ticket, not the session | ✅ ticket 7.9 · entitlement check Phase 8 |
| 12 | Payment webhooks answer 200 for almost everything | ✅ Phase 9.6 |
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
| overload: the pod's bulkhead is full, or no database connection within 2 s (D5.1) | 503 + `Retry-After: 1` | `OVERLOADED` | none. Safe to retry after the delay; [capacity](../hld/03-capacity.md) |
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
- The envelope is `CursorPage<T>`, in `platform` since Phase 11 (engagement's reviews and
  questions page with a newest-first `(created_at, id)` cursor, `limit` 1–50).

## 3. Optimistic concurrency

Content writes take `expectedVersion` in the body, and responses carry the current `version`.
The JPA entity uses `@Version`. A mismatch (`ObjectOptimisticLockingFailureException`) is 409
with `expectedVersion` / `currentVersion` members. Lifecycle transitions take no version:
they re-read the aggregate and re-run their guards.

**Implemented in Phase 6** ([ADR-0010](../adr/0010-optimistic-concurrency-with-version.md)):
`CourseAccess.forEditing` pre-checks `expectedVersion` (409 with both versions); `@Version` catches
two requests that read the same version at once (409 without versions). The course's version
covers the whole aggregate: every mutation through `Course` touches the root. Proven by
`CourseAuthoringIT` (10 concurrent saves of one version → exactly 1 winner).

## 4. Idempotency

Unsafe writes with no version to guard them (create course, duplicate course, complete upload, checkout)
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

Undo/redo are **versioned** instead (they carry `expectedVersion`, Phase 6.5): a double-tapped
undo undoes once and the second press gets 409 `VERSION_CONFLICT`.

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

**Implemented in Phase 6** (`CurriculumCommand`, `POST /api/v1/instructor/courses/{id}/curriculum`):
`{"expectedVersion": 7, "command": {"kind": "ADD_LECTURE", "sectionId": "…", "title": "…",
"lectureKind": "ARTICLE"}}`. Kinds: `ADD_SECTION`, `RENAME_SECTION`, `REORDER_SECTIONS` (the whole
order), `REMOVE_SECTION`, `ADD_LECTURE`, `UPDATE_LECTURE`, `MOVE_LECTURE`, `REMOVE_LECTURE`.
`RESTORE_*` are server-only (400 `NOT_A_CLIENT_COMMAND`). Omitted ids are generated. A field is
never named `kind` (that's the discriminator).

## 7. 64-bit integers as strings

Values that can exceed 2^53 (unbounded `BIGINT` ids and counters) are JSON strings, because
JavaScript numbers lose precision there. Money in minor units is the deliberate exception.

**Decided in 7.3:** byte sizes (`sizeBytes`, `partSizeBytes`, `uploadedBytes`) stay **numbers**.
They are capped at 10 GiB (`PartPlan.MAX_SIZE`, < 2^34), far below 2^53, so a string would only
make every client parse it. The rule is about values without such a bound.

## 8. Uploads return a plan, not a stream

`POST /api/v1/media/uploads` (`Idempotency-Key`) returns the plan (`partSizeBytes`,
`partCount`) plus a presigned URL per part, at most 100. The bytes go browser → S3 (SeaweedFS in
dev) directly and never touch the API. `GET /api/v1/media/uploads/{id}` is both progress and
resume: it asks the storage provider what it actually holds and returns fresh URLs for the parts
still missing. `POST …/{id}/complete` has **no body**: the server builds the part list from the
provider (ADR-0014) and is safe to retry. `DELETE …/{id}` aborts (204, idempotent).

| Code | Status | When |
|---|---|---|
| `UPLOAD_TOO_LARGE` | 422 | `sizeBytes` > 10 GiB (`maxBytes` member) |
| `UNSUPPORTED_MEDIA_TYPE` | 422 | `contentType` not allowed (`allowed` member) |
| `UPLOAD_INCOMPLETE` | 422 | complete while parts are missing or the wrong size (`missing`, `wrongSize`, `unexpected`) |
| `UPLOAD_ALREADY_COMPLETED` · `UPLOAD_ABORTED` · `UPLOAD_EXPIRED` | 409 | the session's state forbids the call (`status` member) |
| `STORAGE_UNAVAILABLE` | 503 + `Retry-After` | the provider is down or throttling |

Implemented by `UploadController`, `UploadService`, `UploadState` (media LLD §3).

## 9. Long-running work: SSE

Transcode progress is exposed two ways: a one-shot `GET /api/v1/media/assets/{id}` for clients
that poll, and `GET …/{id}/stream` (`SseEmitter`, `text/event-stream`). The stream sends a
`snapshot` event first, then `progress` events (lossy ticks, ≤ 1/s per job) and a fresh `snapshot`
whenever a job finishes, and the server closes it at `READY` or `FAILED`. Always send
`Cache-Control: no-cache` and `X-Accel-Buffering: no`, because nginx and ALBs buffer by default.
A `: keep-alive` comment every 20 s stops proxies from closing an idle stream. The server ends a
stream after 30 minutes; the SPA's `MediaApi.follow` reopens a stream that ended (or dropped) before
`READY`/`FAILED`, and each new stream starts with a fresh snapshot.

The stream is authenticated with the normal **bearer token**, so the SPA reads it with
`HttpClient` (`observe: 'events'`, `reportProgress`, fetch backend), not `EventSource`, which can't
send an `Authorization` header. Implemented by `AssetController`, `AssetEvents` (Redis
`media:asset:*` → this replica's open streams) and `AssetProgressService` (access checked once,
when the stream opens).

## 10. Denials carry a reason

A 403 from the entitlement engine sets a `reason` member: `NO_ENTITLEMENT`,
`ENTITLEMENT_REVOKED`, `COURSE_NOT_PUBLISHED`, `ENTITLEMENT_EXPIRED` (and `ACCESS_CHECK_FAILED`
when a policy broke: the engine fails shut). The client shows a buy button, a support link, or
"no longer available" without parsing English.

**How it gets there (8.4):** a method guarded by `@PreAuthorize("@entitlements.canWatch(authentication, #lectureId)")`
gets an `AuthorizationResult` that implements platform's `ReasonedDenial`; Spring Security puts
it in the `AuthorizationDeniedException`, and `GlobalExceptionHandler` copies its reason into the
problem. A plain role check still answers `INSUFFICIENT_ROLE`.

**Asking instead of being refused:** `GET /api/v1/courses/{id}/access` (the course page's locks)
and `GET /api/v1/lectures/{id}/access` (the player's guard) are public and answer the same
decision as a discriminated union, `{"kind": "ALLOWED", "grant": "PREVIEW_LECTURE"}` or
`{"kind": "DENIED", "reason": "NO_ENTITLEMENT"}`, with 200.

## 11. Media tokens, not the session

`<video>` and native Safari HLS send no `Authorization` header. `POST
/api/v1/media/assets/{id}/playback` (bearer) runs the access check once and returns `{masterUrl,
expiresAt}`; `masterUrl` is `…/hls/master.m3u8?t=<ticket>`. The two playlist routes (`GET
…/hls/master.m3u8`, `…/hls/{rung}/index.m3u8`) are public and accept **only** that ticket
(`PlaybackTickets`: HMAC-SHA256 over asset + user + expiry, constant-time check, bound to one
asset; anything else → 403 `PLAYBACK_TICKET_INVALID`). The rung playlist's segments are
presigned storage urls, so the bytes never pass through the api.

**Decided in 7.9:** the ticket lives **6 h**, not the 5 minutes first planned. A VOD player
fetches a *different rung's* playlist whenever ABR switches, which can be an hour into a lecture.
A 5-minute ticket would break the switch, not the attacker. This route is the **owner's
preview** (owner or admin).

**Learners (8.5, [ADR-0017](../adr/0017-short-ip-bound-playback-token.md)):** `POST
/api/v1/lectures/{id}/playback` (bearer, then `@PreAuthorize` runs the entitlement decision)
returns `{manifestUrl, expiresAt}`; `manifestUrl` is `/api/v1/lectures/{id}/hls/master.m3u8?token=…`.
The token lives **5 minutes** and is **bound to the client IP**, which is in its HMAC but not in
the token. The playlist routes (`…/hls/master.m3u8`, `…/hls/{rung}/index.m3u8`) are public, accept
only that token from that IP (else 403 `PLAYBACK_TOKEN_INVALID`), and touch no database. Segments
are presigned for 30 minutes. What makes 5 minutes work where 7.9's didn't: on a 403 the player
**re-tickets** (one new POST, which re-runs the decision) and resumes at the same position. A
lecture without a video is 409 `LECTURE_HAS_NO_VIDEO`; one still processing, 409 `MEDIA_NOT_READY`.

## 12. Payment webhooks

`POST /api/v1/webhooks/payments` authenticates by **signature over the raw body**. Once the
signature verifies, it answers 200 for `processed`, `duplicate`, `ignored` and `deferred`
alike; all of them mean "stop retrying". A bad signature is 400: terminal for the provider,
and it would fail on retry anyway.

**Implemented in Phase 9.6** (`WebhookController` → `WebhookService`, ADR-0018):

| Delivery | Response |
|---|---|
| signature verifies, the event moved an order | 200 `{"outcome": "PROCESSED"}` |
| an event id already processed (Razorpay's `X-Razorpay-Event-Id`) | 200 `DUPLICATE`, nothing done |
| a type commerce doesn't act on, or a message the order's state ignores (late, duplicate, wrong amount) | 200 `IGNORED` |
| about an order that isn't ours | 200 `UNKNOWN_ORDER` |
| signature missing or wrong | 400 `WEBHOOK_SIGNATURE_INVALID`, nothing claimed |
| processing failed | 500; the claim rolled back with the effects, so the redelivery runs |

The controller takes `@RequestBody byte[]`: the HMAC is over the bytes as they arrived, never a
re-serialised DTO. There is no `deferred` yet: nothing is processed later than the request.

## 13. Authentication

**Deny by default.** Every route needs `Authorization: Bearer <access token>` unless a module
declares it public through a `PublicEndpoints` bean (platform API). Today the public routes are
`/api/v1/auth/**`, `POST /api/v1/notifications/unsubscribe/**` (§14), `/api/v1/meta/**` and
the health endpoints. An unknown route without a token
is 401, not 404, so the API reveals nothing about what exists.

| Endpoint | Body | Answer |
|---|---|---|
| `POST /auth/signup` | `{email, displayName, password, applyAsInstructor?}` | 201 `UserResponse` (always a LEARNER; `applyAsInstructor` files an application); 409 `EMAIL_TAKEN`; 400 field errors |
| `POST /auth/verify-email` | `{token}` | 204; 422 `VERIFICATION_TOKEN_INVALID` |
| `POST /auth/login` | `{email, password}` | 200 `TokenResponse` + `Set-Cookie: mn_refresh`; 401 `INVALID_CREDENTIALS` |
| `POST /auth/refresh` | none (the cookie) | 200 `TokenResponse` + a **rotated** cookie; 401 `SESSION_EXPIRED` / `SESSION_REVOKED` |
| `POST /auth/logout` | none (the cookie) | 204; clears the cookie |
| `GET /me` | — | 200 `UserResponse` |
| `GET /me/instructor-application` | — | 200 `{status, pitch, appliedAt, decidedAt}`; 404 none yet |
| `POST /me/instructor-application` | `{pitch?}` (≤ 500) | 201; 409 `APPLICATION_PENDING`; 422 `ALREADY_INSTRUCTOR`; a rejected applicant re-applies |
| `GET /admin/instructor-applications?cursor=` | — | 200 the pending queue, oldest first, keyset-paged by `(appliedAt, userId)`: 50 a page (§2's envelope); 400 `INVALID_CURSOR` |
| `POST /admin/instructor-applications/{userId}/approve` · `…/reject` | none | 200; **idempotent** (the row is locked, so overlapping calls agree); approve grants `INSTRUCTOR` in the same transaction, only when it moves PENDING → APPROVED; 409 `APPLICATION_DECIDED` the other way |
| `GET /admin/users?email=…` | — | 200 `UserResponse` (the admin page's lookup); 404 |

- **Access token:** a 15-minute HS256 JWT with claims `sub` (user id), `roles`, `email_verified`
  and `sid`. Clients keep it **in memory**.
- **Refresh token:** only ever in the `mn_refresh` cookie: `HttpOnly`, `Secure` (outside dev),
  `SameSite=Strict`, `Path=/api/v1/auth`. It is single-use. Presenting a used one revokes the
  session ([ADR-0006](../adr/0006-rotating-refresh-tokens-over-stateless-jwt.md)), so clients
  must refresh **single-flight**.
- **Errors from the security chain** use the same Problem Details shape: 401 `UNAUTHENTICATED`
  (with `WWW-Authenticate: Bearer`), 403 `FORBIDDEN` with `reason: INSUFFICIENT_ROLE`.
- **Roles** live in the token, so a role change reaches the client at its next refresh.
- **No one chooses a role.** Signup's Student / Instructor tabs file an *application*; an admin
  approves it (Phase UI.10). The admin endpoints are ADMIN-only on the service, not the controller.

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

## 15. Progress: heartbeats and beacons

| Endpoint | Auth | Body | Answer |
|---|---|---|---|
| `PUT /me/enrollments/{courseId}` | user | — | 200 `{courseId, status, enrolledAt}`; 422 `COURSE_NOT_FREE`; 403 `ENTITLEMENT_REVOKED`; 404 for a draft or unknown course |
| `GET /me/learning` | user | — | 200 `[LearningCourseResponse]`: the learner's active courses, most recently watched first, **not paged** |
| `GET /me/courses/{courseId}/progress` | user | — | 200 `{enrolled, percentComplete, completedLectureIds, continueLectureId?, continuePositionSeconds}`; not enrolled → `enrolled: false`; 404 for an unknown course, or a draft the caller isn't enrolled in. `continueLectureId` is null only when every lecture is complete or there is nothing to watch |
| `PUT /me/progress/{lectureId}` | user | `{positionSeconds}` (0–86 400) | 200 `{beaconToken, beaconExpiresAt}` |
| `GET /me/progress/{lectureId}` | user | — | 200 `{positionSeconds, maxPositionSeconds, completed}`; 404 for an unknown lecture |
| `POST /progress/beacon` | public | `{token, positionSeconds}` | 204; 403 `BEACON_TOKEN_INVALID` |

- **A heartbeat is a PUT:** it sets a position, and repeating it changes nothing. The player
  sends one every 15 s while playing, and on pause and seek.
- **No SQL on the heartbeat path.** It lands in a Redis buffer flushed every 30 s
  ([ADR-0022](../adr/0022-progress-write-back-buffer.md)). Whether the lecture exists and the
  learner is enrolled is checked at the flush, so a heartbeat for a lecture you don't hold answers
  200 and is dropped there. Don't read a 200 as "saved".
- **Two freshnesses:** `GET /me/progress/…` and `GET /me/courses/…/progress` read through the
  buffer (a heartbeat a second old counts). `GET /me/learning` reads rollups that are at most one
  flush (30 s) behind.
- **Enrolling for free is a PUT on the pair:** idempotent by nature, so it needs no
  `Idempotency-Key`. Paid courses are enrolled by their purchase (ADR-0021).
- **The beacon's credential is in its body.** `navigator.sendBeacon`, the one request a closing
  page reliably sends, can't set `Authorization`, and the access token may have expired anyway.
  Each heartbeat answers a fresh token: signed (HMAC), valid 2 minutes, for **one user, one
  lecture, one client IP**. Forged, expired, mangled and other-network tokens all get the same 403.

Implemented by `enrollment.web.EnrollmentController`, `ProgressController` and
`LearningController`. Design: [`docs/lld/progress.md`](../lld/progress.md).

## 16. Reviews and Q&A

| Endpoint | Auth | Body | Answer |
|---|---|---|---|
| `GET /courses/{courseId}/reviews?cursor&limit` | public | — | 200 `CursorPage<ReviewResponse>`, newest first (`limit` 1–50, default 10); 404 unless published |
| `GET /courses/{courseId}/reviews/summary` | public | — | 200 `{average, count, stars: [1★ … 5★]}` |
| `GET /me/reviews/{courseId}` | user | — | 200 `ReviewResponse`; 404 `Review` when you haven't reviewed it |
| `PUT /me/reviews/{courseId}` | user | `{rating: 1–5, body?: ≤ 2000}` | 200 `ReviewResponse` (create or rewrite); 403 `NOT_ENROLLED` / `OWN_COURSE`; 404 unless published |
| `DELETE /me/reviews/{courseId}` | user | — | 204, also when there was none |
| `GET /lectures/{lectureId}/questions?cursor&limit` | participant | — | 200 `CursorPage<QuestionSummary>`, newest first |
| `POST /lectures/{lectureId}/questions` | participant + `Idempotency-Key` | `{title: 1–150, body?: ≤ 5000}` | 201 `QuestionSummary`, `Location: /questions/{id}` |
| `GET /questions/{questionId}` | participant | — | 200 `{question, answers[], truncated}` |
| `POST /questions/{questionId}/answers` | participant + `Idempotency-Key` | `{parentAnswerId?, body}` | 201 `AnswerResponse`; 422 `REPLY_TOO_DEEP`; 404 for a parent from another thread |

- **One review per learner per course**, so writing is a `PUT` on the pair: idempotent, no key.
  Only an **actively enrolled** learner who doesn't teach the course may review it.
- **A participant** is an enrolled learner, the course's instructor or an admin. Anyone else gets
  403 `NOT_ENROLLED`. The course must be published or **archived** (its learners keep its Q&A and
  may still review it); a draft is 404. The public review list and summary need it published.
- **A thread is a flat list in reading order** (each answer, then its replies, oldest first). Indent
  by `depth` (1–3); `parentId` names the answer a reply belongs to. `byInstructor` marks the
  course's teacher. At most 500 answers are returned (`truncated: true` beyond).
- Names are snapshots (`authorName`), never user ids.

Implemented by `engagement.web.ReviewController`, `MyReviewController` and `QuestionController`.
Design: [`docs/lld/engagement.md`](../lld/engagement.md), ADR-0023.

## 17. Search

| Endpoint | Auth | Answer |
|---|---|---|
| `GET /search/courses?q=&mode=&limit=` | public | 200 `{mode, degraded, items: CourseSummary[]}`, best first; `q` 1–200 chars, `mode` `KEYWORD` \| `SEMANTIC` \| `HYBRID` (default), `limit` 1–50 (default 20) |
| `GET /search/suggestions?q=` | public | 200 `[{slug, title}]`, at most 8; `q` 2–100 chars |

- **Ranked, not paged.** No cursor: relevance is computed per query. For deep browsing, use the
  filtered catalog (§2).
- **`mode` in the answer is what actually ran.** `degraded: true` means semantic ranking was asked
  for but the embedding model was unavailable (or semantic search isn't enabled where you are),
  so these are keyword results. A client can show "showing keyword matches" and must not treat
  it as an error.
- **Under `/search`, not `/courses/search`:** that would shadow a course whose slug is "search".
- Published courses only, for everyone (drafts are never searchable, even by their owner).

Implemented by `catalog.web.SearchController` → `CourseSearchService`. Design:
[`docs/lld/search.md`](../lld/search.md), ADR-0024.
