import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { of } from 'rxjs';
import { course } from '../../../testing/authoring-fixtures';
import { CourseEditorStore } from './course-editor-store';

describe('CourseEditorStore', () => {
  let store: CourseEditorStore;
  let http: HttpTestingController;
  const open = vi.fn(() => ({ afterClosed: () => of(true) }));
  const base = '/api/v1/instructor/courses/c1';

  beforeEach(() => {
    open.mockClear();
    TestBed.configureTestingModule({
      providers: [
        CourseEditorStore, // ⭐ not auto-provided: whoever uses it must provide it
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MatDialog, useValue: { open } },
      ],
    });
    store = TestBed.inject(CourseEditorStore);
    http = TestBed.inject(HttpTestingController);
    store.load('c1');
    http.expectOne(base).flush(course({ version: 4 }));
    http.expectOne(`${base}/curriculum`).flush({
      version: 4,
      canUndo: true,
      canRedo: false,
      lectureCount: 0,
      totalDurationSeconds: 0,
      sections: [],
    });
  });

  afterEach(() => http.verify());

  it('sends the shared version and takes the new one from every answer', () => {
    store.apply({ kind: 'ADD_SECTION', title: 'Intro' });
    const req = http.expectOne(`${base}/curriculum`);
    expect(req.request.body.expectedVersion).toBe(4);
    req.flush({
      version: 5,
      canUndo: true,
      canRedo: false,
      lectureCount: 0,
      totalDurationSeconds: 0,
      sections: [],
    });

    store.saveDetails({
      title: 'T',
      subtitle: null,
      description: 'D',
      categorySlug: 'ci-cd',
      level: 'BEGINNER',
      language: 'en',
    });
    // ⭐ the details form uses the version the CURRICULUM edit produced
    expect(http.expectOne(`${base}/details`).request.body.expectedVersion).toBe(5);
  });

  it('shows a conflict dialog on 409 and reloads instead of retrying', () => {
    store.undo();
    http
      .expectOne(`${base}/curriculum/undo`)
      .flush({ status: 409, code: 'VERSION_CONFLICT' }, { status: 409, statusText: 'Conflict' });

    expect(store.saveState()).toBe('conflict');
    expect(open).toHaveBeenCalledTimes(1);
    http.expectOne(base).flush(course({ version: 6 })); // the reload
    http.expectOne(`${base}/curriculum`).flush({
      version: 6,
      canUndo: true,
      canRedo: false,
      lectureCount: 0,
      totalDurationSeconds: 0,
      sections: [],
    });
    expect(store.version()).toBe(6);
  });

  /** ⭐ Two quick edits from ONE tab: the second waits, then sends the version the first produced. */
  it('queues writes so quick edits never conflict with each other', () => {
    store.apply({ kind: 'ADD_SECTION', title: 'One' });
    store.apply({ kind: 'ADD_SECTION', title: 'Two' });

    const first = http.expectOne(`${base}/curriculum`); // only ONE request in flight
    expect(first.request.body.expectedVersion).toBe(4);
    first.flush({
      version: 5,
      canUndo: true,
      canRedo: false,
      lectureCount: 0,
      totalDurationSeconds: 0,
      sections: [],
    });

    const second = http.expectOne(`${base}/curriculum`);
    expect(second.request.body).toEqual({
      expectedVersion: 5,
      command: { kind: 'ADD_SECTION', title: 'Two' },
    });
    second.flush({
      version: 6,
      canUndo: true,
      canRedo: false,
      lectureCount: 0,
      totalDurationSeconds: 0,
      sections: [],
    });
  });

  it('drops queued writes after a conflict instead of 409-ing again', () => {
    store.apply({ kind: 'ADD_SECTION', title: 'One' });
    store.apply({ kind: 'ADD_SECTION', title: 'Two' });
    http
      .expectOne(`${base}/curriculum`)
      .flush({ status: 409, code: 'VERSION_CONFLICT' }, { status: 409, statusText: 'Conflict' });

    // the reload is what's sent next — not the queued "Two"
    http.expectOne(base).flush(course({ version: 9 }));
    http.expectOne(`${base}/curriculum`).flush({
      version: 9,
      canUndo: true,
      canRedo: false,
      lectureCount: 0,
      totalDurationSeconds: 0,
      sections: [],
    });
    expect(open).toHaveBeenCalledTimes(1);
  });

  it('does nothing when there is nothing to redo', () => {
    store.redo();
    http.expectNone(`${base}/curriculum/redo`);
  });
});
