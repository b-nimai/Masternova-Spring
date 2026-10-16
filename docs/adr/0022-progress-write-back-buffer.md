# ADR-0022 — Lecture progress through a Redis write-back buffer, flushed every 30 s (accepted loss ≤ 30 s)

**Status:** accepted · **Date:** 2026-10-15 · **Deciders:** Nimai
**Context links:** [`docs/lld/progress.md`](../lld/progress.md) §3–§8 · [ADR-0016](0016-cache-the-entitlement-row-not-the-decision.md) (the other Redis Decorator) · [pattern 13](../../patterns/docs/13-decorator.md)

## Context

A playing lecture sends a **heartbeat every 15 s** (the position in seconds). 1,000 learners
watching at once is ~67 writes/s, 10,000 is ~670/s, each an upsert on a hot row. The data is
*valuable but not precious*: losing the last half minute of "where was I" is an annoyance;
losing a payment is not. The reads are "resume this lecture" (must see the latest heartbeat,
including one sent a second ago) and "My Learning" (percent complete, last lecture).

## Decision

1. **Heartbeats go to Redis, not Postgres.** A Lua script merges each one into a hash
   (`progress:v1:dirty`, field `user:lecture` → `last|max|atMillis`), atomically: `max` only goes
   up, `last` is the newest by heartbeat time. No SQL on the heartbeat path.
2. **A flush every 30 s** (`@Scheduled`, one replica via ShedLock) moves the batch to Postgres:
   `RENAME dirty → flushing` (atomic: new heartbeats start a new `dirty`), read it, write it in
   one transaction (one batched upsert + the rollups), then `DEL flushing`.
3. **The merge is the same in three places** (Java `ProgressUpdate.merge`, the Lua script, the
   SQL upsert) and is **commutative, associative and idempotent**. That is what makes the flush
   crash-safe without coordination: a flush that died after the `RENAME` leaves `flushing`
   behind, and the next one writes it again first. Writing a batch twice changes nothing.
4. **Reads go through the buffer**: the player's resume point and the course page merge the
   `dirty` and `flushing` entries over the row. "My Learning" reads the rollup in Postgres, up to
   one flush behind (it shows percentages, not seconds).
5. **Redis down → write-through.** A heartbeat that can't reach Redis is written straight to
   Postgres through the same validated path (degraded, counted, never a 500).
6. **The buffer must never be evicted.** The cluster's Redis ran `allkeys-lru` (fine while it held
   only caches). The buffer has no TTL, so the policy becomes `volatile-lru`: only keys with a TTL
   (the entitlement cache) are candidates; the buffer stays.
7. **Accepted loss: up to one flush interval (30 s) per learner**, only if Redis itself loses the
   data (a restart without persistence, a failover). The api crashing loses nothing: the buffer
   is outside it.

## Consequences

- **Positive:**
  - Postgres sees one transaction per 30 s for all viewers instead of 67–670 upserts per second.
    Batching also collapses repeats: a learner's two heartbeats in one window are one row write.
  - The heartbeat endpoint does no SQL at all: an HMAC for the beacon token and one Redis call.
  - Crash safety comes from the merge's algebra, which a property test checks, not from locks.
- **Negative:**
  - The dashboard lags up to 30 s. The player and the course page don't.
  - Validation (does the lecture exist, is the learner enrolled) moves to the flush: a forged
    heartbeat sits in Redis for ≤ 30 s and is dropped (`enrollment.progress.dropped`). A flood of
    them costs Redis memory, not SQL.
  - A third copy of the merge rule (Lua) to keep in sync; `WriteBackProgressStoreIT` runs the same
    sequences through Lua, SQL and Java.

## Alternatives rejected

| Option | Why not |
|---|---|
| **Upsert in Postgres on every heartbeat** | 67–670 writes/s of row churn (MVCC bloat, WAL, autovacuum) for data whose loss is cheap. |
| **Client-side throttling only (send every 60 s)** | Reduces volume by 4×, not by orders of magnitude, and makes the loss window *larger* (a closed tab loses up to a minute) without any server control. |
| **Write-through cache (Redis + Postgres on every heartbeat)** | Makes reads fast; writes, the actual problem, stay. |
| **Kafka/stream + consumer** | A broker to run for a 30 s batch job; Redis is already there. |
| **`MULTI`/`HSET` without Lua** | `max` must be computed from the stored value: a read-modify-write that races between replicas. The script runs atomically in Redis. |
| **`HGETALL` + `HDEL` of the read fields** | A heartbeat arriving between the read and the delete is deleted unflushed. `RENAME` swaps the whole hash atomically. |
| **Redis persistence (AOF) to shrink the loss to ~1 s** | Possible later; not worth a stateful Redis now for 30 s of resume points. Revisit if progress ever drives certificates. |
