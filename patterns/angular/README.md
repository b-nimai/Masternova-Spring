# Angular study notes

One detailed note per topic, each paired with **real, tested code** in `frontend/src/app/`.

Priority marks: **⭐⭐⭐** must know · **⭐⭐** use daily · **⭐** good to know.

| # | Topic | Note | Code | Roadmap | Status |
|---|---|---|---|---|---|
| 01 | Essentials: standalone components, signals, `input`/`output`/`model`, control flow, RxJS (flattening operators, typeahead), zoneless, testing with marbles | [01-angular-essentials.md](01-angular-essentials.md) | `features/playground/` (route `/playground`) | 1.11 | ✅ |
| 02 | Routing (lazy routes, URL → input binding), guards (`CanMatch`, `UrlTree`), app initializer, functional interceptor + single-flight refresh, token storage, typed reactive forms, custom + cross-field validators, Problem Details → form errors, SPA security (open redirect) | [02-routing-guards-interceptors-forms.md](02-routing-guards-interceptors-forms.md) | `core/auth/`, `features/auth/`, `features/account/`, `features/admin/` (routes `/login`, `/signup`, `/account`, `/admin`) | 3.7–3.10 | ✅ |
| 03 | Optimistic UI & server state: optimistic update + rollback, request races, immutable signal updates, load-state unions, pages that must not act on load (email links), testing Material via ARIA | [03-optimistic-ui-and-server-state.md](03-optimistic-ui-and-server-state.md) | `core/api/notification-api.ts`, `features/account/notifications/`, `features/unsubscribe/` (routes `/account/notifications`, `/unsubscribe`) | 4.7 | ✅ |
| 04 | Material in depth: stepper, dialog, table, CDK drag-drop, virtual scroll | — | Phases 5–6 | 5.8, 6.7–6.8 | ☐ |

**Run the code:** `make web` → http://localhost:4200/playground · **Tests:** `cd frontend && pnpm test`
