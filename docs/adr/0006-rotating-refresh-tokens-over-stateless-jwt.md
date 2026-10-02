# ADR-0006 — Short-lived JWT + rotating refresh tokens with reuse detection

**Status:** accepted · **Date:** 2026-10-02 · **Deciders:** Nimai
**Context links:** [`docs/lld/identity.md`](../lld/identity.md) · NestJS Masternova ADR-0010 (same decision, re-derived for Spring)

## Context

Every API request must be authenticated cheaply. Sessions should last weeks. A stolen credential
must be revocable, and a *copied* refresh token must not give an attacker a silent, parallel
session.

## Decision

1. **Access token:** a JWT signed with HS256 (a server-side secret, `JWT_ACCESS_SECRET`), with a
   **15-minute** lifetime and claims `sub` (user id), `roles` and `email_verified`. It's returned
   in the response body, kept **in memory** by the Angular app, and sent as
   `Authorization: Bearer`. It's verified by Spring Security's OAuth2 resource server, with **no
   database lookup**.
2. **Refresh token:**
   - An opaque **256-bit random** value. Only its **SHA-256 hash** is stored.
   - Sent as an **httpOnly, Secure, SameSite=Strict** cookie scoped to `Path=/api/v1/auth`, so
     it's never readable by JavaScript and never sent to other endpoints.
   - Lifetime **30 days**.
3. **Rotation:** every `/auth/refresh` consumes the presented token (a conditional `UPDATE … WHERE
   consumed_at IS NULL`, so exactly one winner) and issues a new one in the **same session**
   (token family).
4. **Reuse detection:** presenting an already-consumed token means it was copied, so **revoke the
   whole session**. The attacker *and* the victim are logged out of that device, and the victim
   logs in again (a fresh session, the attacker locked out).
5. **Logout** revokes the session and clears the cookie.

## Consequences

- **Positive:**
  - Stateless, fast authorisation on every request.
  - Revocation within ≤ 15 minutes (at the next refresh).
  - Theft is *detected*, not just limited.
  - A DB leak exposes no usable tokens.
- **Negative:**
  - A revoked user keeps a valid access token for up to 15 minutes. Acceptable here; a denylist
    of token ids could close the gap if ever needed.
  - Two tabs refreshing simultaneously can look like reuse. **Mitigation:** single-flight refresh
    in the client (one refresh in flight, other requests wait for it).
  - HS256 means every verifier holds the signing secret. Fine for one api deployable; switch to
    RS256 + JWKS if other services ever need to verify without being able to sign.

## Alternatives rejected

| Option | Why not |
|---|---|
| long-lived stateless JWT (days) | can't be revoked: a stolen token works until it expires |
| server-side sessions (`JSESSIONID`) | state lookup on every request; sticky sessions or a shared session store needed |
| access token in `localStorage` | any XSS reads it |
| refresh token in the response body | the client must store it somewhere JavaScript can read |
| non-rotating refresh token | a copy works in parallel with the original, undetected |
| storing raw tokens | a DB leak = live sessions for everyone |
