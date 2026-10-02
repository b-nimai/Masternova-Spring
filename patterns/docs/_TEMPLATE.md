# <Pattern> — <one phrase on how Masternova uses it>

> **One-liner:** the pattern's intent in one sentence, in your own words.

**Type:** Creational | Structural | Behavioral | Enterprise · **Status:** draft | built · **Last updated:** YYYY-MM-DD
**Real code:** `com.masternova.api.<module>.<Class>` · **Lab:** [`lab/.../<pattern>/`](../lab/src/main/java/com/masternova/patterns/)

**Trigger phrase:** the words in a requirement that should make you reach for this pattern.

## 1. The problem in Masternova

What concrete requirement forced this pattern? What would the code look like *without* it
(the `if/else` or `switch` mess, the duplicated logic, the class that knows too much)?

## 2. Structure

```mermaid
classDiagram
  %% roles from the GoF book, named with the real Masternova classes
```

| GoF role | Masternova class | Responsibility |
|---|---|---|
| | | |

## 3. Code walkthrough

The key 15–30 lines from the real class, with comments on *why*, not *what*.

```java
```

## 4. Java features that make it nicer

For example: sealed interfaces for an exhaustive `switch`, records for immutable value objects,
lambdas for small strategies, `Map<Enum, Strategy>` registries.

## 5. When NOT to use it

The force that justifies it, and the situation where it would be over-engineering
(e.g. "one implementation is not a seam").

## 6. Where Spring itself uses it

## 7. Alternatives considered

| Alternative | Why not here |
|---|---|
| | |

## 8. Interview Q&A

- **Q:** …
  **A:** …

## 9. 30-second recall

- **Intent:**
- **Roles:**
- **In Masternova:**
- **Pitfall:**

*Related:* [[other-pattern]] · LLD note: `../../../LLD/<note>.md`
