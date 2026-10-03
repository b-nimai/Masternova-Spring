import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { course } from '../../../../testing/authoring-fixtures';
import { query, submit } from '../../../../testing/dom';
import { NewCourse } from './new-course';

describe('NewCourse', () => {
  it('creates the draft with one idempotency key per form and opens the wizard', async () => {
    await TestBed.configureTestingModule({
      imports: [NewCourse],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    const http = TestBed.inject(HttpTestingController);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(NewCourse);
    await fixture.whenStable();
    http.expectOne('/api/v1/categories').flush([]);

    const component = fixture.componentInstance as unknown as {
      form: { setValue: (v: object) => void };
    };
    component.form.setValue({
      title: 'Kubernetes',
      categorySlug: 'ci-cd',
      level: 'BEGINNER',
      language: 'en',
    });
    submit(fixture);

    const req = http.expectOne('/api/v1/instructor/courses');
    expect(req.request.body).toEqual({
      title: 'Kubernetes',
      categorySlug: 'ci-cd',
      level: 'BEGINNER',
      language: 'en',
    });
    expect(req.request.headers.get('Idempotency-Key')).toMatch(/^[0-9a-f-]{36}$/);
    req.flush(course({ id: 'new-id' }));
    expect(navigate).toHaveBeenCalledWith(['/instructor/courses', 'new-id', 'edit']);
    expect(query(fixture, '[data-testid="create"]')).toBeTruthy();
    http.verify();
  });
});
