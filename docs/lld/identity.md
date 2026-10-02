# Identity — Low Level Design

> **One-liner:** who you are and what you may do. Signup with email verification, login that
> issues a short-lived **JWT access token** and a **rotating refresh token** (stolen-token reuse
> revokes the whole session), role-based access (LEARNER / INSTRUCTOR / ADMIN), and the
> deny-by-default security filter chain for the whole API.

**Module:** `backend/api/src/main/java/com/masternova/api/identity` · **Status:** draft (§1–§9, Phase 3.1) → built (3.10)
**Last updated:** 2026-10-02 · **Angular:** `frontend/src/app/core/auth/` (store, interceptor, guards) + `features/auth/` (pages)

## 1. Problem

Learners, instructors and admins need accounts. The API must know **who** is calling (cheaply,
on every request) and **what they may do**. Sessions must last weeks without staying logged in
forever after a laptop is stolen. An attacker who copies a refresh token must be locked out the
moment either party uses it twice.

## 2. Forces

- **Every request is authenticated.** The check must be cheap, so no DB lookup per request: a
  signed, short-lived JWT, verified in memory.
- **Revocation.** A JWT can't be revoked before it expires, so keep it short (15 min). The
  long-lived credential (the refresh token) is **stateful** and revocable.
- **Token theft.** XSS can read `localStorage`. Keep the access token in memory, and the refresh
  token in an **httpOnly** cookie that JavaScript can't read.
- **Replay of a stolen refresh token.** Rotation plus reuse detection: a refresh token works
  **once**, and seeing it a second time means someone copied it, so revoke the session.
- **Concurrency.** Two tabs refreshing at once look like reuse. The client does **single-flight**
  refresh (one in-flight refresh per tab), and the server claims a token with a conditional
  `UPDATE` (exactly one winner).
- **Secrets at rest.** Store only **SHA-256 hashes** of refresh and verification tokens. A DB
  leak doesn't hand out live tokens.
- **Module boundaries.** Identity owns authentication and the security chain; other modules
  declare their own public endpoints (`PublicEndpoints`) instead of identity knowing them.

## 3. Domain model

| Entity / value | Key fields | Invariants |
|---|---|---|
| `User` (aggregate root) | id, `Email`, display name, password hash, roles, `emailVerifiedAt`, `@Version` | email unique (normalised lower-case); new users are `LEARNER`; at least one role |
| `Email` (value object, record) | value | trimmed, lower-cased, basic format check, ≤ 320 chars |
| `Role` (enum) | LEARNER, INSTRUCTOR, ADMIN | JWT `roles` claim → `ROLE_*` authorities |
| `AuthSession` (a device / login) | id = token family, user, created, last used, `revokedAt`, `revokeReason` | once revoked, never un-revoked; every refresh token belongs to one |
| `RefreshToken` | id, session, `tokenHash` (unique), issued, expires, `consumedAt` | consumed **at most once** (conditional update); consumed + presented again → REUSE |
| `VerificationToken` | id, user, `tokenHash`, purpose, expires, `usedAt` | single use, 24 h |

## 4. Class design

```mermaid
classDiagram
  direction LR
  class AuthController
  class SignupService
  class AuthService {
    +login(email, password, userAgent) Tokens
    +refresh(rawRefreshToken) Tokens
    +logout(rawRefreshToken)
  }
  class AccessTokenIssuer {
    +issue(User) AccessToken
  }
  class RefreshTokens {
    +issue(AuthSession) RawToken
    +claim(raw) ClaimResult
  }
  class PasswordEncoder {
    <<Spring, Strategy>>
  }
  class UserRepository {
    <<Spring Data>>
  }
  class SecurityConfig {
    deny-by-default chain
  }
  class PublicEndpoints {
    <<platform API>>
  }
  AuthController --> SignupService
  AuthController --> AuthService
  AuthService --> AccessTokenIssuer
  AuthService --> RefreshTokens
  AuthService --> PasswordEncoder
  SignupService --> UserRepository
  SignupService --> EventPublisher : UserRegistered
  SecurityConfig --> PublicEndpoints : collects every module's
```

**Module API** (top-level `com.masternova.api.identity`): `Role`, `UserRegistered` (event),
`CurrentUser` (who is calling: id + roles). Everything else is internal.

## 5. Main flows

**Login → refresh (rotation) → reuse detected:**

```mermaid
sequenceDiagram
  participant B as Browser (Angular)
  participant A as AuthController
  participant DB as Postgres
  B->>A: POST /auth/login {email, password}
  A->>DB: user by email; bcrypt check; INSERT auth_session + refresh_token(hash R1)
  A-->>B: 200 {accessToken (JWT, 15 min)} + Set-Cookie refresh=R1 (httpOnly, SameSite=Strict, Path=/api/v1/auth)
  Note over B: access token kept in memory only
  B->>A: POST /auth/refresh (cookie R1)
  A->>DB: UPDATE refresh_token SET consumed_at=now WHERE hash=h(R1) AND consumed_at IS NULL  → 1 row
  A->>DB: INSERT refresh_token(hash R2) in the same session
  A-->>B: 200 {new access token} + Set-Cookie refresh=R2
  Note over B,A: an attacker who copied R1 tries it later
  B->>A: POST /auth/refresh (cookie R1 again)
  A->>DB: conditional UPDATE → 0 rows; R1 exists and is consumed → REUSE
  A->>DB: revoke the whole session (R2 dies too)
  A-->>B: 401 SESSION_REVOKED — everyone on that session must log in again
```

**Signup → verify:** `POST /auth/signup` → user (LEARNER, unverified) + verification token
(hash stored) + `UserRegistered` event **through the outbox** (the email is sent by the
notification module in Phase 4; in dev the link is logged) → `POST /auth/verify-email {token}`
sets `emailVerifiedAt`.

## 6. Patterns used

| Pattern | Where | The force that justified it |
|---|---|---|
| **Strategy** | Spring's `DelegatingPasswordEncoder` (bcrypt today, upgradeable by `{id}` prefix) | hashing algorithms change over time; stored hashes must keep working |
| **Chain of Responsibility** | the `SecurityFilterChain` (bearer-token filter → authorization) | each concern handles or passes the request on |
| **Observer** (via outbox) | `UserRegistered` → notification (Phase 4) | identity must not depend on notification |
| **Value Object** | `Email` record | normalisation and validation in one place |
| **Repository** | Spring Data `UserRepository`, `AuthSessionRepository`, `RefreshTokenRepository`, `VerificationTokenRepository` | persistence behind interfaces |
| *(extension point)* | `PublicEndpoints` beans (platform API) collected by `SecurityConfig` | each module owns its own public URLs, and nothing is public by accident |

## 7. Alternatives rejected

See [ADR-0006](../adr/0006-rotating-refresh-tokens-over-stateless-jwt.md). In short:

- **Long-lived stateless JWT only:** can't be revoked.
- **Server sessions (`JSESSIONID`):** stateful on every request, and needs sticky sessions or a
  session store.
- **Tokens in `localStorage`:** readable by XSS.
- **Storing raw refresh tokens:** a DB leak hands out live sessions.

## 8. Failure modes

| Failure | Detected by | Behaviour | Recovery |
|---|---|---|---|
| wrong password / unknown email | bcrypt mismatch / no user | 401 `INVALID_CREDENTIALS`, the **same** answer for both (no account probing) | — |
| expired access token | JWT `exp` | 401 `UNAUTHENTICATED` | the client refreshes once, then retries (interceptor) |
| expired / unknown refresh token | lookup | 401 `SESSION_EXPIRED` | log in again |
| refresh token reused | consumed token presented | **session revoked**, 401 `SESSION_REVOKED` | log in again; a security signal (D3 alert) |
| two tabs refresh at once | conditional UPDATE: one wins | the loser gets 401 and its tab re-reads the cookie-backed state | single-flight refresh in the client |
| duplicate signup | unique email | 409 `EMAIL_TAKEN` | — |
| verification token expired / used | lookup | 422 `VERIFICATION_TOKEN_INVALID` | request a new one (later task) |

## 9. Data & indexes

`V4__identity.sql`:

| Table | Notes |
|---|---|
| `app_user` | `email` VARCHAR(320) UNIQUE, normalised by `Email` |
| `app_user_role` | (user, role) |
| `auth_session` | index on user |
| `refresh_token` | `token_hash` UNIQUE, index on session |
| `verification_token` | `token_hash` UNIQUE |

All foreign keys are indexed (Postgres doesn't do it for you: note 10 §10).

## 10. Tests that prove it

*(Completed in 3.10.)*

## 11. Interview notes — 60-second recall

*(Written last, in 3.10.)*
