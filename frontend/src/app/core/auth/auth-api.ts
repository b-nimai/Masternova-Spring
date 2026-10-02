import { HttpClient } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Observable } from 'rxjs';

export type Role = 'LEARNER' | 'INSTRUCTOR' | 'ADMIN';

/** Mirrors the backend's `UserResponse` record. */
export interface User {
  id: string;
  email: string;
  displayName: string;
  roles: Role[];
  emailVerified: boolean;
}

/** Mirrors `TokenResponse`. The refresh token is NOT here — it lives in an httpOnly cookie. */
export interface TokenResponse {
  accessToken: string;
  tokenType: 'Bearer';
  expiresIn: number;
  user: User;
}

export interface SignupRequest {
  email: string;
  displayName: string;
  password: string;
}

/** Thin HTTP layer for /api/v1/auth and /api/v1/me — no state here (that's AuthStore). */
@Service()
export class AuthApi {
  private readonly http = inject(HttpClient);

  signup(body: SignupRequest): Observable<User> {
    return this.http.post<User>('/api/v1/auth/signup', body);
  }

  verifyEmail(token: string): Observable<void> {
    return this.http.post<void>('/api/v1/auth/verify-email', { token });
  }

  login(email: string, password: string): Observable<TokenResponse> {
    return this.http.post<TokenResponse>('/api/v1/auth/login', { email, password });
  }

  /** The browser attaches the httpOnly refresh cookie by itself (same origin). */
  refresh(): Observable<TokenResponse> {
    return this.http.post<TokenResponse>('/api/v1/auth/refresh', null);
  }

  logout(): Observable<void> {
    return this.http.post<void>('/api/v1/auth/logout', null);
  }

  me(): Observable<User> {
    return this.http.get<User>('/api/v1/me');
  }
}
