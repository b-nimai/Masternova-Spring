import { BreakpointObserver, Breakpoints } from '@angular/cdk/layout';
import {
  afterRenderEffect,
  Component,
  computed,
  ElementRef,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatBadgeModule } from '@angular/material/badge';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { CatalogApi } from '../../api/catalog-api';
import { AuthStore } from '../../auth/auth-store';
import { injectSignOut } from '../../auth/sign-out';
import { CartStore } from '../../commerce/cart-store';
import { SearchBox } from '../../../shared/search-box/search-box';
import { SITE_LINKS, visibleTo } from '../nav-items';
import { initialsOf } from '../../../shared/initials';
import { prefersReducedMotion } from '../../../shared/motion/reduced-motion';

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
  /** A failed request leaves the Catalog menu with "All courses" only (toSignal would rethrow). */
  protected readonly categories = toSignal(
    inject(CatalogApi)
      .categories()
      .pipe(catchError(() => of([]))),
    { initialValue: [] },
  );
  protected readonly searchOpen = signal(false);

  private readonly cartLink = viewChild('cartLink', { read: ElementRef<HTMLElement> });

  constructor() {
    // The cart badge bumps when a course goes in: the eye catches the number that changed.
    // After render, so the badge shows its new number while it grows (Web Animations: no CSS
    // class to remove afterwards).
    let previous = this.cart.count();
    afterRenderEffect(() => {
      const count = this.cart.count();
      if (count > previous && !prefersReducedMotion()) {
        this.cartLink()
          ?.nativeElement.querySelector('.mat-badge-content')
          ?.animate(
            [{ transform: 'scale(1)' }, { transform: 'scale(1.45)' }, { transform: 'scale(1)' }],
            {
              duration: 420,
              easing: 'cubic-bezier(0.22, 1, 0.36, 1)',
            },
          );
      }
      previous = count;
    });
  }

  /** The avatar: the display name's initials (users have no picture). */
  protected readonly initials = computed(() => initialsOf(this.auth.user()?.displayName ?? ''));

  protected toggleSearch(): void {
    this.searchOpen.update((open) => !open);
  }
}
