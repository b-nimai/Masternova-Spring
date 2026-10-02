# ADR-0002 — Maven multi-module build

**Status:** accepted · **Date:** 2026-10-02 · **Deciders:** Nimai

## Context

The backend needs two Spring Boot apps (`api`, `worker`) that share a `kernel` library, and
the build tool should match what the target jobs (Java backend + Angular) use day to day.

## Decision

- **Maven**, with the wrapper (`./mvnw`) pinned in `.mvn/wrapper`. No global install needed.
- **`backend/pom.xml`** is the parent. It inherits `spring-boot-starter-parent`, imports the
  Spring Modulith BOM, and owns every plugin version: Spotless (google-java-format), JaCoCo,
  and Failsafe for `*IT` tests.
- **`patterns/lab`** is a *separate*, Spring-free Maven project, so pattern examples can't
  accidentally lean on the framework.

## Consequences

- Declarative, conventional, and what most enterprise Java codebases and interviewers expect.
- XML is verbose. Custom build logic is awkward, so it gets pushed into plugins or CI.

## Alternatives rejected

| Option | Why not |
|---|---|
| Gradle (Kotlin DSL) | Faster incremental builds, but less common in the target job market, and build scripts become code to maintain |
