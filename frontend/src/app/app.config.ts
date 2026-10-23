import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import {
  ApplicationConfig,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { MAT_FORM_FIELD_DEFAULT_OPTIONS } from '@angular/material/form-field';
import {
  provideRouter,
  TitleStrategy,
  withComponentInputBinding,
  withViewTransitions,
} from '@angular/router';
import { routes } from './app.routes';
import { authInterceptor } from './core/auth/auth-interceptor';
import { AuthStore } from './core/auth/auth-store';
import { PageTitle } from './core/layout/page-title';
import { skipUnlessPageChanges } from './core/layout/page-transitions';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(
      routes,
      withComponentInputBinding(),
      // a cross-fade between PAGES (not filters): core/layout/page-transitions.ts. Scrolling to
      // the top of a new page: core/layout/scroll-to-top.ts
      withViewTransitions({
        skipInitialTransition: true,
        onViewTransitionCreated: skipUnlessPageChanges,
      }),
    ),
    { provide: TitleStrategy, useExisting: PageTitle }, // route titles → tab + dashboard header
    // the Figma spaces fields 20 px apart: an error line takes room only when there is one
    { provide: MAT_FORM_FIELD_DEFAULT_OPTIONS, useValue: { subscriptSizing: 'dynamic' } },
    provideHttpClient(withFetch(), withInterceptors([authInterceptor])),
    // ⭐ Before the first navigation (and its guards): resume the session from the refresh cookie.
    provideAppInitializer(() => inject(AuthStore).restoreSession()),
  ],
};
