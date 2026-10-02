# ADR-0003 — Build in vertical slices

**Status:** accepted · **Date:** 2026-10-02 · **Deciders:** Nimai

## Context

The NestJS plan went backend → DevOps → frontend → integrate. This rebuild has a second goal:
learning **Angular** alongside Java. Building all of the backend first would leave Angular
untouched for months and integration risk piled up at the end.

## Decision

Each module phase ships **its backend and its Angular screens together**, and is demoable at
the end of the phase. DevOps phases are interleaved at the points where there's something real
to ship:

- **D1 and D2** after identity.
- **D3** after the pipeline.
- **D4** after commerce.

## Consequences

- Java and Angular concepts are learned side by side. Every phase ends with something
  clickable, and contract mismatches surface within a phase, not at the end.
- Context switching inside a phase, and some UI rework when a later module changes a shared
  component.
