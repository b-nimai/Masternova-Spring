import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AccessResponse } from '../../../core/api/entitlement-api';
import { AuthStore } from '../../../core/auth/auth-store';
import { AccessCta } from './access-cta';

describe('AccessCta', () => {
  let fixture: ComponentFixture<AccessCta>;
  let signedIn = false;

  beforeEach(async () => {
    signedIn = false;
    await TestBed.configureTestingModule({
      imports: [AccessCta],
      providers: [
        provideRouter([]),
        { provide: AuthStore, useValue: { isAuthenticated: () => signedIn } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(AccessCta);
    fixture.componentRef.setInput('slug', 'k8s');
    fixture.componentRef.setInput('firstLectureId', 'l1');
  });

  function show(access: AccessResponse): HTMLElement {
    fixture.componentRef.setInput('access', access);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('starts learning when allowed', () => {
    const el = show({ kind: 'ALLOWED', grant: 'ENTITLED' });
    const link = el.querySelector('a')!;
    expect(link.textContent).toContain('Start learning');
    expect(link.getAttribute('href')).toBe('/courses/k8s/lectures/l1');
  });

  it('asks a visitor to sign in, a member to buy', () => {
    expect(show({ kind: 'DENIED', reason: 'NO_ENTITLEMENT' }).textContent).toContain(
      'Sign in to buy',
    );
    signedIn = true;
    fixture.componentRef.setInput('access', { kind: 'ALLOWED', grant: 'ENTITLED' });
    fixture.detectChanges(); // re-render with the new auth state
    expect(show({ kind: 'DENIED', reason: 'NO_ENTITLEMENT' }).textContent).toContain('Buy now');
  });

  it('offers a renewal for expired access and only the truth after a refund', () => {
    expect(show({ kind: 'DENIED', reason: 'ENTITLEMENT_EXPIRED' }).textContent).toContain(
      'Renew access',
    );
    const revoked = show({ kind: 'DENIED', reason: 'ENTITLEMENT_REVOKED' });
    expect(revoked.querySelector('button, a')).toBeNull();
    expect(revoked.textContent).toContain('Contact support');
  });

  it('lets the page retry when the check itself failed', () => {
    let retried = 0;
    fixture.componentInstance.retry.subscribe(() => retried++);
    show({ kind: 'DENIED', reason: 'ACCESS_CHECK_FAILED' }).querySelector('button')!.click();
    expect(retried).toBe(1);
  });
});
