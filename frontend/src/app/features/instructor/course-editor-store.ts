import { HttpErrorResponse } from '@angular/common/http';
import { inject, Service, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatDialog } from '@angular/material/dialog';
import { catchError, concatMap, EMPTY, forkJoin, Observable, Subject, tap } from 'rxjs';
import {
  AuthoringApi,
  AuthorAction,
  CourseDetails,
  CurriculumCommand,
  CurriculumResponse,
  ReadinessResponse,
} from '../../core/api/authoring-api';
import { CourseDetailResponse } from '../../core/api/catalog-api';
import { asProblem } from '../../core/api/problem';
import { ConflictDialog } from './conflict-dialog/conflict-dialog';

export type SaveState = 'idle' | 'saving' | 'saved' | 'conflict' | 'error';

/**
 * The state of ONE course being edited, shared by the wizard's steps and the curriculum editor.
 *
 * ⭐ Scoped to the wizard (`autoProvided: false` + the wizard's `providers`): two open wizards would
 *    get two stores. ⭐ It holds the ONE `version` every write sends back as `expectedVersion`: the
 *    details form, the pricing step and the curriculum editor all move the same course's version
 *    (the root's version covers the aggregate, ADR-0010), so they must share it.
 * ⭐ ALL writes go through ONE queue (`concatMap`): a write starts only after the previous one has
 *    answered, so it sends the version that answer produced. Without the queue, two quick edits
 *    from one tab sent the same version and the second got a false 409 (found in review).
 */
@Service({ autoProvided: false })
export class CourseEditorStore {
  private readonly api = inject(AuthoringApi);
  private readonly dialog = inject(MatDialog);

  readonly course = signal<CourseDetailResponse | null>(null);
  readonly curriculum = signal<CurriculumResponse | null>(null);
  readonly readiness = signal<ReadinessResponse | null>(null);
  readonly version = signal(0);
  readonly saveState = signal<SaveState>('idle');
  /** Bumped on every (re)load: forms re-read the course then, and ONLY then (not after saves). */
  readonly loads = signal(0);
  private courseId = '';

  private readonly writes = new Subject<() => Observable<unknown>>();

  constructor() {
    this.writes
      .pipe(
        // ⭐ concatMap calls each write only when its turn comes, so the write reads the version
        //    the previous answer set. After a conflict, queued writes are dropped until the reload.
        concatMap((write) =>
          this.saveState() === 'conflict' ? EMPTY : write().pipe(catchError((e) => this.fail(e))),
        ),
        takeUntilDestroyed(),
      )
      .subscribe();
  }

  load(courseId: string): void {
    this.courseId = courseId;
    forkJoin([this.api.get(courseId), this.api.curriculum(courseId)]).subscribe(
      ([course, curriculum]) => {
        this.course.set(course);
        this.curriculum.set(curriculum);
        this.version.set(curriculum.version);
        this.saveState.set('idle');
        this.loads.update((n) => n + 1);
      },
    );
  }

  reload(): void {
    this.load(this.courseId);
  }

  saveDetails(details: CourseDetails): void {
    this.enqueue(() =>
      this.api
        .updateDetails(this.courseId, this.version(), details)
        .pipe(tap((course) => this.acceptCourse(course))),
    );
  }

  confirmPrice(priceMinor: number): void {
    this.enqueue(() =>
      this.api
        .confirmPrice(this.courseId, this.version(), priceMinor)
        .pipe(tap((course) => this.acceptCourse(course))),
    );
  }

  transition(action: AuthorAction): void {
    this.enqueue(() =>
      this.api.transition(this.courseId, action).pipe(
        tap((course) => {
          this.acceptCourse(course);
          this.loadReadiness();
        }),
      ),
    );
  }

  apply(command: CurriculumCommand): void {
    this.enqueue(() =>
      this.curriculumWrite(this.api.apply(this.courseId, this.version(), command)),
    );
  }

  undo(): void {
    if (this.curriculum()?.canUndo) {
      this.enqueue(() => this.curriculumWrite(this.api.undo(this.courseId, this.version())));
    }
  }

  redo(): void {
    if (this.curriculum()?.canRedo) {
      this.enqueue(() => this.curriculumWrite(this.api.redo(this.courseId, this.version())));
    }
  }

  loadReadiness(): void {
    this.api.readiness(this.courseId).subscribe((r) => this.readiness.set(r));
  }

  /** Optimistic local edit (a drag): shown now; the server's answer replaces it. */
  showLocally(curriculum: CurriculumResponse): void {
    this.curriculum.set(curriculum);
  }

  private enqueue(write: () => Observable<unknown>): void {
    this.saveState.set('saving');
    this.writes.next(write);
  }

  private curriculumWrite(request: Observable<CurriculumResponse>): Observable<CurriculumResponse> {
    return request.pipe(
      tap((curriculum) => {
        this.curriculum.set(curriculum);
        this.version.set(curriculum.version);
        this.saveState.set('saved');
      }),
    );
  }

  private acceptCourse(course: CourseDetailResponse): void {
    this.course.set(course);
    this.version.set(course.version);
    this.saveState.set('saved');
  }

  /**
   * ⭐ 409 VERSION_CONFLICT: another tab (or person) saved first. Never retry with a fresh version
   * behind the user's back — that would overwrite the other edit. Show the conflict, then reload.
   */
  private fail(error: unknown): Observable<never> {
    if (error instanceof HttpErrorResponse && asProblem(error)?.code === 'VERSION_CONFLICT') {
      this.saveState.set('conflict');
      this.dialog
        .open(ConflictDialog, { disableClose: true })
        .afterClosed()
        .subscribe(() => this.reload());
    } else {
      this.saveState.set('error');
      this.reload(); // an optimistic change the server refused must not stay on screen
    }
    return EMPTY; // ⭐ the queue keeps running for the writes that follow
  }
}
