import { Routes } from '@angular/router';

/** Feature pages are lazy-loaded: each one becomes its own JS chunk. */
export const routes: Routes = [
  { path: '', loadComponent: () => import('./features/home/home').then((m) => m.Home) },
  {
    path: 'playground', // Phase 1.11 — Angular essentials (patterns/angular/01-angular-essentials.md)
    loadComponent: () =>
      import('./features/playground/playground/playground').then((m) => m.Playground),
  },
  { path: '**', redirectTo: '' },
];
