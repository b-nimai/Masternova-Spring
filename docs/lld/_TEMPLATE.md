# <Feature> — Low Level Design

> **One-liner:** what this module is responsible for, in a single sentence.

**Module:** `backend/api/src/main/java/com/masternova/api/<module>` · **Status:** draft | built | hardened
**Last updated:** YYYY-MM-DD · **Angular:** `frontend/src/app/features/<feature>`

## 1. Problem

What are we solving, and for whom. One paragraph.

## 2. Forces

Why this is not trivial. Name them explicitly: concurrency · retries · money ·
multiple actors · partial failure · write volume · external system you don't control.

## 3. Domain model

Entities (JPA `@Entity`) and value objects (`record`), their invariants ("an order can never
leave PAID without an enrollment"), and legal states (a `sealed interface` or an enum).

## 4. Class design

```mermaid
classDiagram
  %% public module API (top-level package) vs internal (sub-packages)
  %% interfaces, implementations, @DesignPattern roles
```

**Module API** (what other modules may use): the top-level public types and published events.

## 5. Main flow

```mermaid
sequenceDiagram
  %% the happy path, then the interesting failure path
```

## 6. Patterns used

| Pattern | Class (`@DesignPattern` role) | The force that justified it | Catalog note |
| ------- | ----------------------------- | --------------------------- | ------------ |

## 7. Alternatives rejected

| Option | Why not |
| ------ | ------- |

## 8. Failure modes

| Failure | How it is detected | Behaviour | Recovery |
| ------- | ------------------ | --------- | -------- |

## 9. Data & indexes

Flyway migration(s) `V<n>__<module>_*.sql`, tables touched, the indexes that serve the
queries, and the `@Transactional` boundaries.

## 10. Tests that prove it

- Unit (no Spring, no DB): the pattern classes.
- `@ApplicationModuleTest`: the module in isolation.
- Testcontainers `*IT`: persistence and the idempotency/concurrency proofs ("50× concurrently → 1 effect").
- Angular: component + service specs.

## 11. Interview notes — 60-second recall

The compressed version: the problem, the one design decision that mattered,
and the number that proves it works.

<!--
Section 11 is the highest-value part of this file. Write it LAST, keep it to genuinely
sixty seconds, and keep it in the same shape as the LLD/0. Index.md notes so revision
feels familiar.
-->
