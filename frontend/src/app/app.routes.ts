import { Routes } from '@angular/router';
import { authGuard, guestGuard, roleGuard } from './core/auth/auth-guard';
import { canWatchGuard } from './features/learn/can-watch-guard';

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
    // Phase 8: play one lecture. ⭐ canMatch signs the viewer in first (the token needs a user);
    // canActivate then asks the entitlement engine — denied viewers go back to the course page
    path: 'courses/:slug/lectures/:lectureId',
    canMatch: [authGuard],
    canActivate: [canWatchGuard],
    loadComponent: () =>
      import('./features/learn/lecture-player/lecture-player').then((m) => m.LecturePlayer),
  },
  {
    path: 'learning', // Phase 10: "My Learning", the learner's courses and progress
    canMatch: [authGuard],
    loadComponent: () =>
      import('./features/learn/my-learning/my-learning').then((m) => m.MyLearning),
  },
  {
    path: 'instructor', // Phase 6: an instructor's own courses (INSTRUCTOR or ADMIN)
    canMatch: [authGuard, roleGuard('INSTRUCTOR', 'ADMIN')],
    loadComponent: () =>
      import('./features/instructor/instructor-courses/instructor-courses').then(
        (m) => m.InstructorCourses,
      ),
  },
  {
    path: 'instructor/courses/new',
    canMatch: [authGuard, roleGuard('INSTRUCTOR', 'ADMIN')],
    loadComponent: () =>
      import('./features/instructor/new-course/new-course').then((m) => m.NewCourse),
  },
  {
    path: 'instructor/courses/:id/edit', // :id → the wizard's `id` input
    canMatch: [authGuard, roleGuard('INSTRUCTOR', 'ADMIN')],
    loadComponent: () =>
      import('./features/instructor/course-wizard/course-wizard').then((m) => m.CourseWizard),
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
    path: 'cart', // Phase 9: priced by the server, so signed in (who you are decides "already owned")
    canMatch: [authGuard],
    loadComponent: () => import('./features/commerce/cart/cart').then((m) => m.Cart),
  },
  {
    path: 'orders/:id', // the payment result; polls while the webhook is on its way
    canMatch: [authGuard],
    loadComponent: () =>
      import('./features/commerce/order-result/order-result').then((m) => m.OrderResult),
  },
  {
    path: 'account/orders',
    canMatch: [authGuard],
    loadComponent: () =>
      import('./features/commerce/order-history/order-history').then((m) => m.OrderHistory),
  },
  {
    path: 'admin',
    canMatch: [authGuard, roleGuard('ADMIN')],
    loadComponent: () => import('./features/admin/admin').then((m) => m.Admin),
  },
  { path: '**', redirectTo: '' },
];
