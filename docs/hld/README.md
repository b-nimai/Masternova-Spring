# High-level design

| File | Holds |
|---|---|
| [`01-architecture.md`](01-architecture.md) | C4 context, containers, Modulith modules, cross-cutting decisions, deployment views |
| `02-data-flows.md` | Written incrementally: each module phase adds its flow (signup, upload → playable, checkout → enroll …) |
| [`03-capacity.md`](03-capacity.md) | k6 against the SLOs: the knee (~150 rps), the collapse past it, the bulkhead (D5.1), pool sizing (D5.2) |
| [`04-resilience-drills.md`](04-resilience-drills.md) | Chaos drills: worker crash mid-transcode, api pod crash, Postgres failover (D5.3) |

**SLOs** (carried over from the NestJS plan):

- 99.9% availability
- API p95 < 300 ms
- Video start < 2 s
- Upload → playable < 5 min
