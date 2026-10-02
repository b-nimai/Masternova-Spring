import { BreakpointObserver, Breakpoints } from '@angular/cdk/layout';
import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatMenuModule } from '@angular/material/menu';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { map } from 'rxjs';
import { AuthStore } from './core/auth/auth-store';
import { NAV_ITEMS } from './core/layout/nav-items';

/**
 * The app shell: toolbar + responsive sidenav + routed page.
 *
 * ⭐ Responsive with a SIGNAL: BreakpointObserver emits whenever the viewport crosses a breakpoint;
 * toSignal turns that stream into state the template reads. Handset → the sidenav overlays the page
 * and closes after navigation; desktop → it sits beside the content, always open.
 */
@Component({
  selector: 'app-root',
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatToolbarModule,
    MatSidenavModule,
    MatListModule,
    MatIconModule,
    MatButtonModule,
    MatMenuModule,
  ],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected readonly isHandset = toSignal(
    inject(BreakpointObserver)
      .observe([Breakpoints.Handset])
      .pipe(map((state) => state.matches)),
    { initialValue: false },
  );

  /** Handset: user-controlled drawer. Desktop: always open. */
  protected readonly drawerOpen = signal(false);
  protected readonly sidenavOpened = computed(() => !this.isHandset() || this.drawerOpen());
  protected readonly sidenavMode = computed(() => (this.isHandset() ? 'over' : 'side'));

  protected readonly auth = inject(AuthStore);
  private readonly router = inject(Router);

  /**
   * ⭐ ROLE-BASED UI: entries the user can't use aren't shown. This is UX only — the API enforces
   * every rule again (deny-by-default + @PreAuthorize), so hiding a link is never the security.
   */
  protected readonly navItems = computed(() =>
    NAV_ITEMS.filter(
      (item) =>
        (!item.requiresAuth || this.auth.isAuthenticated()) &&
        (item.roles.length === 0 || this.auth.hasAnyRole(item.roles)),
    ),
  );

  protected logout(): void {
    this.auth.logout().subscribe({
      complete: () => void this.router.navigate(['/']),
      error: () => void this.router.navigate(['/']), // signed out locally either way
    });
  }

  protected toggleDrawer(): void {
    this.drawerOpen.update((open) => !open);
  }

  /** After picking a page on a phone, get the drawer out of the way. */
  protected closeOnHandset(): void {
    if (this.isHandset()) {
      this.drawerOpen.set(false);
    }
  }
}
