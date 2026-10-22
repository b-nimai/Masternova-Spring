import { Routes } from '@angular/router';
import { authGuard, guestGuard, roleGuard } from './core/auth/auth-guard';
import { DashboardLayout } from './core/layout/dashboard-layout/dashboard-layout';
import { PublicLayout } from './core/layout/public-layout/public-layout';
import { canWatchGuard } from './features/learn/can-watch-guard';

/**
 * Feature pages are lazy-loaded (each one its own JS chunk). ⭐ canMatch guards stop a route from
 * matching at all — so a user who may not see a page never even downloads its code.
 *
 * ⭐ Two LAYOUT routes (path '' + a component + children): the public layout adds the footer, the
 * dashboard layout the sidebar and header. A URL no child of the first matches falls through to
 * the second. The guards stay on the children: a guard on the dashboard parent would also run
 * for an unknown URL and send a visitor to log in instead of home.
 * Each `title` names the browser tab and the dashboard header (core/layout/page-title.ts).
 */
export const routes: Routes = [
  {
    path: '',
    component: PublicLayout,
    children: [
      {
        path: '',
        pathMatch: 'full',
        loadComponent: () => import('./features/home/home').then((m) => m.Home),
      },
      {
        path: 'playground', // Phase 1.11 — Angular essentials (patterns/angular/01-angular-essentials.md)
        title: 'Playground',
        loadComponent: () =>
          import('./features/playground/playground/playground').then((m) => m.Playground),
      },
      {
        path: 'courses', // the public catalog: filters live in the query string (Phase 5.8)
        title: 'Catalog',
        loadComponent: () => import('./features/catalog/catalog').then((m) => m.Catalog),
      },
      {
        path: 'search', // Phase 11: ranked search; ?q= and ?mode= bind to the page's inputs
        title: 'Search',
        loadComponent: () =>
          import('./features/search/search-results/search-results').then((m) => m.SearchResults),
      },
      {
        path: 'courses/:slug', // :slug → the component's `slug` input (withComponentInputBinding)
        title: 'Course',
        loadComponent: () =>
          import('./features/catalog/course-detail/course-detail').then((m) => m.CourseDetail),
      },
      {
        path: 'login',
        title: 'Log in',
        canMatch: [guestGuard],
        loadComponent: () => import('./features/auth/login/login').then((m) => m.Login),
      },
      {
        path: 'signup',
        title: 'Sign up',
        canMatch: [guestGuard],
        loadComponent: () => import('./features/auth/signup/signup').then((m) => m.Signup),
      },
      {
        path: 'verify-email', // the link in the verification email: /verify-email?token=…
        title: 'Verify email',
        loadComponent: () =>
          import('./features/auth/verify-email/verify-email').then((m) => m.VerifyEmail),
      },
      {
        path: 'unsubscribe', // the footer link in optional emails: /unsubscribe?token=… (no login needed)
        title: 'Unsubscribe',
        loadComponent: () =>
          import('./features/unsubscribe/unsubscribe').then((m) => m.Unsubscribe),
      },
    ],
  },
  {
    // Phase 8: play one lecture, full screen (no layout). ⭐ canMatch signs the viewer in first
    // (the token needs a user); canActivate then asks the entitlement engine — denied viewers go
    // back to the course page
    path: 'courses/:slug/lectures/:lectureId',
    title: 'Lecture',
    canMatch: [authGuard],
    canActivate: [canWatchGuard],
    loadComponent: () =>
      import('./features/learn/lecture-player/lecture-player').then((m) => m.LecturePlayer),
  },
  {
    path: '',
    component: DashboardLayout,
    children: [
      {
        path: 'account',
        title: 'My Profile',
        canMatch: [authGuard],
        loadComponent: () => import('./features/account/account').then((m) => m.Account),
      },
      {
        path: 'account/notifications',
        title: 'Settings',
        canMatch: [authGuard],
        loadComponent: () =>
          import('./features/account/notifications/notifications').then((m) => m.Notifications),
      },
      {
        path: 'learning', // Phase 10: "My Learning", the learner's courses and progress
        title: 'Enrolled Courses',
        canMatch: [authGuard],
        loadComponent: () =>
          import('./features/learn/my-learning/my-learning').then((m) => m.MyLearning),
      },
      {
        path: 'cart', // Phase 9: priced by the server, so signed in (who you are decides "already owned")
        title: 'Cart',
        canMatch: [authGuard],
        loadComponent: () => import('./features/commerce/cart/cart').then((m) => m.Cart),
      },
      {
        path: 'orders/:id', // the payment result; polls while the webhook is on its way
        title: 'Order',
        data: { pageHeader: false },
        canMatch: [authGuard],
        loadComponent: () =>
          import('./features/commerce/order-result/order-result').then((m) => m.OrderResult),
      },
      {
        path: 'account/orders',
        title: 'Purchase History',
        canMatch: [authGuard],
        loadComponent: () =>
          import('./features/commerce/order-history/order-history').then((m) => m.OrderHistory),
      },
      {
        path: 'instructor', // Phase 6: an instructor's own courses (INSTRUCTOR or ADMIN)
        title: 'My Courses',
        canMatch: [authGuard, roleGuard('INSTRUCTOR', 'ADMIN')],
        loadComponent: () =>
          import('./features/instructor/instructor-courses/instructor-courses').then(
            (m) => m.InstructorCourses,
          ),
      },
      {
        path: 'instructor/courses/new',
        title: 'New course',
        canMatch: [authGuard, roleGuard('INSTRUCTOR', 'ADMIN')],
        loadComponent: () =>
          import('./features/instructor/new-course/new-course').then((m) => m.NewCourse),
      },
      {
        path: 'instructor/courses/:id/edit', // :id → the wizard's `id` input
        title: 'Edit course',
        data: { pageHeader: false }, // the wizard's heading is the course's title
        canMatch: [authGuard, roleGuard('INSTRUCTOR', 'ADMIN')],
        loadComponent: () =>
          import('./features/instructor/course-wizard/course-wizard').then((m) => m.CourseWizard),
      },
      {
        path: 'admin',
        title: 'Admin',
        canMatch: [authGuard, roleGuard('ADMIN')],
        loadComponent: () => import('./features/admin/admin').then((m) => m.Admin),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
