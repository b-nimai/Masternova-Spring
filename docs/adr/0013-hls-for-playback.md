# ADR-0013 — HLS (fMP4 segments, H.264/AAC ladder) for video playback

**Status:** accepted · **Date:** 2026-10-13 · **Deciders:** Nimai
**Context links:** [`docs/lld/video-pipeline.md`](../lld/video-pipeline.md) · [`docs/lld/media.md`](../lld/media.md) §5 (playback)

## Context

Instructors upload one source file (any container and codec, up to 10 GiB). Learners watch it on
laptops, phones and bad hotel Wi-Fi. What we need from the delivery format:

- **Adaptive bitrate (ABR):** the player switches quality when bandwidth changes, without
  stalling.
- **It plays everywhere:** Safari/iOS (which only plays HLS natively), Chrome, Firefox, Edge.
- **Seeking in a long lecture:** a jump to 47:10 fetches a few seconds of video, not the whole
  file.
- **Access control by URL:** a paid lecture must not be one permanent URL that can be shared.
  Phase 8 decides who may watch; this format has to make that check enforceable.
- **Crash-safe output** (7.10): the transcoder writes a known set of files at deterministic keys,
  and a rerun overwrites them.

## Decision

**HLS** with **fMP4 (CMAF) segments**:

- **Ladder:** H.264 (High profile) + AAC-LC, rungs 360p / 720p / 1080p. A rung is never above
  the source's height (no upscaling). The ladder is a Strategy (7.5) so dev and CI can use a
  2-rung `ultrafast` ladder.
- **Segments:** 6 s each, with a **closed GOP of 2 s aligned across rungs** (`-g` = 2 × fps,
  `-sc_threshold 0`, `-force_key_frames`), so every segment boundary is a switch point in every
  rung.
- **Files per asset:** `master.m3u8` → one `index.m3u8` + `init.mp4` + `seg_NNNNN.m4s` per rung.
  `master.m3u8` is written **last** and is the commit marker: an asset without it isn't playable,
  whatever else exists.
- **Delivery:** segments are fetched straight from storage with **short-lived presigned URLs**.
  The api serves the (small, text) playlists and rewrites each segment and `EXT-X-MAP` URI to a
  presigned URL. The access check happens once, when the api issues a **playback ticket**: the
  owner or an admin in Phase 7, and the entitlement chain at the same point in Phase 8. The
  ticket is a signed, 6-hour URL token, because native Safari HLS can't send an `Authorization`
  header ([media LLD §4](../lld/media.md#4-class-design)).
- **Player:** hls.js in Chrome, Firefox and Edge; native `<video>` in Safari (7.9).

## Consequences

- **Positive:**
  - ABR and seeking come with the format.
  - Every URL a learner holds expires, and the check sits in the one place (the playlist
    endpoint) that Phase 8 will extend.
  - CMAF segments are what DASH uses too. A DASH manifest could be added later without
    re-encoding.
  - Deterministic file names make a rerun an overwrite (7.10).
- **Negative:**
  - **Many objects per asset:** a 1-hour lecture × 3 rungs ≈ 1,800 segments. Listing and
    deleting go by prefix (`media/{assetId}/hls/`), never one by one from the DB.
  - **The playlist endpoint presigns every segment:** ~600 HMAC signatures for a 1-hour rung.
    That's local CPU (microseconds each), with no call to S3. It's measured in 7.9; if it shows
    up, cache the rewritten playlist per (asset, rung, expiry bucket).
  - **Presigned URLs expire mid-watch:** a learner who pauses longer than the expiry gets a 403
    on the next segment. The expiry is 6 h, and the player re-fetches the playlist on a 403 and
    resumes at the same position (7.9). Production would move to CDN signed cookies (D6), which
    removes the rewriting.
  - **Transcoding is slow (CPU-bound):** it's the reason the worker is a separate deployable
    (ADR-0001).

## Alternatives rejected

| Option | Why not |
|---|---|
| **Progressive MP4** (one file, range requests) | No ABR: one bitrate for every network. A single permanent object is easy to download and share. It's the simplest option, but it fails the bad-Wi-Fi learner. |
| **MPEG-DASH only** | Safari/iOS has no native DASH (needs MSE, which iOS Safari on iPhone lacks). HLS plays natively there, and through hls.js everywhere else. |
| **HLS with MPEG-TS segments** | The older form. fMP4 plays in every target, is smaller (no TS packet overhead) and is also DASH's format. TS only helps players older than ~2016. |
| **DRM (Widevine / FairPlay)** | Licence servers and per-platform integrations for a learning project. Expiring URLs plus server-side access checks are the proportionate control, and the threat model says so (D5). |
| **A managed service** (MediaConvert, Mux, Cloudflare Stream) | Fine in a real company, and it's the "provider truth" for production. Here the pipeline itself is what we're learning (DAG, retries, crash safety), so it's built. The storage key layout is the same, so swapping one in later stays possible. |
