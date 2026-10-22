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
  withInMemoryScrolling,
} from '@angular/router';
import { routes } from './app.routes';
import { authInterceptor } from './core/auth/auth-interceptor';
import { AuthStore } from './core/auth/auth-store';
import { PageTitle } from './core/layout/page-title';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(
      routes,
      withComponentInputBinding(),
      // `#reviews` (the player's "Add Review") scrolls to its id; scroll restoration stays off,
      // or every filter change in the catalog (a query-param navigation) would jump to the top
      withInMemoryScrolling({ anchorScrolling: 'enabled' }),
    ),
    { provide: TitleStrategy, useExisting: PageTitle }, // route titles → tab + dashboard header
    // the Figma spaces fields 20 px apart: an error line takes room only when there is one
    { provide: MAT_FORM_FIELD_DEFAULT_OPTIONS, useValue: { subscriptSizing: 'dynamic' } },
    provideHttpClient(withFetch(), withInterceptors([authInterceptor])),
    // ⭐ Before the first navigation (and its guards): resume the session from the refresh cookie.
    provideAppInitializer(() => inject(AuthStore).restoreSession()),
  ],
};
