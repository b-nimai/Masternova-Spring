import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { TokenResponse } from './auth-api';
import { authInterceptor } from './auth-interceptor';
import { AuthStore } from './auth-store';

describe('authInterceptor', () => {
  const tokens = (accessToken: string): TokenResponse => ({
    accessToken,
    tokenType: 'Bearer',
    expiresIn: 900,
    user: { id: 'u1', email: 'a@x.dev', displayName: 'A', roles: ['LEARNER'], emailVerified: true },
  });
  let http: HttpTestingController;
  let client: HttpClient;
  const navigate = vi.fn();

  beforeEach(() => {
    navigate.mockReset();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: Router, useValue: { navigate, url: '/account' } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    client = TestBed.inject(HttpClient);
    TestBed.inject(AuthStore).login('a@x.dev', 'pw').subscribe();
    http.expectOne('/api/v1/auth/login').flush(tokens('old'));
  });

  afterEach(() => http.verify());

  it('attaches the access token to API calls', () => {
    client.get('/api/v1/me').subscribe();

    expect(http.expectOne('/api/v1/me').request.headers.get('Authorization')).toBe('Bearer old');
  });

  it('never attaches it to the auth endpoints themselves', () => {
    client.post('/api/v1/auth/refresh', null).subscribe();

    expect(http.expectOne('/api/v1/auth/refresh').request.headers.has('Authorization')).toBe(false);
  });

  it('on 401: refreshes once and retries with the new token', () => {
    let body: unknown;
    client.get('/api/v1/me').subscribe((b) => (body = b));

    http
      .expectOne('/api/v1/me')
      .flush({ status: 401 }, { status: 401, statusText: 'Unauthorized' });
    http.expectOne('/api/v1/auth/refresh').flush(tokens('new'));
    const retry = http.expectOne('/api/v1/me');
    expect(retry.request.headers.get('Authorization')).toBe('Bearer new');
    retry.flush({ email: 'a@x.dev' });

    expect(body).toEqual({ email: 'a@x.dev' });
  });

  it('when the refresh fails too: sends the user to log in and surfaces the error', () => {
    let failed = false;
    client.get('/api/v1/me').subscribe({ error: () => (failed = true) });

    http
      .expectOne('/api/v1/me')
      .flush({ status: 401 }, { status: 401, statusText: 'Unauthorized' });
    http
      .expectOne('/api/v1/auth/refresh')
      .flush({ status: 401 }, { status: 401, statusText: 'Unauthorized' });

    expect(failed).toBe(true);
    expect(navigate).toHaveBeenCalledWith(['/login'], { queryParams: { returnUrl: '/account' } });
  });

  it('passes other errors straight through', () => {
    let status = 0;
    client.get('/api/v1/me').subscribe({ error: (e) => (status = e.status) });

    http.expectOne('/api/v1/me').flush({ status: 403 }, { status: 403, statusText: 'Forbidden' });

    expect(status).toBe(403); // no refresh attempt for a 403
  });
});
