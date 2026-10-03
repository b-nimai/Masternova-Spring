import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { page } from '../../../../testing/catalog-fixtures';
import { query, text } from '../../../../testing/dom';
import { InstructorCourses } from './instructor-courses';

describe('InstructorCourses', () => {
  it('lists my courses with a link into the wizard', async () => {
    await TestBed.configureTestingModule({
      imports: [InstructorCourses],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    const http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(InstructorCourses);
    TestBed.tick();
    http.expectOne('/api/v1/instructor/courses').flush(page(1, 2, null));
    await fixture.whenStable();

    expect(
      query<HTMLAnchorElement>(fixture, '[data-testid="mine-course-1"] a').getAttribute('href'),
    ).toBe('/instructor/courses/1/edit');
    expect(text(fixture, 'empty')).toBe('');
    http.verify();
  });
});
