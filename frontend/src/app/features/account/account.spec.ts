import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';
import { text } from '../../../testing/dom';
import { AuthApi, TokenResponse } from '../../core/auth/auth-api';
import { AuthStore } from '../../core/auth/auth-store';
import { Account } from './account';

describe('Account', () => {
  const tokens = (emailVerified: boolean): TokenResponse => ({
    accessToken: 't',
    tokenType: 'Bearer',
    expiresIn: 900,
    user: { id: 'u1', email: 'asha@x.dev', displayName: 'Asha', roles: ['LEARNER'], emailVerified },
  });
  let verified = true;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Account],
      providers: [
        provideRouter([]),
        {
          provide: AuthApi,
          useValue: { login: () => of(tokens(verified)), logout: () => of(undefined) },
        },
      ],
    }).compileComponents();
  });

  async function signedIn(emailVerified: boolean) {
    verified = emailVerified;
    TestBed.inject(AuthStore).login('asha@x.dev', 'pw').subscribe();
    const fixture = TestBed.createComponent(Account);
    await fixture.whenStable();
    return fixture;
  }

  it("shows the user's name and verified status", async () => {
    const fixture = await signedIn(true);
    expect(text(fixture, 'display-name')).toBe('Asha');
    expect(text(fixture, 'email-status')).toContain('Verified');
  });

  it('nudges an unverified user to check their inbox', async () => {
    const fixture = await signedIn(false);
    expect(text(fixture, 'email-status')).toContain('Not verified');
  });

  it('logout clears the session and leaves the page', async () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const fixture = await signedIn(true);

    (fixture.nativeElement as HTMLElement).querySelector('button')!.click();
    await fixture.whenStable();

    expect(TestBed.inject(AuthStore).isAuthenticated()).toBe(false);
    expect(navigate).toHaveBeenCalledWith('/');
  });
});
