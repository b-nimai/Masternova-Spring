import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { LearningCourseResponse } from '../../../core/api/learning-api';
import { MyLearning, nextStep } from './my-learning';

const course = (over: Partial<LearningCourseResponse>): LearningCourseResponse => ({
  courseId: 'c1',
  slug: 'k8s',
  title: 'Kubernetes',
  instructorName: 'Asha Rao',
  completedLectures: 1,
  totalLectures: 4,
  percentComplete: 25,
  enrolledAt: '2026-10-01T10:00:00Z',
  lastActivityAt: '2026-10-15T10:00:00Z',
  continueLectureId: 'l2',
  ...over,
});

describe('MyLearning', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<MyLearning>;
  const el = () => fixture.nativeElement as HTMLElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MyLearning],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(MyLearning);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  const load = async (courses: LearningCourseResponse[]) => {
    http.expectOne('/api/v1/me/learning').flush(courses);
    await fixture.whenStable();
    fixture.detectChanges();
  };

  it('shows each course with its progress and a button to the lecture to continue', async () => {
    await load([course({})]);

    const row = el().querySelector('[data-testid="course-c1"]')!;
    expect(row.textContent).toContain('Kubernetes');
    expect(row.querySelector('[data-testid="percent"]')!.textContent).toContain('25%');
    expect(row.textContent).toContain('1 of 4 lectures');
    const next = row.querySelector<HTMLAnchorElement>('[data-testid="next-step"]')!;
    expect(next.textContent).toContain('Continue');
    expect(next.getAttribute('href')).toBe('/courses/k8s/lectures/l2');
  });

  it('filters completed courses from those in progress', async () => {
    await load([
      course({}),
      course({ courseId: 'c2', continueLectureId: null, percentComplete: 100 }),
    ]);
    const toggles = el().querySelectorAll<HTMLButtonElement>('mat-button-toggle button');

    toggles[2].click(); // Completed
    fixture.detectChanges();
    expect(el().querySelector('[data-testid="course-c1"]')).toBeNull();
    expect(el().querySelector('[data-testid="course-c2"]')).not.toBeNull();

    toggles[1].click(); // In progress
    fixture.detectChanges();
    expect(el().querySelector('[data-testid="course-c2"]')).toBeNull();
  });

  it('invites a learner with no courses to browse', async () => {
    await load([]);
    expect(el().querySelector('[data-testid="no-courses"]')!.textContent).toContain(
      "haven't enrolled",
    );
  });

  it('names the next step: start, continue or review', async () => {
    await load([]);
    expect(nextStep(course({ lastActivityAt: null }))).toBe('START');
    expect(nextStep(course({}))).toBe('CONTINUE');
    expect(nextStep(course({ continueLectureId: null }))).toBe('REVIEW');
  });
});
