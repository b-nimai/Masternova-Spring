import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { text } from '../../../../testing/dom';
import { PendingApplicationResponse } from '../admin-api';
import { InstructorQueue } from './instructor-queue';

describe('InstructorQueue', () => {
  const ina: PendingApplicationResponse = {
    userId: 'u1',
    email: 'ina@x.dev',
    displayName: 'Ina',
    emailVerified: true,
    pitch: 'Kubernetes',
    appliedAt: '2026-10-22T10:00:00Z',
  };
  let fixture: ComponentFixture<InstructorQueue>;
  let http: HttpTestingController;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [InstructorQueue],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(InstructorQueue);
    fixture.detectChanges();
    http
      .expectOne('/api/v1/admin/instructor-applications')
      .flush({ items: [ina], nextCursor: null });
    await fixture.whenStable();
  });

  afterEach(() => http.verify());

  const click = (testId: string) =>
    (fixture.nativeElement as HTMLElement)
      .querySelector<HTMLButtonElement>(`[data-testid="${testId}"]`)!
      .click();

  it('lists who applied, with their pitch', () => {
    expect(text(fixture, 'application')).toContain('Ina');
    expect(text(fixture, 'application')).toContain('Kubernetes');
  });

  it('approving removes the row only once the server agreed', async () => {
    click('approve');
    await fixture.whenStable();
    const req = http.expectOne('/api/v1/admin/instructor-applications/u1/approve');
    expect(req.request.method).toBe('POST');
    expect(text(fixture, 'application')).toContain('Ina'); // still there while in flight

    req.flush({ status: 'APPROVED' });
    await fixture.whenStable();
    expect(text(fixture, 'queue-empty')).toContain('No applications');
  });

  it('a conflict says so and reloads the queue', async () => {
    click('reject');
    http
      .expectOne('/api/v1/admin/instructor-applications/u1/reject')
      .flush({ status: 409, code: 'APPLICATION_DECIDED' }, { status: 409, statusText: 'Conflict' });
    TestBed.tick(); // the reload starts on the next change detection
    http.expectOne('/api/v1/admin/instructor-applications').flush({ items: [], nextCursor: null });
    await fixture.whenStable();

    expect(text(fixture, 'queue-error')).toContain('already decided');
  });

  it('"Show more" appends the next page by its cursor', async () => {
    const ben = { ...ina, userId: 'u2', displayName: 'Ben' };
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [InstructorQueue],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(InstructorQueue);
    fixture.detectChanges();
    http
      .expectOne('/api/v1/admin/instructor-applications')
      .flush({ items: [ina], nextCursor: 'c1' });
    await fixture.whenStable();

    click('queue-more');
    const req = http.expectOne((r) => r.url === '/api/v1/admin/instructor-applications');
    expect(req.request.params.get('cursor')).toBe('c1');
    req.flush({ items: [ben], nextCursor: null });
    await fixture.whenStable();

    const rows = fixture.nativeElement.querySelectorAll('[data-testid="application"]');
    expect([...rows].map((r) => (r as HTMLElement).textContent)).toEqual([
      expect.stringContaining('Ina'),
      expect.stringContaining('Ben'),
    ]);
    expect(fixture.nativeElement.querySelector('[data-testid="queue-more"]')).toBeNull();
  });
});
