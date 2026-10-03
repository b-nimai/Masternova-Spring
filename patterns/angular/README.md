# Angular study notes

One detailed note per topic, each paired with **real, tested code** in `frontend/src/app/`.

Priority marks: **⭐⭐⭐** must know · **⭐⭐** use daily · **⭐** good to know.

| # | Topic | Note | Code | Roadmap | Status |
|---|---|---|---|---|---|
| 01 | Essentials: standalone components, signals, `input`/`output`/`model`, control flow, RxJS (flattening operators, typeahead), zoneless, testing with marbles | [01-angular-essentials.md](01-angular-essentials.md) | `features/playground/` (route `/playground`) | 1.11 | ✅ |
| 02 | Routing (lazy routes, URL → input binding), guards (`CanMatch`, `UrlTree`), app initializer, functional interceptor + single-flight refresh, token storage, typed reactive forms, custom + cross-field validators, Problem Details → form errors, SPA security (open redirect) | [02-routing-guards-interceptors-forms.md](02-routing-guards-interceptors-forms.md) | `core/auth/`, `features/auth/`, `features/account/`, `features/admin/` (routes `/login`, `/signup`, `/account`, `/admin`) | 3.7–3.10 | ✅ |
| 03 | Optimistic UI & server state: optimistic update + rollback, request races, immutable signal updates, load-state unions, pages that must not act on load (email links), testing Material via ARIA | [03-optimistic-ui-and-server-state.md](03-optimistic-ui-and-server-state.md) | `core/api/notification-api.ts`, `features/account/notifications/`, `features/unsubscribe/` (routes `/account/notifications`, `/unsubscribe`) | 4.7 | ✅ |
| 04 | URL as state (query params → inputs, controls write the URL), cursor infinite scroll as one RxJS pipeline (`switchMap` / `exhaustMap` / `scan`), CDK virtual scroll, `rxResource`, `@defer (on viewport)`, money pipe | [04-url-state-infinite-scroll-defer.md](04-url-state-infinite-scroll-defer.md) | `core/api/catalog-api.ts`, `features/catalog/`, `shared/` (routes `/courses`, `/courses/:slug`) | 5.8 | ✅ |
| 05 | Wizards & editors: component-scoped signal store (one shared version), typed reactive forms, autosave with `debounceTime` + `concatMap` (why not `switchMap`), patch without saving, 409 → dialog + reload, `MatStepper`, CDK drag-drop → commands, server-side undo + keyboard shortcuts, idempotent create | [05-wizard-autosave-drag-drop.md](05-wizard-autosave-drag-drop.md) | `core/api/authoring-api.ts`, `features/instructor/` (routes `/instructor`, `/instructor/courses/new`, `/instructor/courses/:id/edit`) | 6.7–6.8 | ✅ |

**Run the code:** `make web` → http://localhost:4200/playground · **Tests:** `cd frontend && pnpm test`
