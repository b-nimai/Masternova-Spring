# High-level design

| File | Holds |
|---|---|
| [`01-architecture.md`](01-architecture.md) | C4 context, containers, Modulith modules, cross-cutting decisions, deployment views |
| `02-data-flows.md` | Written incrementally: each module phase adds its flow (signup, upload → playable, checkout → enroll …) |
| `03-capacity.md` | Phase D5: k6 results against the SLOs |

**SLOs** (carried over from the NestJS plan):

- 99.9% availability
- API p95 < 300 ms
- Video start < 2 s
- Upload → playable < 5 min
