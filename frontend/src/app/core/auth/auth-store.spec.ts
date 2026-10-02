import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { TokenResponse } from './auth-api';
import { AuthStore } from './auth-store';

describe('AuthStore', () => {
  const tokens = (accessToken: string): TokenResponse => ({
    accessToken,
    tokenType: 'Bearer',
    expiresIn: 900,
    user: {
      id: 'u1',
      email: 'asha@x.dev',
      displayName: 'Asha',
      roles: ['LEARNER'],
      emailVerified: true,
    },
  });
  let store: AuthStore;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    store = TestBed.inject(AuthStore);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('login keeps the token in memory and exposes the user as signals', () => {
    store.login('asha@x.dev', 'pw').subscribe();
    http.expectOne('/api/v1/auth/login').flush(tokens('t1'));

    expect(store.isAuthenticated()).toBe(true);
    expect(store.accessToken()).toBe('t1');
    expect(store.hasAnyRole(['LEARNER'])).toBe(true);
    expect(store.hasAnyRole(['ADMIN'])).toBe(false);
  });

  it('refresh is SINGLE-FLIGHT: concurrent callers share one request', () => {
    const received: string[] = [];

    store.refresh().subscribe((t) => received.push(t));
    store.refresh().subscribe((t) => received.push(t));
    store.refresh().subscribe((t) => received.push(t));

    http.expectOne('/api/v1/auth/refresh').flush(tokens('t2')); // ⭐ exactly ONE request
    expect(received).toEqual(['t2', 't2', 't2']);

    store.refresh().subscribe(); // a LATER refresh is a new request
    http.expectOne('/api/v1/auth/refresh').flush(tokens('t3'));
    expect(store.accessToken()).toBe('t3');
  });

  it('a failed refresh signs the user out', () => {
    store.login('asha@x.dev', 'pw').subscribe();
    http.expectOne('/api/v1/auth/login').flush(tokens('t1'));

    store.refresh().subscribe({ error: () => undefined });
    http
      .expectOne('/api/v1/auth/refresh')
      .flush({ status: 401, code: 'SESSION_REVOKED' }, { status: 401, statusText: 'Unauthorized' });

    expect(store.isAuthenticated()).toBe(false);
    expect(store.accessToken()).toBeNull();
  });

  it('restoreSession is quiet when there is no session', async () => {
    const restored = store.restoreSession();
    http
      .expectOne('/api/v1/auth/refresh')
      .flush({ status: 401 }, { status: 401, statusText: 'Unauthorized' });

    await expect(restored).resolves.toBeUndefined();
    expect(store.isAuthenticated()).toBe(false);
  });

  it('logout clears local state even when the server call fails', () => {
    store.login('asha@x.dev', 'pw').subscribe();
    http.expectOne('/api/v1/auth/login').flush(tokens('t1'));

    store.logout().subscribe({ error: () => undefined });
    http.expectOne('/api/v1/auth/logout').flush(null, { status: 500, statusText: 'Server Error' });

    expect(store.isAuthenticated()).toBe(false);
  });
});
