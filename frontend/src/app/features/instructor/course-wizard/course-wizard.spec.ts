import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { course, curriculum } from '../../../../testing/authoring-fixtures';
import { query, text } from '../../../../testing/dom';
import { CourseWizard } from './course-wizard';

describe('CourseWizard', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<CourseWizard>;
  const base = '/api/v1/instructor/courses/c1';
  const open = vi.fn(() => ({ afterClosed: () => of(true) }));

  beforeEach(async () => {
    open.mockClear();
    await TestBed.configureTestingModule({
      imports: [CourseWizard],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: MatDialog, useValue: { open } },
      ],
    })
      // the wizard provides its own store; replace only the dialog it opens
      .overrideComponent(CourseWizard, {
        add: { providers: [{ provide: MatDialog, useValue: { open } }] },
      })
      .compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(CourseWizard);
    fixture.componentRef.setInput('id', 'c1');
    fixture.detectChanges();
    http.expectOne('/api/v1/categories').flush([]);
    http.expectOne(base).flush(course({ version: 3 }));
    http.expectOne(`${base}/curriculum`).flush(curriculum());
    await fixture.whenStable();
  });

  afterEach(() => {
    vi.useRealTimers();
    http.verify();
  });

  it('fills the details form from the loaded course without saving', () => {
    expect(query<HTMLInputElement>(fixture, '[data-testid="title"]').value).toBe(
      'Kubernetes Basics',
    );
    http.expectNone(`${base}/details`); // ⭐ emitEvent: false — loading isn't an edit
  });

  it('autosaves typing ONCE, debounced, with the current version', async () => {
    vi.useFakeTimers();
    const title = query<HTMLInputElement>(fixture, '[data-testid="title"]');
    for (const value of ['K', 'Ku', 'Kube']) {
      title.value = value;
      title.dispatchEvent(new Event('input'));
      vi.advanceTimersByTime(200);
    }
    http.expectNone(`${base}/details`);
    vi.advanceTimersByTime(800);

    const req = http.expectOne(`${base}/details`);
    expect(req.request.body).toEqual(
      expect.objectContaining({ expectedVersion: 3, title: 'Kube' }),
    );
    req.flush(course({ title: 'Kube', version: 4 }));
    vi.useRealTimers();
    await fixture.whenStable();
    expect(text(fixture, 'save-state')).toBe('All changes saved');
  });

  /** ⭐ The answer to a save must not put older text back over what was typed meanwhile. */
  it('keeps what the user typed while a save was in flight', async () => {
    vi.useFakeTimers();
    const title = query<HTMLInputElement>(fixture, '[data-testid="title"]');
    title.value = 'Kube';
    title.dispatchEvent(new Event('input'));
    vi.advanceTimersByTime(800);
    const save = http.expectOne(`${base}/details`); // "Kube" is on its way…

    title.value = 'Kubernetes'; // …and the user keeps typing
    title.dispatchEvent(new Event('input'));
    save.flush(course({ title: 'Kube', version: 4 }));
    TestBed.tick(); // effects + change detection, still under the fake clock

    expect(title.value).toBe('Kubernetes'); // ⭐ not reverted to the saved "Kube"
    vi.advanceTimersByTime(800);
    expect(http.expectOne(`${base}/details`).request.body).toEqual(
      expect.objectContaining({ expectedVersion: 4, title: 'Kubernetes' }),
    );
  });

  it('shows the conflict dialog when another tab saved first, then reloads', async () => {
    vi.useFakeTimers();
    const title = query<HTMLInputElement>(fixture, '[data-testid="title"]');
    title.value = 'Stale tab';
    title.dispatchEvent(new Event('input'));
    vi.advanceTimersByTime(800);
    http
      .expectOne(`${base}/details`)
      .flush(
        { status: 409, code: 'VERSION_CONFLICT', expectedVersion: 3, currentVersion: 4 },
        { status: 409, statusText: 'Conflict' },
      );

    expect(open).toHaveBeenCalledTimes(1);
    http.expectOne(base).flush(course({ title: 'Other tab', version: 4 }));
    http.expectOne(`${base}/curriculum`).flush(curriculum({ version: 4 }));
    vi.useRealTimers();
    await fixture.whenStable();
    expect(query<HTMLInputElement>(fixture, '[data-testid="title"]').value).toBe('Other tab');
  });
});
