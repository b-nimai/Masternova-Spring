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
    path: 'courses', // the public catalog: filters live in the query string (Phase 5.8)
    loadComponent: () => import('./features/catalog/catalog').then((m) => m.Catalog),
  },
  {
    path: 'courses/:slug', // :slug → the component's `slug` input (withComponentInputBinding)
    loadComponent: () =>
      import('./features/catalog/course-detail/course-detail').then((m) => m.CourseDetail),
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
    path: 'unsubscribe', // the footer link in optional emails: /unsubscribe?token=… (no login needed)
    loadComponent: () => import('./features/unsubscribe/unsubscribe').then((m) => m.Unsubscribe),
  },
  {
    path: 'account',
    canMatch: [authGuard],
    loadComponent: () => import('./features/account/account').then((m) => m.Account),
  },
  {
    path: 'account/notifications',
    canMatch: [authGuard],
    loadComponent: () =>
      import('./features/account/notifications/notifications').then((m) => m.Notifications),
  },
  {
    path: 'admin',
    canMatch: [authGuard, roleGuard('ADMIN')],
    loadComponent: () => import('./features/admin/admin').then((m) => m.Admin),
  },
  { path: '**', redirectTo: '' },
];
