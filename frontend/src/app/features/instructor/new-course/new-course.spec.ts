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
    const firstKey = req.request.headers.get('Idempotency-Key');
    // a refused attempt: the server stored THAT answer under the key…
    req.flush(
      {
        status: 400,
        code: 'VALIDATION_FAILED',
        errors: [{ field: 'categorySlug', code: 'UNKNOWN_CATEGORY', message: 'No such category.' }],
      },
      { status: 400, statusText: 'Bad Request' },
    );
    component.form.setValue({
      title: 'Kubernetes',
      categorySlug: 'containers-kubernetes',
      level: 'BEGINNER',
      language: 'en',
    });
    submit(fixture); // the corrected form

    // …so the corrected attempt is a NEW request with a NEW key (no 422 IDEMPOTENCY_KEY_REUSED)
    const retry = http.expectOne('/api/v1/instructor/courses');
    expect(retry.request.headers.get('Idempotency-Key')).not.toBe(firstKey);
    retry.flush(course({ id: 'new-id' }));
    expect(navigate).toHaveBeenCalledWith(['/instructor/courses', 'new-id', 'edit']);
    expect(query(fixture, '[data-testid="create"]')).toBeTruthy();
    http.verify();
  });
});
