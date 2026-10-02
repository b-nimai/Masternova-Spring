import { Routes } from '@angular/router';
import { authGuard, guestGuard, roleGuard } from './core/auth/auth-guard';

/**
 * Feature pages are lazy-loaded (each one its own JS chunk). ⭐ canMatch guards stop a route from
 * matching at all — so a user who may not see a page never even downloads its code.
 */
export const routes: Routes = [
  { path: '', loadComponent: () => import('./features/home/home').then((m) => m.Home) },
  {
    path: 'playground', // Phase 1.11 — Angular essentials (patterns/angular/01-angular-essentials.md)
    loadComponent: () =>
      import('./features/playground/playground/playground').then((m) => m.Playground),
  },
  {
    path: 'login',
    canMatch: [guestGuard],
    loadComponent: () => import('./features/auth/login/login').then((m) => m.Login),
  },
  {
    path: 'signup',
    canMatch: [guestGuard],
    loadComponent: () => import('./features/auth/signup/signup').then((m) => m.Signup),
  },
  {
    path: 'verify-email', // the link in the verification email: /verify-email?token=…
    loadComponent: () =>
      import('./features/auth/verify-email/verify-email').then((m) => m.VerifyEmail),
  },
  {
    path: 'account',
    canMatch: [authGuard],
    loadComponent: () => import('./features/account/account').then((m) => m.Account),
  },
  {
    path: 'admin',
    canMatch: [authGuard, roleGuard('ADMIN')],
    loadComponent: () => import('./features/admin/admin').then((m) => m.Admin),
  },
  { path: '**', redirectTo: '' },
];
