# Angular 01 — Essentials: Components, Signals, Control Flow, RxJS

> **One-liner:** an Angular app is a tree of **standalone components**. State lives in
> **signals** (`signal`, `computed`, `effect`); data flows down through **`input()`** and
> events up through **`output()`** (or both ways with **`model()`**); templates use
> **`@if`/`@for`/`@switch`**. **RxJS** handles anything about *time*: debounce, cancel, retry,
> streams. Signals and RxJS meet at `toSignal`/`toObservable`.

**Roadmap:** task 1.11 · **Last updated:** 2026-10-02 · **Angular:** 22.2 (standalone, zoneless, Vitest)
**Code:** [`frontend/src/app/features/playground/`](../../frontend/src/app/features/playground/): `Playground` (container), `CourseCard` (inputs/outputs), `SortToggle` (`model`), `CartStore` (signal store), `course-search.ts` (RxJS pipeline), `CourseCatalog` (fake API)
**Run it:** `make web` → http://localhost:4200/playground (try typing `java`, `devops`, `error`)
**Tests:** `cd frontend && pnpm test`. The marble tests in `course-search.spec.ts` are the highlight.

**Priority marks:** ⭐⭐⭐ must know (interviews, bugs) · ⭐⭐ use daily · ⭐ good to know.

| # | Section | Priority |
|---|---|---|
| 1 | [React → Angular cheat sheet](#1-react--angular-cheat-sheet-) | ⭐⭐⭐ |
| 2 | [Standalone components and DI](#2-standalone-components-and-di-) | ⭐⭐⭐ |
| 3 | [Signals: `signal`, `computed`, `effect`](#3-signals-signal-computed-effect-) | ⭐⭐⭐ |
| 4 | [Component communication: `input`, `output`, `model`](#4-component-communication-input-output-model-) | ⭐⭐⭐ |
| 5 | [Templates and control flow](#5-templates-and-control-flow-) | ⭐⭐⭐ |
| 6 | [RxJS essentials](#6-rxjs-essentials-) | ⭐⭐⭐ |
| 7 | [The typeahead pipeline, operator by operator](#7-the-typeahead-pipeline-operator-by-operator-) | ⭐⭐⭐ |
| 8 | [Signals vs RxJS: when to use which](#8-signals-vs-rxjs-when-to-use-which-) | ⭐⭐⭐ |
| 9 | [Change detection: zoneless](#9-change-detection-zoneless-) | ⭐⭐ |
| 10 | [Testing: TestBed, inputs, stubs, marbles](#10-testing-testbed-inputs-stubs-marbles-) | ⭐⭐ |
| 11 | [Common mistakes](#11-common-mistakes-) | ⭐⭐⭐ |
| 12 | [Interview Q&A](#12-interview-qa-) | ⭐⭐⭐ |
| 13 | [30-second recall](#13-30-second-recall) | ⭐⭐⭐ |

---

## 1. React → Angular cheat sheet ⭐⭐⭐

| React | Angular (v22) | In the playground |
|---|---|---|
| function component | `@Component` class, **standalone** (no NgModule) | every component |
| JSX | an HTML template with bindings | `*.html` |
| props | `input()` / `input.required()` | `CourseCard.course` |
| callback props (`onAdd`) | `output()` + `(added)="…"` | `CourseCard.added` |
| controlled value + onChange | `model()` + `[(direction)]` | `SortToggle.direction` |
| `useState` | `signal()` | `query`, `sort` |
| `useMemo` | `computed()` | `results`, `count`, `totalMinor` |
| `useEffect` | `effect()` | the tab title |
| context / zustand store | a service (`@Service()`) holding signals | `CartStore` |
| `useContext` / hooks to get a service | `inject(Service)` | `inject(CourseCatalog)` |
| `{cond && <X/>}` | `@if (cond) { … }` | |
| `items.map(i => <X key={i.id}/>)` | `@for (i of items; track i.id) { … }` | |
| React Testing Library | `TestBed` + `fixture` (Vitest runner) | `*.spec.ts` |
| react-query / SWR | `HttpClient` + RxJS, or `httpResource` | (Phase 3+) |

The big conceptual difference is **RxJS**: Angular's HTTP client returns Observables, and
time-based logic is written as operator pipelines (§6–§7).

---

## 2. Standalone components and DI ⭐⭐⭐

```ts
@Component({
  selector: 'app-course-card',                         // the tag: <app-course-card>
  imports: [MatCardModule, MatButtonModule, CurrencyPipe],   // ⭐ what THIS template uses
  templateUrl: './course-card.html',
  styleUrl: './course-card.scss',                      // styles are scoped to this component
})
export class CourseCard { … }
```

- **Standalone:** each component lists its own template dependencies in `imports`. (NgModules
  are legacy; you'll still see them in older codebases.)
- **Lazy routes:** `loadComponent: () => import('./…/playground').then(m => m.Playground)`
  produces a separate JS chunk (the build showed `playground  124 kB`), downloaded only when the
  route is visited.
- **DI:** services are created by Angular's injector. `@Service()` (new in v22; older code uses
  `@Injectable({ providedIn: 'root' })`) makes an app-wide singleton. `inject(X)` gets one, in a
  field initialiser or constructor. It's the same idea as Spring DI (note 08), with no
  `@Autowired`.
- **Container vs presentational:**
  - Container (`Playground`): owns state, talks to services, composes children.
  - Presentational (`CourseCard`, `SortToggle`): inputs in, outputs out, no services.

  This is the same split as smart vs dumb components in React.

---

## 3. Signals: `signal`, `computed`, `effect` ⭐⭐⭐

```ts
protected readonly query = signal('');                        // writable
query();                     // READ: call it like a function
query.set('java');           // replace
query.update(q => q + '!');  // derive from the old value

protected readonly results = computed(() => {                 // derived, cached
  const state = this.state();                                 // reading a signal = depending on it
  if (state.kind !== 'ok') return [];
  return [...state.items].sort(… this.sort() …);              // also depends on sort()
});

effect(() => title.setTitle(`Masternova (${this.cart.count()} in cart)`));   // side effect
```

| Primitive | Use for | Rule |
|---|---|---|
| `signal` | state you change | update **immutably** (`[...list, item]`, not `push`): signals compare by reference |
| `computed` | values derived from other signals | pure, no side effects. Lazy and memoised: recomputes only when a dependency changed. |
| `effect` | syncing to the **outside** world (document title, localStorage, logging, a non-Angular library) | **not** for deriving state: if you `set` a signal inside an effect, you wanted `computed` |
| `.asReadonly()` | exposing state without letting others write it | `CartStore.courses` |

**Dependency tracking is automatic.** Whatever a `computed`/`effect`/template *reads* becomes a
dependency. There are no dependency arrays like React's `useEffect([...])`, so there are no stale
closures to get wrong. Use `untracked(() => …)` to read without subscribing.

**The signal store pattern** (`CartStore`): a private writable signal, public read-only views and
`computed`s, and named methods (`add`, `remove`) as the only way to change state. It's the
frontend version of note 06's encapsulated `Cart`. The same invariant ("each course once") lives
inside the store.

---

## 4. Component communication: `input`, `output`, `model` ⭐⭐⭐

```ts
// child
readonly course = input.required<CourseSummary>();   // parent MUST pass it
readonly inCart = input(false);                      // optional, with a default
readonly added = output<CourseSummary>();            // an event
added.emit(course());

readonly direction = model<SortDirection>('asc');    // two-way
direction.update(d => d === 'asc' ? 'desc' : 'asc');
```

```html
<!-- parent -->
<app-course-card [course]="course" [inCart]="cart.has(course.id)" (added)="cart.add($event)" />
<app-sort-toggle [(direction)]="sort" />    <!-- [( )] = "banana in a box": two-way -->
```

| Syntax | Direction |
|---|---|
| `[prop]="expr"` | parent → child (property binding) |
| `(event)="handler($event)"` | child → parent (event binding) |
| `[(prop)]="signal"` | both (needs `model()` in the child) |
| `{{ expr }}` | interpolation (text) |

Inputs are **signals**, so derived values recompute when the parent passes something new:
`priceRupees = computed(() => this.course().priceMinor / 100)`.

---

## 5. Templates and control flow ⭐⭐⭐

```html
@let current = state();                                  <!-- a template variable (v18.1+) -->
@switch (current.kind) {
  @case ('error') { <p role="alert">{{ current.message }}</p> }   <!-- ⭐ narrowed: .message is typed -->
  @case ('ok') {
    @for (course of results(); track course.id) {         <!-- ⭐ track is REQUIRED -->
      <app-course-card [course]="course" … />
    } @empty {
      <p>No courses match "{{ current.term }}".</p>
    }
  }
}
@if (isFree()) { Free } @else { {{ priceRupees() | currency: 'INR' }} }
```

- **`track`** tells Angular which DOM node belongs to which item, like React's `key`. Use a
  stable id, never the index for lists that change.
- **Type narrowing in templates:** `SearchState` is a discriminated union (TypeScript's version
  of a Java sealed interface, note 02). Inside `@case ('error')`, the compiler knows `message`
  exists. With strict templates (on by default), a typo fails the **build**.
- **Pipes** transform values in templates: `currency`, `date`, `async`. Pure pipes only re-run
  when their input changes.
- Legacy syntax you'll meet in older code: `*ngIf`, `*ngFor`, `[ngSwitch]` (the old structural
  directives).

---

## 6. RxJS essentials ⭐⭐⭐

An **Observable** is a stream of values over time. It's lazy: nothing happens until someone
subscribes.

| | Promise | Observable |
|---|---|---|
| Values | one | zero, one, or many |
| Starts | immediately | on `subscribe` (lazy) |
| Cancel | no | `unsubscribe` |
| Operators | `then` | `pipe(map, filter, debounceTime, switchMap, retry, …)` |

**The four flattening operators: the #1 RxJS interview question** ⭐⭐⭐

Each turns a value into an inner Observable (like an HTTP call). They differ in what happens when
a new value arrives while the previous inner one is still running:

| Operator | Behaviour | Use for |
|---|---|---|
| **`switchMap`** | **cancels** the previous inner one, keeps the newest | search / typeahead, route params: only the latest matters |
| **`mergeMap`** | runs all **in parallel** | independent fire-and-forget work (careful: results can arrive out of order) |
| **`concatMap`** | **queues**: one at a time, in order | saves that must happen in sequence |
| **`exhaustMap`** | **ignores** new values while one is running | a "Pay" button: double clicks must not double-charge |

**Other everyday operators:** `map`, `filter`, `tap` (side effects/logging), `debounceTime`,
`distinctUntilChanged`, `catchError`, `retry`, `startWith`, `combineLatest`,
`takeUntilDestroyed()` (auto-unsubscribe when the component is destroyed).

**Unsubscribing:** an Observable you `subscribe` to manually must be unsubscribed, or it leaks.
These handle it for you:

- `toSignal(obs$)` (the playground uses it);
- the `async` pipe in templates;
- `takeUntilDestroyed()`.

`HttpClient` calls complete on their own.

---

## 7. The typeahead pipeline, operator by operator ⭐⭐⭐

`course-search.ts` is a **pure function**, with no Angular inside, so it's testable with marbles:

```ts
query$.pipe(
  map(q => q.trim()),
  debounceTime(300),            // ① wait until typing pauses
  distinctUntilChanged(),       // ② "java" → "java " → "java" is one search
  switchMap(term =>             // ③ new term → cancel the old request
    term.length < 2
      ? of({ kind: 'idle' })
      : search(term).pipe(
          map(items => ({ kind: 'ok', term, items })),
          catchError(e => of({ kind: 'error', term, message: e.message })),   // ④ INSIDE switchMap
          startWith({ kind: 'loading', term }),                                // ⑤ show a spinner first
        )),
);
```

Each claim is proven by a marble test (`course-search.spec.ts`):

| Test | Proves |
|---|---|
| `debounces: only the last term…` | typing `j, ja, jav, java` 2 ms apart sends **one** request (`searched === ['java']`) |
| `switchMap cancels an outdated search…` | a slow "java" request is cancelled when "spring" arrives, so java's late result **never appears** |
| `an error becomes a state…` | after a failed search, the next one still works |
| `terms shorter than two characters…` | short terms are idle; trimmed duplicates are ignored |

⭐⭐⭐ **Why `catchError` must be inside `switchMap`:** an error *terminates* an Observable. If
`catchError` sits on the outer pipe, the first failed request ends the whole search stream, and
the search box silently stops working forever. Inside `switchMap`, only that one inner request
fails, and it's converted into an error **state**.

**Reading marble diagrams:**

| Symbol | Meaning |
|---|---|
| `-` | 1 ms of virtual time |
| a letter | a value |
| `#` | an error |
| `|` | complete |
| `16ms` | jump ahead |

In `'-l---mn'`: `l` lands at 1 ms, `m` at 5 ms, `n` at 6 ms. Inside `scheduler.run(...)`,
`debounceTime` uses this virtual clock, so the tests are exact and instant.

---

## 8. Signals vs RxJS: when to use which ⭐⭐⭐

| Use **signals** for | Use **RxJS** for |
|---|---|
| state: current values (query text, sort, cart) | events over **time**: debounce, throttle, intervals |
| derived values (`computed`) | async **coordination**: cancel, retry, race, combine requests |
| template bindings | WebSockets / SSE streams (Phase 7 upload progress) |
| simple stores | complex async flows |

**The bridge** (used in `Playground`):

```ts
state = toSignal(searchStates(toObservable(this.query), term => this.catalog.search(term)),
                 { initialValue: { kind: 'idle' } });
// signal → Observable → RxJS pipeline → back to a signal the template reads
```

---

## 9. Change detection: zoneless ⭐⭐

Classic Angular used **zone.js** to patch every browser event and timer, and re-checked the whole
component tree after each one. New Angular apps are **zoneless**: our app has no `zone.js` at
all. The framework re-renders when it's notified:

- a **signal** read by a template changes;
- a template event handler runs (`(click)`, `(input)`);
- `async`/`toSignal` emit, or `markForCheck()` is called.

**Consequence:** mutating plain fields from a `setTimeout` won't update the view. Keep UI state in
**signals** and it just works. That's why the playground holds everything in signals.

---

## 10. Testing: TestBed, inputs, stubs, marbles ⭐⭐

| Technique | Where |
|---|---|
| `TestBed.inject(Service)`, then call methods and read signals | `cart-store.spec.ts`, `course-catalog.spec.ts` |
| `TestBed.createComponent(X)` + `fixture.componentRef.setInput('course', …)` | `course-card.spec.ts` (inputs) |
| subscribe to an `output()` and click the DOM | `course-card.spec.ts` (`added.subscribe`) |
| replace a dependency: `providers: [{ provide: CourseCatalog, useValue: stub }]` | `playground.spec.ts` |
| `await fixture.whenStable()` after changes (zoneless) | all component specs |
| **marble tests** with `TestScheduler` | `course-search.spec.ts` |

Angular 21+ uses **Vitest** (`pnpm test`); older projects use Karma/Jasmine. The assertions look
almost the same.

---

## 11. Common mistakes ⭐⭐⭐

| ❌ Mistake | ✅ Instead | § |
|---|---|---|
| `items().push(x)` (mutating signal contents) | `items.update(l => [...l, x])` | 3 |
| setting signals inside `effect` to derive state | `computed` | 3 |
| making the writable signal public in a store | private `signal` + `asReadonly()` + methods | 3 |
| `@for` without a stable `track` (or tracking `$index` on a changing list) | `track item.id` | 5 |
| `mergeMap` for search | `switchMap` (stale results overwrite fresh ones with `mergeMap`) | 6 |
| `switchMap` for a payment button | `exhaustMap` (or disable the button) | 6 |
| `catchError` on the outer pipe | `catchError` inside the flattening operator | 7 |
| manual `subscribe` without cleanup | `toSignal`, the `async` pipe, `takeUntilDestroyed` | 6 |
| a component calling `HttpClient` directly everywhere | a service per API area (`core/api/…`) | 2 |
| business logic in templates | `computed` in the class | 3, 5 |
| updating plain fields in callbacks (zoneless) | signals | 9 |
| testing debounce with real timers everywhere | marble tests for pipelines; one real-time test at most | 10 |

---

## 12. Interview Q&A ⭐⭐⭐

**Q1. What are signals? How do they differ from RxJS Observables?**
Signals are synchronous reactive values with automatic dependency tracking: `signal`,
`computed`, `effect`, read by calling them. Observables are lazy, possibly asynchronous streams
over time, with operators and cancellation. Use signals for state and RxJS for time-based and
async coordination; bridge them with `toSignal`/`toObservable`.

**Q2. `computed` vs `effect`?**
`computed` derives a value and is pure, lazy and cached. `effect` runs side effects when its
dependencies change. Deriving state in an effect is an anti-pattern.

**Q3. `switchMap` vs `mergeMap` vs `concatMap` vs `exhaustMap`?**
Cancel the previous inner Observable / run all in parallel / queue in order / ignore new values
while busy. Typeahead, independent tasks, ordered saves, submit buttons.

**Q4. How do you implement a typeahead search?**
`debounceTime` → `distinctUntilChanged` → `switchMap` to the HTTP call, with `catchError` inside
the `switchMap`, and `startWith` for a loading state.

**Q5. Why does an error kill an RxJS stream, and how do you prevent it?**
An `error` notification terminates the Observable. Catch it on the inner Observable, inside the
flattening operator, and turn it into a value.

**Q6. `input()` vs `@Input()`? What does `model()` do?**
`input()` is the signal-based input (reactive, required/optional typing); `@Input()` is the older
decorator. `model()` is a writable input that also emits changes, which enables `[(x)]`
two-way binding.

**Q7. What is zoneless change detection?**
Angular runs without zone.js and re-renders when signals change, template events fire or async
values arrive. You need signal-based state, and you get faster, more predictable rendering.

**Q8. Why must `@for` have `track`?**
So Angular can match DOM nodes to items across changes and move or keep nodes instead of
re-creating them, like React's `key`.

**Q9. How do you avoid memory leaks with Observables?**
Prefer `toSignal`/the `async` pipe. Otherwise use `takeUntilDestroyed()`. HTTP Observables
complete on their own.

**Q10. How do you test RxJS timing logic?**
Marble tests with `TestScheduler.run`, using virtual time: exact, fast, and covering debounce
and cancellation.

---

## 13. 30-second recall

- **Components:** standalone, each with its own `imports`. Lazy routes with `loadComponent`.
  `inject()` for DI; `@Service()` for singletons.
- **Signals:**
  - `signal` (state, updated immutably), `computed` (derived, cached), `effect` (outside world
    only).
  - Dependencies are tracked automatically.
  - Store pattern: a private writable signal, a public read-only view, and methods.
- **Component communication:** `input()` / `input.required()` down, `output()` up, `model()` +
  `[(x)]` for two-way binding.
- **Templates:** `@if`, `@for (…; track id) … @empty`, `@switch` narrows discriminated unions,
  `@let`. Strict templates catch typos at build time.
- **RxJS:**
  - Lazy, cancellable streams.
  - `switchMap` (latest), `mergeMap` (parallel), `concatMap` (queue), `exhaustMap` (ignore while
    busy).
  - `catchError` goes inside the flattening operator.
- **Typeahead:** `trim` → `debounceTime` → `distinctUntilChanged` → `switchMap(search…catchError…startWith(loading))`.
- **Signals or RxJS?** Signals for state, RxJS for time; bridge with `toSignal`/`toObservable`.
- **Zoneless:** keep UI state in signals.
- **Tests:** Vitest + TestBed, `setInput`, stub providers, marble tests.
- **Next:** Phase 3 (identity + Angular shell) adds routing guards, interceptors and typed reactive forms.
