# 06 — Chunked uploads and live progress over SSE

> **One-liner:** send a 10 GiB file in parallel, retryable, resumable parts **straight to S3**,
> with no byte passing through our api, and watch its processing live over Server-Sent Events read
> through `HttpClient`, so the bearer token and the refresh interceptor still apply.

**Last updated:** 2026-10-13 · **Roadmap:** 7.8
**Code:**
- `core/api/media-api.ts`: `MediaApi`, plus `parseSse`, a pure function;
- `features/instructor/upload/chunked-uploader.ts`: `ChunkedUploader`;
- `features/instructor/upload/lecture-video/`: `LectureVideo`, embedded in the curriculum
  editor.

**Backend:** [media LLD](../../docs/lld/media.md) · [ADR-0014](../../docs/adr/0014-upload-progress-from-the-provider.md) (the provider is the source of truth) · [API conventions §8–§9](../../docs/api/conventions.md)

## 1. The shape of an upload ⭐⭐⭐

```text
POST /media/uploads  (Idempotency-Key)  → plan: partSize, partCount, a presigned url per part
PUT  <presigned url>  × partCount        → straight to storage, 4 in flight
POST /media/uploads/{id}/complete         → no body: the server asks storage what arrived
GET  /media/assets/{id}/stream            → snapshot · progress ticks · READY → the stream ends
```

The api never sees the bytes. It hands out **permission** (signed urls) and checks **results**
(`ListParts`).

## 2. Parallel parts: `mergeMap(…, 4)` ⭐⭐⭐

```ts
from(plan.parts).pipe(
  mergeMap((part) => this.putPart(file, plan, part.number, part.url), PARALLEL_PARTS),
  scan((total, { part, bytes }) => …),   // bytes per part → one running total
)
```

- **`mergeMap` with a concurrency limit** is the whole parallel uploader: at most 4 PUTs in
  flight, and the next starts as one finishes. `concatMap` would be serial (slow); plain
  `mergeMap` would open 100 connections at once.
- **`file.slice(start, end)`** is a `Blob` view of the file, not a copy. Slicing 10 GiB costs
  nothing until the bytes are sent.
- Progress = the **sum of per-part `loaded`**, kept in a `Map` (a retried part restarts at 0 and
  overwrites its entry) plus the bytes storage already had (`uploadedBytes`, on resume).

## 3. Retry one part, not the upload ⭐⭐⭐

```ts
defer(() => this.http.put(url, blob, { observe: 'events', reportProgress: true }))
  .pipe(retry({ count: 4, delay: (_e, attempt) => timer(250 * 2 ** attempt) }))
```

- **`defer`** matters: `retry` re-subscribes, and only a deferred observable builds a *new*
  request each time.
- The retry wraps **one part's** stream, so a flaky part retries alone while the other three keep
  going.
- After the last retry the error goes up and the upload stops, but it's **resumable** (§4).

## 4. Resume = ask the server, which asks storage ⭐⭐⭐

The upload id is saved in `localStorage` under the file's **fingerprint** (`name:size:lastModified`).
Picking the same file again calls `GET /media/uploads/{id}`, which runs `ListParts` and returns urls
for **only the missing parts**. If the upload is gone (409/404: completed, aborted, expired), the
uploader starts a new one.

- The browser never keeps its own list of finished parts. A part whose response was lost (the tab
  closed right after the PUT) is still counted, because storage has it (ADR-0014).
- `localStorage` can throw (private mode, blocked storage): every access is in `try/catch`, and
  without it the upload still works; it just can't resume.
- `input.value = ''` after reading the file: picking the **same** file again must fire `change`
  again, or "resume" would do nothing.

## 5. Never send our token to S3 ⭐⭐

The presigned url **is** the authorization. A second `Authorization: Bearer …` header makes S3
reject the request ("only one auth mechanism"). The interceptor only touches relative `/api/` urls,
and the spec asserts it: `expect(put.request.headers.has('Authorization')).toBe(false)`.

## 6. SSE without `EventSource` ⭐⭐⭐

`EventSource` can't set headers, so it can't send a bearer token. The usual workarounds are a
token in the url (it ends up in logs and history) or a cookie (CSRF, and a second auth path).
Instead:

```ts
this.http.get(url, { observe: 'events', reportProgress: true, responseType: 'text' })
// HttpDownloadProgressEvent.partialText = everything received so far
```

The **fetch backend** (`withFetch()`) streams the body and reports `partialText` as it grows.
`MediaApi.stream` takes the new suffix each time, appends it to a buffer, and lets `parseSse`
split off complete events (an event ends with a blank line; a chunk can end mid-event). `:` lines
are keep-alive comments. The interceptor's refresh-on-401 works as for any call.

What `EventSource` would give that this doesn't: **automatic reconnection**. The widget handles a
broken stream by showing "reconnect to see progress"; the asset's state is in the database either
way.

## 7. Follow an input with `effect` + `untracked` ⭐⭐

```ts
effect(() => {
  const asset = this.assetId();          // tracked: re-runs when the lecture's asset changes
  untracked(() => this.watch(asset));    // NOT tracked: watch() reads/writes state signals
});
```

The lecture's `assetId` (from the server's curriculum) is the source of truth. **Attach** sets it,
**undo** clears it, a **reload** restores it, and the widget follows: it unsubscribes from the old
stream (the request is cancelled) and opens the new one, or shows "Upload video" again. Without
`untracked`, writing `state` inside the effect would make the effect depend on it.

## 8. Testing it ⭐⭐⭐

| Spec | Proves |
|---|---|
| `media-api.spec` | `parseSse` (tail kept, comments skipped, multi-line data); a stream split **mid-event** across two progress chunks yields the right events and completes with the response |
| `chunked-uploader.spec` | 3 parts **in flight together**, slices `[5, 5, 2]`, no `Authorization` to storage, complete with no body, localStorage set then cleared; a 500 retried after the backoff (`vi.useFakeTimers`); resume sends only part 3; a dead stored upload starts over |
| `lecture-video.spec` | progress → "Uploading 25%" → emits the asset id; a stream to READY; a FAILED code in words; an interrupted upload offers Resume; detaching cancels the stream |
| `curriculum-editor.spec` | a finished upload becomes `ATTACH_MEDIA` with the current version |

`HttpTestingController` can play a stream: `req.event({ type: HttpEventType.DownloadProgress,
partialText })` per chunk, then `req.flush(fullBody)`.

## 9. Common mistakes ⭐⭐⭐

- **`EventSource` + token in the query string.** It's logged by every proxy (§6).
- **Retrying without `defer`.** It re-subscribes to the same finished request.
- **`switchMap` for parts.** It cancels the previous part's upload when the next starts.
- **Tracking finished parts in the browser** and sending them to `complete`. A lost response then
  means a lost part (ADR-0014).
- **Reading `localStorage` without `try/catch`.** Private Safari throws on `setItem`.
- **Building the SSE parser around `\n`.** Servers may send `\r\n`; events end at a **blank
  line**, not at a newline.

## 10. Interview Q&A

- **Q: How do you upload a 10 GB file from a browser?** **A:** Multipart, straight to object
  storage with presigned part urls (the server never streams the bytes), N parts in parallel with
  per-part retries, and resume by asking storage which parts it has.
- **Q: How does the client resume after a reload?** **A:** The upload id is stored under a
  fingerprint of the file. Picking the file again asks the server, which asks S3 `ListParts`, for
  the missing parts and fresh urls.
- **Q: SSE with authentication?** **A:** `EventSource` can't send headers, so read the stream with
  `fetch` / `HttpClient` (`reportProgress` + `partialText`) and parse it yourself. The normal bearer
  token and refresh flow then apply. The cost is reconnection, which you do yourself.
- **Q: Why `mergeMap(fn, 4)` and not `forkJoin`?** **A:** `forkJoin` starts every part at once
  and only emits at the end. `mergeMap` with a limit keeps 4 in flight and emits as each progresses.

## 11. 30-second recall

- Plan from the api → PUT parts straight to S3 (4 at a time, `mergeMap`) → complete with no body.
- Per-part `retry` around a `defer`red PUT; the upload fails only after a part has failed 5 times.
- Resume: fingerprint → stored upload id → `GET` asks storage what's missing.
- Never send our bearer to S3: the signed url is the auth.
- SSE through `HttpClient` (`partialText`) + `parseSse`, so the bearer and refresh keep working.
- The widget follows `assetId` with `effect` + `untracked`; attach/undo/reload all just work.
