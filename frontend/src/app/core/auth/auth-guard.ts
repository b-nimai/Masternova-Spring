import { inject } from '@angular/core';
import { CanMatchFn, Router } from '@angular/router';
import { Role } from './auth-api';
import { AuthStore } from './auth-store';

/**
 * Route guards as plain functions. ⭐ CanMatch (not CanActivate): if the guard says no, the route
 * doesn't even MATCH — so its lazy JS chunk is never downloaded by someone who can't use it.
 */

/** Signed-in users only; everyone else goes to /login and comes back afterwards. */
export const authGuard: CanMatchFn = (_route, segments) => {
  if (inject(AuthStore).isAuthenticated()) {
    return true;
  }
  const returnUrl = '/' + segments.map((s) => s.path).join('/');
  return inject(Router).createUrlTree(['/login'], { queryParams: { returnUrl } });
};

/** Only users holding one of `roles` (the API enforces it again — the UI check is for UX). */
export function roleGuard(...roles: Role[]): CanMatchFn {
  return () => inject(AuthStore).hasAnyRole(roles) || inject(Router).createUrlTree(['/']);
}

/** Login/signup pages are for signed-OUT users. */
export const guestGuard: CanMatchFn = () =>
  !inject(AuthStore).isAuthenticated() || inject(Router).createUrlTree(['/account']);
