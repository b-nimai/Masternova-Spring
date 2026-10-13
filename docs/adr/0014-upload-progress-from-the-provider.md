# ADR-0014 — Upload progress lives in the storage provider, not in our database

**Status:** accepted · **Date:** 2026-10-13 · **Deciders:** Nimai
**Context links:** [`docs/lld/media.md`](../lld/media.md) §3, §5 · [ADR-0012](0012-seaweedfs-as-local-s3.md) (the probe checks `ListParts`)

## Context

A source video is up to 10 GiB, and it goes **browser → storage** through presigned multipart
URLs (the bytes never pass through the api). Uploads get interrupted: the laptop sleeps, the tab
closes, the network drops. **Resume** means uploading only the missing parts, possibly hours
later, maybe from a reloaded tab.

So who knows which parts are uploaded?

1. **Our database:** a `upload_part` row per part, which the browser reports after each PUT
   (`POST …/parts/{n}` with its ETag).
2. **The provider:** S3 already records every part it accepted (`ListParts`: number, size,
   ETag). We ask it when it matters.

## Decision

**The provider is the source of truth.** Our `upload_session` row stores only the *plan*: upload
id, object key, declared size, part size, part count, status, expiry. It never records which
parts are done.

- **Resume:** `GET /media/uploads/{id}` calls `ListParts` and returns the missing part numbers
  with fresh presigned URLs. The browser uploads those.
- **Complete:** the browser only says "done" (`POST …/complete`, no body). The server calls
  `ListParts` and checks it against the plan:
  - every part number `1..partCount` is present;
  - every part except the last has exactly `partSize` bytes;
  - the sizes add up to the declared size.
  Then it calls `CompleteMultipartUpload` with **the provider's ETags** and confirms the object's
  length with `HEAD`. **The client never sends an ETag.**
- **Progress bars** come from the browser's own upload events. The server isn't told about each
  part.

## Consequences

- **Positive:**
  - **There's nothing to get out of sync.** A part the browser uploaded but failed to report
    (tab closed between the PUT and the report) is still counted: S3 has it. With option 1, that
    part would be re-uploaded, or worse, the row would say done and S3 wouldn't have it.
  - **No write per part:** a 100-part upload is 3 api calls (create, complete, maybe resume),
    not 102.
  - **The client can't lie:** a hostile or buggy client can't complete an upload with a part
    missing or a size it didn't declare. The plan check uses what storage actually holds.
  - **There's one fewer table** and no cleanup job for it.
- **Negative:**
  - Resume and complete each make a `ListParts` call (one page: we plan ≤ 100 parts, and a page
    holds 1,000). That's a few ms on S3, and only on those two rare calls.
  - We trust the provider's `ListParts` to be **consistent right after an `UploadPart`
    succeeds**. S3 has been strongly consistent since 2020; the probe checks it for SeaweedFS.
  - Dashboards can't ask SQL "how far along are uploads?". That's an ops question, answered by
    the upload metrics in D3, not by a table.

## Alternatives rejected

| Option | Why not |
|---|---|
| **`upload_part` table reported by the browser** | It's a second copy of data S3 already has, and the report can be lost after the PUT succeeds (it's a separate request). Then the two copies disagree, and the code has to decide who wins. It's the NestJS-era design this ADR exists to avoid. |
| **Client-built `CompleteMultipartUpload`** (browser sends the ETag list, as many tutorials do) | The server would trust client-supplied ETags and part lists. It needs CORS to expose `ETag` for correctness, not just for convenience. And it still can't detect a part uploaded with the wrong size. |
| **Upload through the api** (stream to S3 server-side) | 10 GiB through request threads and the api's bandwidth, the thing presigning avoids. Resume would need our own chunk protocol. |
| **tus protocol server** | A good resumable protocol, but it's another server to run, and S3 multipart already gives us the resumable primitive. |
