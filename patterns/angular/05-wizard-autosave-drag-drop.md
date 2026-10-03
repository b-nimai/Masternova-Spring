# 05 — Wizards, autosave, optimistic concurrency, drag-drop and undo in Angular

> **One-liner:** a multi-step editor that autosaves with **typed reactive forms** and
> `debounceTime` + **`concatMap`** (never `switchMap` for writes), shares one **version** through a
> **component-scoped signal store**, turns every drag into a **command**, and treats a 409 as "show
> the conflict and reload", never "retry with a newer version".

**Roadmap:** tasks 6.7–6.8 · **Last updated:** 2026-10-03 · **Prev:** [04 — URL state & infinite scroll](04-url-state-infinite-scroll-defer.md)
**Code:** [`features/instructor/`](../../frontend/src/app/features/instructor/): `course-editor-store.ts`, `course-wizard/`, `curriculum-editor/`, `conflict-dialog/`, `new-course/`, `instructor-courses/` · [`core/api/authoring-api.ts`](../../frontend/src/app/core/api/authoring-api.ts)
**Tests:** the `instructor/**/*.spec.ts` specs, and [`e2e/tests/authoring.spec.ts`](../../e2e/tests/authoring.spec.ts) (the whole flow plus two real tabs)
**Backend it talks to:** [`docs/lld/catalog-authoring.md`](../../docs/lld/catalog-authoring.md), ADR-0010 (versions), ADR-0011 (undo)

| # | Section | Priority |
|---|---|---|
| 1 | [A component-scoped store](#1-a-component-scoped-store-) | ⭐⭐⭐ |
| 2 | [Typed reactive forms](#2-typed-reactive-forms-) | ⭐⭐ |
| 3 | [Autosave: debounce, then `concatMap`](#3-autosave-debounce-then-concatmap-) | ⭐⭐⭐ |
| 4 | [Filling a form without saving it](#4-filling-a-form-without-saving-it-) | ⭐⭐ |
| 5 | [409: show, reload, never retry](#5-409-show-reload-never-retry-) | ⭐⭐⭐ |
| 6 | [`MatStepper`](#6-matstepper-) | ⭐⭐ |
| 7 | [CDK drag-drop → commands](#7-cdk-drag-drop--commands-) | ⭐⭐⭐ |
| 8 | [Undo/redo and keyboard shortcuts](#8-undoredo-and-keyboard-shortcuts-) | ⭐⭐ |
| 9 | [Idempotent create](#9-idempotent-create-) | ⭐⭐ |
| 10 | [Testing it](#10-testing-it-) | ⭐⭐⭐ |
| 11 | [Common mistakes](#11-common-mistakes-) | ⭐⭐⭐ |
| 12 | [Interview Q&A](#12-interview-qa) | ⭐⭐⭐ |
| 13 | [30-second recall](#13-30-second-recall) | ⭐⭐⭐ |

---

## 1. A component-scoped store ⭐⭐⭐

Three parts of the wizard write to the same course: the details form, the pricing step and the
curriculum editor. On the server **every** write moves the **same** `version` (the root's version
covers the aggregate, ADR-0010). So the client must hold one version, shared by all three:

```ts
@Service({ autoProvided: false })      // ⭐ NOT a global singleton…
export class CourseEditorStore {
  readonly course = signal<CourseDetailResponse | null>(null);
  readonly curriculum = signal<CurriculumResponse | null>(null);
  readonly version = signal(0);         // the ONE token every write sends as expectedVersion
  readonly saveState = signal<SaveState>('idle');
  …
}

@Component({
  providers: [CourseEditorStore],        // …one instance PER WIZARD, created with it, destroyed with it
  …
})
export class CourseWizard { protected readonly store = inject(CourseEditorStore); }

// the curriculum editor, rendered inside the wizard, injects the SAME instance (DI walks up the tree)
export class CurriculumEditor { protected readonly store = inject(CourseEditorStore); }
```

| Scope | How | Use for |
|---|---|---|
| app-wide | `@Service()` (auto-provided in root) | auth, API clients |
| ⭐ one feature instance | `@Service({ autoProvided: false })` + the component's `providers` | the state of *this* edited course |
| one component | plain signals in the component | local UI state |

Why it matters: a details save returns version 8; the next curriculum command must send 8. Two
separate pieces of state would each keep their own copy of the version, and the second write would 409 against
yourself (`course-editor-store.spec`: "the details form uses the version the curriculum edit
produced").

## 2. Typed reactive forms ⭐⭐

```ts
type DetailsForm = FormGroup<{
  title: FormControl<string>;
  level: FormControl<CourseLevel>;   // ⭐ the union type, not string
  …
}>;

protected readonly details: DetailsForm = new FormGroup({
  title: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(120)] }),
  level: new FormControl<CourseLevel>('BEGINNER', { nonNullable: true }),
  …
});

const v = this.details.getRawValue();   // { title: string; level: CourseLevel; … } — no `any`
```

`nonNullable: true` makes `reset()` go back to the initial value instead of `null`, so the value
type has no `| null`. The client validators mirror the server's (`@NotBlank @Size(max = 120)`) for
fast feedback; the server stays the authority.

## 3. Autosave: debounce, then `concatMap` ⭐⭐⭐

```ts
this.details.valueChanges.pipe(
  debounceTime(800),                                   // wait for a pause in typing
  filter(() => this.details.valid),                    // never send an invalid state
  map(() => this.toDetails()),
  distinctUntilChanged((a, b) => JSON.stringify(a) === JSON.stringify(b)),  // no no-op PUTs
  concatMap((details) => this.store.saveDetails(details)),                  // ⭐ in ORDER
  takeUntilDestroyed(),
).subscribe();
```

**Why not `switchMap`** (the operator for searches)? `switchMap` cancels the previous inner
observable, i.e. it stops *listening* for the HTTP response. The PUT already reached the server, and
the server applied it and bumped the version. The client never learns the new version, so the
**next** save sends a stale one and gets 409 against its own earlier save.

| Operator | For | Here |
|---|---|---|
| `switchMap` | reads (only the latest answer matters) | ❌ a cancelled write still happened |
| ⭐ `concatMap` | writes that must apply in order | ✅ each save starts after the previous answered, reading the newest `version()` |
| `exhaustMap` | ignore new triggers while busy (a submit button) | ❌ would drop the user's latest text |
| `mergeMap` | independent parallel work | ❌ two PUTs with the same version → one 409 |

**One queue for every write (found in review).** Autosave alone wasn't enough: a curriculum command,
an undo and a details save could run *in parallel*, all sending version N; the second to land got a
409 against its own tab's first. So the store has ONE write queue, and every write goes through it:

```ts
private readonly writes = new Subject<() => Observable<unknown>>();

constructor() {
  this.writes.pipe(
    // ⭐ concatMap calls the thunk only when its turn comes → it reads the version the
    //    previous answer set. After a conflict, queued writes are dropped until the reload.
    concatMap((write) => this.saveState() === 'conflict' ? EMPTY : write().pipe(catchError((e) => this.fail(e)))),
    takeUntilDestroyed(),
  ).subscribe();
}

apply(command: CurriculumCommand): void {
  this.enqueue(() => this.curriculumWrite(this.api.apply(this.courseId, this.version(), command)));
}
```

The queue holds **thunks** (`() => Observable`), not observables: `this.version()` must be read when
the write *runs*, not when it was queued. The wizard's autosave just hands each debounced value to
`store.saveDetails`.

## 4. Filling a form without saving it ⭐⭐

Loading the course must fill the form **without** triggering an autosave, and our own saves must
**not** re-fill it (that would fight the user's typing):

```ts
effect(() => {
  this.store.loads();                 // ⭐ the ONLY trigger: bumped on (re)load, not on saves
  const course = untracked(() => this.store.course());   // ⭐ read, but don't depend on it
  if (course) {
    this.details.setValue({ … }, { emitEvent: false });   // no valueChanges → no autosave
  }
});
```

**`untracked` matters (found in review).** Reading `this.store.course()` normally makes it a
dependency, so the effect re-ran after **every save** and put the saved (older) text back over what
the user had typed while the PUT was in flight. Inside `untracked(...)`, the read doesn't subscribe.
The spec "keeps what the user typed while a save was in flight" fails without it.

## 5. 409: show, reload, never retry ⭐⭐⭐

```ts
private fail(error: unknown): Observable<never> {
  if (error instanceof HttpErrorResponse && asProblem(error)?.code === 'VERSION_CONFLICT') {
    this.saveState.set('conflict');
    this.dialog.open(ConflictDialog, { disableClose: true })
      .afterClosed().subscribe(() => this.reload());     // the human sees the newer state
  } else {
    this.saveState.set('error');
    this.reload();                                      // undo any optimistic change
  }
  return EMPTY;                                         // ⭐ the autosave stream keeps running
}
```

A 409 means **someone else's edit is newer**. Automatically retrying with the current version would
overwrite that edit, which is the lost update the version exists to prevent. The only correct move is to
show it and let the person redo their change on top. `catchError` returning `EMPTY` (inside the
inner observable) keeps the outer autosave pipeline alive; an error escaping to the outer stream
would end autosave for the rest of the session.

The e2e test proves it with **two real tabs**: tab B saves, tab A (stale) types, the dialog appears,
"Reload latest" shows tab B's title, nothing was overwritten.

## 6. `MatStepper` ⭐⭐

```html
<mat-stepper (selectionChange)="onStep($event)">
  <mat-step label="Details">…<button mat-flat-button matStepperNext>Next: pricing</button></mat-step>
  <mat-step label="Pricing">…</mat-step>
  <mat-step label="Curriculum"><app-curriculum-editor /></mat-step>
  <mat-step label="Review & submit">…checklist…</mat-step>
</mat-stepper>
```

- Not `linear`: an existing course can be edited in any order; the **server's publish gate**
  decides readiness, and the last step shows its checklist (`GET …/readiness`).
- `selectionChange` reloads the checklist when the review step opens, so it reflects edits made in
  the other steps.
- The first step (creating the course) is a separate page, `/instructor/courses/new`: the wizard
  needs a course id (and a version) to exist before anything can autosave.

## 7. CDK drag-drop → commands ⭐⭐⭐

```html
<div cdkDropListGroup>                                  <!-- every list inside is connected -->
  @for (section of sections(); track section.id) {
    <ul cdkDropList [cdkDropListData]="section" (cdkDropListDropped)="dropLecture($event)">
      @for (lecture of section.lectures; track lecture.id) {
        <li cdkDrag [cdkDragData]="lecture"><mat-icon cdkDragHandle>drag_indicator</mat-icon>…</li>
      }
    </ul>
  }
</div>
```

```ts
dropLecture(event: CdkDragDrop<SectionResponse, SectionResponse, LectureResponse>): void {
  const from = event.previousContainer.data, to = event.container.data, lecture = event.item.data;
  if (from.id === to.id && event.previousIndex === event.currentIndex) return;
  // ⭐ optimistic and IMMUTABLE: a new curriculum object, shown now
  this.store.showLocally({ ...current, sections: current.sections.map(/* move it */) });
  this.store.apply({ kind: 'MOVE_LECTURE', lectureId: lecture.id, toSectionId: to.id, toPosition: event.currentIndex });
}
```

- `cdkDropListGroup` connects all lecture lists, so a lecture can move **across** sections.
- The drop becomes **one command** (API conventions §6); the server's answer (the whole
  curriculum, new version, `canUndo`) replaces the optimistic picture. If the server refuses, the
  store reloads the truth.
- Use `[cdkDropListData]`/`[cdkDragData]` to carry the model, not DOM indexes.
- **Sections reorder with ↑/↓ buttons**, not drag: nested drop lists (sections containing lecture
  lists) are fiddly, and buttons are keyboard- and screen-reader-friendly. Each sends
  `REORDER_SECTIONS` with the **whole** order.

## 8. Undo/redo and keyboard shortcuts ⭐⭐

Undo lives on the **server** (the `course_edit` history, ADR-0011), so it survives a reload and
works from another tab. The UI only calls it, and enables the buttons from `canUndo`/`canRedo`:

```ts
@Component({ host: { '(document:keydown)': 'onKeydown($event)' }, … })   // host listener, no @HostListener
onKeydown(event: KeyboardEvent): void {
  const target = event.target;                                          // may be the document itself
  if (target instanceof Element && target.closest('input, textarea, [contenteditable="true"]')) {
    return;                                    // ⭐ inside a text field, Ctrl+Z undoes the TEXT
  }
  if (!(event.ctrlKey || event.metaKey)) return;                       // ⌘ on macOS
  const key = event.key.toLowerCase();
  if (key === 'z' && !event.shiftKey) { event.preventDefault(); this.store.undo(); }
  else if ((key === 'z' && event.shiftKey) || key === 'y') { event.preventDefault(); this.store.redo(); }
}
```

Found by a spec: an event dispatched on `document` has the `Document` as its target, which has no
`closest()`; hence `instanceof Element`.

**Only on the curriculum step (found in review).** The stepper keeps every step's component alive,
so the editor's document listener also fired on the Details step: Ctrl+Z there silently undid a
curriculum edit. The editor has `shortcutsEnabled = input(true)`; the wizard binds it to
`stepper.selectedIndex === 2`.

## 9. Idempotent create ⭐⭐

```ts
private idempotencyKey = crypto.randomUUID();   // ⭐ ONE per ATTEMPT, not per click
…error: () => { this.idempotencyKey = crypto.randomUUID(); … }   // a refused attempt is over
```

Creating has no version to guard it, so the API requires an `Idempotency-Key`. One key per attempt
means a double-click or a retry after a timeout sends the **same** key, and the server replays the
first 201 instead of creating a second course. But the server also stores a **4xx** answer under the
key: reusing it for the corrected form would get 422 `IDEMPOTENCY_KEY_REUSED` forever. After a
refused attempt, the next submit is a new request with a new key (found in review).

## 10. Testing it ⭐⭐⭐

| What | How |
|---|---|
| debounced autosave | fake timers after the page is stable; type 3 values 200 ms apart; `advanceTimersByTime(800)`; assert **one** PUT with the current version |
| a conflict | flush a 409 problem; assert the dialog opened (a `MatDialog` stub), then flush the reload and assert the form shows the newer data |
| a store not auto-provided | list it in the test's `providers` (or the component's, overridden with `overrideComponent`) |
| drag-drop | call `dropLecture` with a hand-made `CdkDragDrop` (`item.data`, `container.data`, indexes); assert the optimistic DOM and the command body. Real dragging is the e2e's job |
| keyboard | `document.dispatchEvent(new KeyboardEvent('keydown', { key: 'z', ctrlKey: true }))`, and the same from inside an input (`bubbles: true`) to prove it's ignored |
| the whole flow | Playwright as the seeded instructor; two `context.newPage()` tabs for the conflict |

## 11. Common mistakes ⭐⭐⭐

| Mistake | Symptom | Fix |
|---|---|---|
| `switchMap` for autosave | random 409s against your own saves | `concatMap` |
| each step holding its own version | the second step's save 409s | one store, one `version` signal |
| writes from different steps in parallel | false 409s against your own tab | one write queue of thunks (`concatMap`) |
| an effect that reads the state it then overwrites | typed text reverts after each save | read it inside `untracked` |
| the same Idempotency-Key after a 4xx | 422 `IDEMPOTENCY_KEY_REUSED` on every retry | a new key per attempt |
| a document key listener on a hidden step | shortcuts act on screens you can't see | enable them only for the visible step |
| auto-retrying a 409 with the newer version | silently overwrites the other tab | dialog + reload |
| re-patching the form after every save | the cursor jumps / text reverts while typing | patch only on (re)load, `emitEvent: false` |
| `catchError` on the OUTER autosave stream | the first error ends autosave for good | catch inside the inner (per-save) observable |
| a global singleton for per-course state | two open courses share one version | `autoProvided: false` + component providers |
| new Idempotency-Key per click | double-click creates two courses | one key per form |
| Ctrl+Z hijacked inside inputs | users can't undo typing | ignore events from editable targets |
| moving list items by mutating arrays | signals/OnPush don't see it | new arrays and objects |

## 12. Interview Q&A

- **Q:** How do you implement autosave correctly?
  **A:** Debounce the form's value changes, skip invalid and unchanged values, and send saves
  **sequentially** (`concatMap`) with the latest version, so they apply in order. `switchMap` is wrong
  for writes: cancelling the request doesn't cancel the server-side write.
- **Q:** Two tabs edit the same thing. What happens?
  **A:** Each write carries the version it was based on; the stale one gets 409. The UI shows a
  conflict dialog and reloads; it never retries automatically, which would overwrite the other edit.
- **Q:** Where do you keep state shared by several components of one screen?
  **A:** In a service provided by the screen's root component (`providers: [...]`,
  `autoProvided: false`): one instance per screen, destroyed with it, injected by the children.
- **Q:** How does drag-and-drop talk to the backend?
  **A:** The drop event carries the model (`cdkDragData`/`cdkDropListData`); it becomes one command
  (`MOVE_LECTURE` with target and index). The UI updates optimistically and accepts the server's
  answer as the truth.
- **Q:** Client-side or server-side undo?
  **A:** Server-side here: the history must survive reloads, other tabs and other replicas, and an
  undo is a write that must respect the version like any other.

## 13. 30-second recall

- **Store:** `@Service({ autoProvided: false })` + `providers` on the wizard → one per edited
  course; holds the **one version** all writes send.
- **Autosave:** `debounceTime → filter(valid) → distinctUntilChanged → concatMap(save)`; save
  reads `version()` at request time; never `switchMap` for writes.
- **Load ≠ edit:** patch with `emitEvent: false`, only on (re)loads.
- **409:** dialog + reload, never retry; `catchError` inside the inner stream.
- **Drag-drop:** `cdkDropListGroup`, data-carrying lists/items, one `MOVE_LECTURE`, optimistic +
  server truth; sections reorder by buttons (a11y) with the whole order.
- **Undo:** server history; buttons from `canUndo`/`canRedo`; Ctrl+Z ignored inside inputs.
- **Create:** one Idempotency-Key per form.
