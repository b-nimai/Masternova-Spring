import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import {
  ApplicationConfig,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideRouter, TitleStrategy, withComponentInputBinding } from '@angular/router';
import { routes } from './app.routes';
import { authInterceptor } from './core/auth/auth-interceptor';
import { AuthStore } from './core/auth/auth-store';
import { PageTitle } from './core/layout/page-title';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding()),
    { provide: TitleStrategy, useExisting: PageTitle }, // route titles → tab + dashboard header
    provideHttpClient(withFetch(), withInterceptors([authInterceptor])),
    // ⭐ Before the first navigation (and its guards): resume the session from the refresh cookie.
    provideAppInitializer(() => inject(AuthStore).restoreSession()),
  ],
};
