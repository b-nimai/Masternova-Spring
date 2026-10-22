import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { CourseDetailResponse } from '../../../core/api/catalog-api';
import { LectureSidebar } from './lecture-sidebar';

describe('LectureSidebar', () => {
  const course = {
    id: 'c1',
    slug: 'k8s',
    title: 'Kubernetes',
    sections: [
      {
        id: 's1',
        title: 'Basics',
        lectures: [
          { id: 'l1', title: 'Pods', kind: 'VIDEO', durationSeconds: 60, preview: true },
          { id: 'l2', title: 'Services', kind: 'VIDEO', durationSeconds: 60, preview: false },
          { id: 'l3', title: 'Reading', kind: 'ARTICLE', durationSeconds: 60, preview: false },
          { id: 'l4', title: 'Ingress', kind: 'VIDEO', durationSeconds: 60, preview: false },
        ],
      },
    ],
  } as unknown as CourseDetailResponse;
  const allowed = { kind: 'ALLOWED', grant: 'PURCHASE' };

  async function render() {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    const fixture = TestBed.createComponent(LectureSidebar);
    fixture.componentRef.setInput('course', course);
    fixture.componentRef.setInput('currentLectureId', 'l2');
    TestBed.tick();
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('/api/v1/me/courses/c1/progress').flush({
      courseId: 'c1',
      enrolled: true,
      percentComplete: 25,
      completedLectureIds: ['l1'],
      continueLectureId: 'l2',
      continuePositionSeconds: 0,
    });
    http.expectOne('/api/v1/courses/c1/access').flush({
      courseId: 'c1',
      course: allowed,
      lectures: [
        { lectureId: 'l1', access: allowed },
        { lectureId: 'l2', access: allowed },
        { lectureId: 'l3', access: allowed },
        { lectureId: 'l4', access: { kind: 'DENIED', reason: 'NO_ENTITLEMENT' } },
      ],
    });
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  const row = (el: HTMLElement, id: string) =>
    el.querySelector<HTMLElement>(`[data-testid="side-${id}"]`)!;

  it('ticks completed lectures, marks the current one and counts what is done', async () => {
    const el = await render();

    expect(row(el, 'l1').classList).toContain('done');
    expect(row(el, 'l2').classList).toContain('current');
    expect(row(el, 'l2').querySelector('[aria-current="page"]')?.textContent).toContain('Services');
    expect(el.querySelector('[data-testid="done-count"]')?.textContent?.trim()).toBe('1/4');
    expect(el.querySelector('a.review')?.getAttribute('href')).toBe('/courses/k8s#reviews');
  });

  it('links only the other videos this viewer may open', async () => {
    const el = await render();

    expect(row(el, 'l1').querySelector('a')?.getAttribute('href')).toBe('/courses/k8s/lectures/l1');
    expect(row(el, 'l2').querySelector('a')).toBeNull(); // the current one
    expect(row(el, 'l3').querySelector('a')).toBeNull(); // an article: not for the player
    expect(row(el, 'l4').querySelector('a')).toBeNull(); // denied
  });
});
