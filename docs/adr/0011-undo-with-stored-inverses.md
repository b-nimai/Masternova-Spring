# ADR-0011 — Curriculum undo: stored inverse commands, not a snapshot stack

**Status:** accepted · **Date:** 2026-10-03 · **Deciders:** Nimai
**Context links:** [`docs/lld/catalog-authoring.md`](../lld/catalog-authoring.md) · [pattern note 04 Command](../../patterns/docs/04-command.md) · [pattern note 05 Memento](../../patterns/docs/05-memento.md) · `UndoStrategyComparisonTest`

## Context

The curriculum editor needs undo and redo (Ctrl+Z / Ctrl+Shift+Z). The history must survive a
second api replica and a deploy, so it lives in Postgres, not in memory. Two classic designs:

1. **Snapshot stack (Memento only):** before every edit, store the whole curriculum; undo restores
   the previous snapshot.
2. **Inverse commands (Command + a small Memento):** every edit is a command whose `applyTo`
   returns its inverse; store both; undo applies the inverse. Only destructive edits carry a
   snapshot, of just what they destroyed (`SectionSnapshot` / `LectureSnapshot`).

The roadmap asked to build both, keep one, and record why.

## Measurements

`UndoStrategyComparisonTest`: one history entry, as JSON, on a 10-section × 5-lecture course:

| Edit | Inverse commands (command + inverse) | Snapshot stack (whole curriculum) |
|---|---:|---:|
| rename a section | 193 B | 7,541 B |
| move a lecture | 284 B | 7,541 B |
| remove a lecture | 312 B | 7,541 B |
| remove a section (5 lectures) | 880 B | 7,541 B |

The snapshot grows with the course (every entry is the whole curriculum); a command grows with the
edit. An instructor's afternoon (~200 edits) is ~60 KB of commands vs ~1.5 MB of snapshots for this
course, and a bigger course widens the gap.

## Decision

**Inverse commands** (`course_edit`: command + inverse as jsonb, `undone_at` for the redo branch).

1. **Size:** 9–39× smaller per entry (above), independent of the course's size.
2. **Intent is kept:** the history says *"moved Pods to Core"*, which an activity log or an
   "undo: move lecture" tooltip can show. A snapshot only says "it looked like this".
3. **Identity is kept:** the inverse of a removal restores the same section and lecture ids
   (media and progress reference them). A snapshot restore could do that too, but would rewrite
   *every* row on every undo.
4. **It's the edit pipeline already:** undo and redo go through the same `Course.apply` (editable
   check, rollups, version touch) as a normal edit. A snapshot restore would be a second, bulk
   write path that bypasses the commands.

The Memento pattern stays where it's the right size: the inverse of a *removal* carries a snapshot
of what was removed (and only that).

## Consequences

- **Positive:** small history; a readable activity trail; one write path; ids preserved.
- **Negative:**
  - Every command must implement its inverse correctly. Guarded by `CurriculumCommandTest`:
    every client kind must round-trip to an identical curriculum.
  - A stored inverse written by an older deploy must stay readable. The JSON contract
    (`CurriculumCommandJsonTest`) covers it; a renamed field needs a Jackson alias.
  - A redo re-applies the original command and stores the *fresh* inverse it produces.

## Alternatives rejected

| Option | Why not |
|---|---|
| snapshot stack (Memento only) | 7.5 KB per edit here and growing with the course; loses intent; a second bulk write path |
| in-memory undo stack (per session) | lost with two replicas or one deploy |
| deriving the inverse at undo time | impossible after a delete |
| event sourcing the whole course | rebuilds the read model from events; far more than undo needs |
