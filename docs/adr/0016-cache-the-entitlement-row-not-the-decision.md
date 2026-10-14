# ADR-0016 — Cache the entitlement row, not the decision (a Decorator over the repository)

**Status:** accepted · **Date:** 2026-10-14 · **Deciders:** Nimai
**Context links:** [`docs/lld/entitlement-engine.md`](../lld/entitlement-engine.md) §4, §8 · NestJS Masternova (same decision, `CachedEntitlementRepository`)

## Context

The entitlement decision runs on every course page (once per lecture, for the lock icons), every
lecture open, and every playback token. It reads:

- the **entitlement row** for `(user, course)`, which this module owns and which changes about
  twice in its life (bought, maybe refunded);
- **course facts** (instructor, publish status, price, preview flags), which catalog owns and
  which an instructor edits all the time.

We want the hot path not to hit Postgres for the row every time, and a refund to take effect
quickly.

## Decision

1. **Cache the row, not the decision.** A cached *decision* would have five invalidation
   triggers (grant, revoke, price change, publish/archive, preview toggled), three of them in
   catalog and each a fan-out over every learner of the course. The row has exactly three writers
   (`grant`, `revoke`, and expiry, which is a read-time check), all methods on the interface being
   cached.
2. **As a Decorator:** `CachedEntitlementRepository implements EntitlementRepository` and wraps
   `JdbcEntitlementRepository`. The decoration is visible in one `@Bean` method. The JDBC class
   doesn't know it's cached: its IT tests SQL, and the decorator's unit test (a fake inner, a
   fake Redis) tests invalidation.
3. **Evict after commit, never write through.** `grant`/`revoke` delegate, then register a
   `TransactionSynchronization.afterCommit` that `DEL`s the key (immediately if no transaction
   is active). Evicting *before* the commit lets a concurrent reader reload the old row and
   cache it; writing the new value through installs what *this* process believes, and two
   concurrent writers race. Deleting sends the next read to the only source that knows.
4. **Cache negatives too** (`"-"`): a stranger opening paid courses is the traffic the cache is
   for, and a positive-only cache misses it every time.
5. **Fail open to Postgres.** Every Redis call is wrapped: an error is counted
   (`entitlement.cache{result=error}`), logged, and the read falls through to the database. A
   failed eviction does not fail the grant or revoke: the refund has committed, and a 500 would
   make the payment webhook retry a finished operation. Redis commands time out after 500 ms.
6. **TTL 5 minutes** (`masternova.entitlement.cache-ttl`). It's the backstop for a missed
   eviction and for the race below, not the invalidation mechanism.
7. **Values are JSON of the `Entitlement` record** (Jackson 3, `Instant` as ISO-8601), with a
   version in the key (`entitlement:v1:…`) so a changed shape is a new key, not a parse error.
   A unit test round-trips `expiresAt`: the NestJS version shipped a bug where the cached
   `expiresAt` came back as a string and expired access kept working, on the cached path only.

## Consequences

- **Positive:**
  - One Redis `GET` per decision for a signed-in viewer; none for an anonymous one.
  - Catalog never needs to know entitlement caches anything.
  - Removing the cache is deleting one wrapper in `EntitlementConfig`.
- **Negative:**
  - **Read-old / evict / write-old race:** a reader that loaded the row before the commit can
    `SET` it after the eviction. The stale value lives one TTL. Accepted: a refund that takes
    effect within 5 minutes is fine; a versioned compare-and-set would cost a Lua script.
  - Course facts are not cached: two small SQL statements per decision. Cache them only if a
    measurement says so, in catalog, with catalog's invalidation.

## Alternatives rejected

| Option | Why not |
|---|---|
| **Cache the decision** per `(user, lecture)` | Five triggers across two modules; catalog would have to publish events just to keep entitlement's cache honest. |
| **Spring's `@Cacheable` / `@CacheEvict`** | It can do it: `TransactionAwareCacheDecorator` defers evictions to after commit, `cache-null-values` caches negatives, a `CacheErrorHandler` fails open. But the cache becomes annotations and SpEL keys on the JDBC repository, the class that should not know it's cached, and testing the invalidation needs a Spring context. The explicit Decorator is ~100 lines and tested in milliseconds. |
| **Write-through on grant/revoke** | Two concurrent writers each install their own view; neither knows it lost. |
| **No cache** | A valid choice at this scale. The row lookup is a PK hit (~0.2 ms). The cache exists because the same decision runs once per lecture on every course page, for every visitor, and because this is the place to learn it properly. |
