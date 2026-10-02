import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthApi } from './auth-api';

describe('AuthApi', () => {
  it('posts to the auth endpoints', () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const api = TestBed.inject(AuthApi);
    const http = TestBed.inject(HttpTestingController);

    api.login('a@x.dev', 'pw').subscribe();
    api.refresh().subscribe();
    api.verifyEmail('tok').subscribe();

    expect(http.expectOne('/api/v1/auth/login').request.body).toEqual({
      email: 'a@x.dev',
      password: 'pw',
    });
    expect(http.expectOne('/api/v1/auth/refresh').request.body).toBeNull(); // the cookie carries it
    expect(http.expectOne('/api/v1/auth/verify-email').request.body).toEqual({ token: 'tok' });
    http.verify();
  });
});
