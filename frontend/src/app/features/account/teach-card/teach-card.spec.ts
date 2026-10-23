import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Observable, of, Subject, throwError } from 'rxjs';
import { text } from '../../../../testing/dom';
import { Role } from '../../../core/auth/auth-api';
import { AuthStore } from '../../../core/auth/auth-store';
import {
  InstructorApplicationApi,
  InstructorApplicationResponse,
} from '../instructor-application-api';
import { TeachCard } from './teach-card';

describe('TeachCard', () => {
  const pending: InstructorApplicationResponse = {
    status: 'PENDING',
    pitch: null,
    appliedAt: '2026-10-22T10:00:00Z',
    decidedAt: null,
  };
  let mine: () => Observable<InstructorApplicationResponse>;
  let apply: ReturnType<typeof vi.fn>;
  let refresh: ReturnType<typeof vi.fn>;
  let roles: Role[];

  beforeEach(() => {
    roles = ['LEARNER'];
    apply = vi.fn(() => of(pending));
    refresh = vi.fn(() => of('t'));
    TestBed.configureTestingModule({
      imports: [TeachCard],
      providers: [
        provideRouter([]),
        { provide: InstructorApplicationApi, useValue: { mine: () => mine(), apply } },
        {
          provide: AuthStore,
          useValue: { hasAnyRole: (r: Role[]) => r.some((x) => roles.includes(x)), refresh },
        },
      ],
    });
  });

  async function render() {
    const fixture = TestBed.createComponent(TeachCard);
    await fixture.whenStable();
    return fixture;
  }

  it('a learner who never applied (404) applies with an optional pitch, then waits', async () => {
    mine = () => throwError(() => new HttpErrorResponse({ status: 404, error: { status: 404 } }));
    const fixture = await render();
    expect(text(fixture, 'teach-none')).toContain('admin reviews');

    const pitch = fixture.nativeElement.querySelector('[data-testid="teach-pitch"]');
    pitch.value = '  Kubernetes  ';
    pitch.dispatchEvent(new Event('input'));
    fixture.nativeElement.querySelector('[data-testid="teach-apply"]').click();
    await fixture.whenStable();

    expect(apply).toHaveBeenCalledWith('Kubernetes');
    expect(text(fixture, 'teach-pending')).toContain('waiting for an admin');
  });

  it('a rejected applicant can apply again', async () => {
    mine = () => of({ ...pending, status: 'REJECTED', decidedAt: '2026-10-22T11:00:00Z' });
    const fixture = await render();

    expect(text(fixture, 'teach-rejected')).toContain('apply again');
  });

  it('⭐ approved but the token still says learner: one silent refresh picks the role up', async () => {
    refresh.mockReturnValue(new Subject<string>()); // still in flight
    mine = () => of({ ...pending, status: 'APPROVED', decidedAt: '2026-10-22T11:00:00Z' });
    const fixture = await render();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(text(fixture, 'teach-approved')).toContain('Approved');
    expect(refresh).toHaveBeenCalledTimes(1);
  });

  it('an instructor gets a link to their courses, and nothing is requested twice', async () => {
    roles = ['LEARNER', 'INSTRUCTOR'];
    mine = () => of({ ...pending, status: 'APPROVED', decidedAt: '2026-10-22T11:00:00Z' });
    const fixture = await render();

    expect(text(fixture, 'teach-instructor')).toContain("You're an instructor");
    expect(refresh).not.toHaveBeenCalled();
  });

  it('approved, refreshed, and still no role (taken away since): offers to apply again', async () => {
    mine = () => of({ ...pending, status: 'APPROVED', decidedAt: '2026-10-22T11:00:00Z' });
    const fixture = await render();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(refresh).toHaveBeenCalledTimes(1);
    expect(text(fixture, 'teach-removed')).toContain('Apply again');
    expect(fixture.nativeElement.querySelector('[data-testid="teach-apply"]')).not.toBeNull();
  });
});
