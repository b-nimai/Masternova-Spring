import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { AuthStore } from './auth-store';

/**
 * Log out and go home: the nav bar's avatar menu and the dashboard sidebar both offer it. Call in
 * an injection context (a field initializer); the returned function can then be called anywhere.
 */
export function injectSignOut(): () => void {
  const auth = inject(AuthStore);
  const router = inject(Router);
  return () =>
    auth.logout().subscribe({
      complete: () => void router.navigate(['/']),
      error: () => void router.navigate(['/']), // signed out locally either way
    });
}
