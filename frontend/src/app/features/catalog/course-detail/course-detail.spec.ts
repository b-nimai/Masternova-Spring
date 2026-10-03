import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { DeferBlockBehavior, DeferBlockState, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { CourseDetailResponse } from '../../../core/api/catalog-api';
import { K8S } from '../../../../testing/catalog-fixtures';
import { text } from '../../../../testing/dom';
import { CourseDetail } from './course-detail';

const DETAIL: CourseDetailResponse = {
  ...K8S,
  description: 'Pods, deployments, services.',
  enrollmentCount: 42,
  priceSet: true,
  version: 2,
  sections: [
    {
      id: 's1',
      title: 'Intro',
      lectures: [{ id: 'l1', title: 'Welcome', kind: 'VIDEO', preview: true, durationSeconds: 90 }],
    },
  ],
};

describe('CourseDetail', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CourseDetail],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
      // ⭐ we decide when the @defer block loads, instead of faking an IntersectionObserver
      deferBlockBehavior: DeferBlockBehavior.Manual,
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function open(slug: string) {
    const fixture = TestBed.createComponent(CourseDetail);
    fixture.componentRef.setInput('slug', slug); // what the router does with /courses/:slug
    // ⭐ tick, not whenStable: a loading rxResource is a PENDING TASK, so the fixture isn't
    //    "stable" until we flush its request — whenStable here would wait forever
    TestBed.tick();
    return fixture;
  }

  it('loads the course named by the slug', async () => {
    const fixture = await open('k8s');
    http.expectOne('/api/v1/courses/k8s').flush(DETAIL);
    await fixture.whenStable();

    expect(text(fixture, 'title')).toBe('Kubernetes Basics');
    expect(text(fixture, 'price')).toBe('₹1,499.00');
    expect(text(fixture, 'draft-banner')).toBe('');
  });

  it('defers the curriculum until its block is triggered', async () => {
    const fixture = await open('k8s');
    http.expectOne('/api/v1/courses/k8s').flush(DETAIL);
    await fixture.whenStable();

    expect(text(fixture, 'curriculum-placeholder')).toBe('1 sections · 12 lectures');
    const [curriculum] = await fixture.getDeferBlocks();
    await curriculum.render(DeferBlockState.Complete); // as if it scrolled into view

    expect(text(fixture, 'section-0')).toContain('Intro');
    expect(text(fixture, 'curriculum-placeholder')).toBe('');
  });

  it('says "not found" for a 404 (missing, or a draft that isn\'t yours)', async () => {
    const fixture = await open('secret-draft');
    http
      .expectOne('/api/v1/courses/secret-draft')
      .flush({ status: 404, code: 'NOT_FOUND' }, { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();

    expect(text(fixture, 'not-found')).toContain('Course not found');
  });

  it('marks a draft its owner is looking at', async () => {
    const fixture = await open('mine');
    http.expectOne('/api/v1/courses/mine').flush({ ...DETAIL, status: 'DRAFT' });
    await fixture.whenStable();

    expect(text(fixture, 'draft-banner')).toContain('only you can see this page');
  });

  it('loads the new course when the slug changes', async () => {
    const fixture = await open('k8s');
    http.expectOne('/api/v1/courses/k8s').flush(DETAIL);
    await fixture.whenStable();

    fixture.componentRef.setInput('slug', 'docker');
    TestBed.tick();

    http.expectOne('/api/v1/courses/docker').flush({ ...DETAIL, title: 'Docker' });
    await fixture.whenStable();
    expect(text(fixture, 'title')).toBe('Docker');
  });
});
