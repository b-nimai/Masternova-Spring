import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthStore } from './auth-store';

const AUTH_ENDPOINTS = '/api/v1/auth/';

/**
 * Every API call: attach the access token; if the API answers 401, refresh ONCE (single-flight in
 * AuthStore) and retry with the new token. A functional interceptor (Angular 15+): a plain
 * function, dependencies via inject().
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  // ⭐ never touch the auth endpoints themselves — refreshing a failed /refresh would loop forever
  if (!req.url.startsWith('/api/') || req.url.startsWith(AUTH_ENDPOINTS)) {
    return next(req);
  }
  const store = inject(AuthStore);
  const router = inject(Router);
  const withToken = (request: HttpRequest<unknown>, token: string | null) =>
    token ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : request;

  return next(withToken(req, store.accessToken())).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse) || error.status !== 401) {
        return throwError(() => error);
      }
      return store.refresh().pipe(
        switchMap((freshToken) => next(withToken(req, freshToken))), // retry exactly once
        catchError(() => {
          // the session is really over → send the user to log in, then come back here
          void router.navigate(['/login'], { queryParams: { returnUrl: router.url } });
          return throwError(() => error);
        }),
      );
    }),
  );
};
