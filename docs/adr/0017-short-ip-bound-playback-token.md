# ADR-0017 — Learner playback: a 5-minute token bound to user, lecture and IP; the player re-tickets

**Status:** accepted · **Date:** 2026-10-14 · **Deciders:** Nimai
**Amends:** [ADR-0013](0013-hls-for-playback.md) (delivery) and [API conventions §11](../api/conventions.md#11-media-tokens-not-the-session) (the 6-hour ticket decided in 7.9), for **learner** playback only
**Context links:** [`docs/lld/entitlement-engine.md`](../lld/entitlement-engine.md) §5, §8

## Context

Phase 7 gave the asset's **owner** a 6-hour ticket for the HLS playlists (owner or admin only). 7.9
rejected the 5-minute ticket the roadmap first planned: a VOD player fetches *another rung's*
playlist whenever ABR switches, which can happen an hour into a lecture, and a 5-minute ticket
broke the switch.

Learners are different from an instructor previewing their own upload:

- **Access can end mid-watch.** A refund revokes it; with a 6-hour ticket a refunded learner
  keeps watching for 6 hours, and so does anyone they sent the link to.
- **The link is worth sharing.** A paid course's playlist URL, pasted into a chat, plays for
  anyone until it expires.

## Decision

1. **A new token for lecture playback**, issued by the entitlement module after the decision
   (`POST /api/v1/lectures/{id}/playback`, behind `@PreAuthorize`). Payload: `lectureId ·
   assetId · userId · expiresAt`, through the kernel's `SignedTokens` (HMAC-SHA256,
   constant-time compare).
2. **5-minute lifetime** (`masternova.entitlement.token-ttl`). This bounds how stale a decision
   can be when a playlist is fetched.
3. **Bound to the client IP, which is signed but not carried.** The MAC covers the payload *and*
   the IP the token was issued to; the token carries only the payload. Verification recomputes
   the MAC with the requesting IP. A link replayed from another network fails, and no IP address
   ends up in URLs, logs or screenshots. The IP is `request.getRemoteAddr()` with
   `server.forward-headers-strategy: native`: Tomcat's `RemoteIpValve` takes `X-Forwarded-For`
   only from internal proxies (our nginx), so a client can't spoof it. ⭐ Found in the phase
   review: "internal" includes Docker's 172.x gateway, so the api's own port is published on
   loopback only (`127.0.0.1:8080`); browsers reach it through nginx.
4. **The playlists are verified by token alone:** no SQL, no Redis. The asset id is in the
   signed payload.
5. **Segments are presigned for 30 minutes** (`segment-url-ttl`), not 6 hours: a long pause
   costs one re-ticket, and a revoked learner loses the current rung within 30 minutes.
6. **The player re-tickets on a 403.** A playlist or segment refused (expired token, changed IP,
   expired segment URL) makes the player ask for a new token, which **re-runs the entitlement
   decision**, and resume at the same position. Once per failure, so a real denial is shown,
   not retried forever. This is what turns the 7.9 objection (an ABR switch after 5 minutes)
   into a short refetch instead of a broken player.
7. **Owner preview is unchanged:** `/api/v1/media/assets/{id}/playback` keeps its 6-hour,
   asset-bound ticket. An instructor watching their own upload has nothing to revoke.

## Consequences

- **Positive:**
  - A refund takes effect at the learner's next playlist fetch (≤ 5 min) and the current rung's
    segments stop within 30 minutes.
  - A leaked playlist link is useless after 5 minutes and from any other network.
  - The playlist routes stay cheap: an HMAC and a storage read.
- **Negative:**
  - **More POSTs:** a 1-hour lecture with a few ABR switches re-tickets a handful of times. Each
    is one decision (one cached row read and two small SQL).
  - **IP binding breaks a network change** (Wi-Fi → mobile, some carrier NATs rotate egress IPs).
    It costs one re-ticket, not playback.
  - **Two playback paths** (owner preview by asset, learner by lecture), both on media's one
    playlist-rewriting implementation.
  - Segment URLs are not IP-bound (S3 presigning can't). A leaked segment URL serves 6 seconds of
    video for 30 minutes. CDN signed cookies (D6) would close this.

## Alternatives rejected

| Option | Why not |
|---|---|
| **Reuse the 6-hour asset ticket** | Revocation would take 6 hours, and the link works from anywhere. |
| **5-minute token, no re-ticket** | 7.9's finding stands: the first ABR switch after 5 minutes breaks playback. |
| **Re-run the full decision on every playlist fetch** | Correct, but each rung fetch would cost a decision. The 5-minute token is the same guarantee, made cheap. |
| **IP in the payload** | Puts a personal IP address in every playlist URL. |
| **Cookies instead of a URL token** | Native Safari HLS sends cookies, but our API is cookie-free by design (bearer only, CSRF off: ADR-0006). A playlist cookie would bring CSRF back for those routes. |
| **DRM** | Same as ADR-0013: licence servers for a learning project. |
