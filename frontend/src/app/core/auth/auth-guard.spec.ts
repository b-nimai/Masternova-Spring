import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { CanMatchFn, provideRouter, Route, Router, UrlSegment, UrlTree } from '@angular/router';
import { Role, TokenResponse } from './auth-api';
import { authGuard, guestGuard, roleGuard } from './auth-guard';
import { AuthStore } from './auth-store';

describe('route guards', () => {
  // Angular 22's CanMatchFn also receives `currentSnapshot` — our guards don't need it.
  const run = (guard: CanMatchFn, path = 'account') =>
    TestBed.runInInjectionContext(() =>
      guard({} as Route, [new UrlSegment(path, {})], {} as never),
    );

  function signInAs(...roles: Role[]) {
    const tokens: TokenResponse = {
      accessToken: 't',
      tokenType: 'Bearer',
      expiresIn: 900,
      user: { id: 'u', email: 'u@x.dev', displayName: 'U', roles, emailVerified: true },
    };
    TestBed.inject(AuthStore).login('u@x.dev', 'pw').subscribe();
    TestBed.inject(HttpTestingController).expectOne('/api/v1/auth/login').flush(tokens);
  }

  const urlOf = (result: unknown) => TestBed.inject(Router).serializeUrl(result as UrlTree);

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
  });

  it('authGuard sends anonymous users to login with a return URL', () => {
    expect(urlOf(run(authGuard))).toBe('/login?returnUrl=%2Faccount');
  });

  it('authGuard lets signed-in users through', () => {
    signInAs('LEARNER');
    expect(run(authGuard)).toBe(true);
  });

  it('roleGuard checks the role', () => {
    signInAs('LEARNER');
    expect(urlOf(run(roleGuard('ADMIN')))).toBe('/');
    expect(run(roleGuard('LEARNER', 'ADMIN'))).toBe(true);
  });

  it('guestGuard keeps signed-in users away from login/signup', () => {
    expect(run(guestGuard)).toBe(true);
    signInAs('LEARNER');
    expect(urlOf(run(guestGuard))).toBe('/account');
  });
});
