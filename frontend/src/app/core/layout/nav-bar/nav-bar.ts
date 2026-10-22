import { BreakpointObserver, Breakpoints } from '@angular/cdk/layout';
import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatBadgeModule } from '@angular/material/badge';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { map } from 'rxjs';
import { CatalogApi } from '../../api/catalog-api';
import { AuthStore } from '../../auth/auth-store';
import { injectSignOut } from '../../auth/sign-out';
import { CartStore } from '../../commerce/cart-store';
import { SearchBox } from '../../../shared/search-box/search-box';
import { SITE_LINKS, visibleTo } from '../nav-items';

/**
 * The top navigation bar (Figma "Navigation Bar"): logo, links + a Catalog menu of the
 * categories, then search, cart and the account menu.
 *
 * ⭐ Responsive with a SIGNAL: BreakpointObserver → toSignal. On a handset the links collapse into
 * one menu. ⭐ The search box is `@defer (when searchOpen())`: its autocomplete and form-field code
 * (~134 kB) download the first time someone opens search, not with the page.
 */
@Component({
  selector: 'app-nav-bar',
  imports: [
    RouterLink,
    RouterLinkActive,
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
    MatBadgeModule,
    SearchBox,
  ],
  templateUrl: './nav-bar.html',
  styleUrl: './nav-bar.scss',
})
export class NavBar {
  protected readonly auth = inject(AuthStore);
  protected readonly cart = inject(CartStore);
  protected readonly signOut = injectSignOut();

  protected readonly isHandset = toSignal(
    inject(BreakpointObserver)
      .observe([Breakpoints.Handset])
      .pipe(map((state) => state.matches)),
    { initialValue: false },
  );

  protected readonly links = computed(() => visibleTo(this.auth, SITE_LINKS));
  protected readonly categories = toSignal(inject(CatalogApi).categories(), { initialValue: [] });
  protected readonly searchOpen = signal(false);

  /** The avatar: the display name's initials (users have no picture). */
  protected readonly initials = computed(() =>
    (this.auth.user()?.displayName ?? '')
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((word) => word[0].toUpperCase())
      .join(''),
  );

  protected toggleSearch(): void {
    this.searchOpen.update((open) => !open);
  }
}
