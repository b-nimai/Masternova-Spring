import { BreakpointObserver, Breakpoints } from '@angular/cdk/layout';
import { NgTemplateOutlet } from '@angular/common';
import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { map } from 'rxjs';
import { AuthStore } from '../../auth/auth-store';
import { injectSignOut } from '../../auth/sign-out';
import { DASHBOARD_ITEMS, DASHBOARD_SETTINGS, visibleTo } from '../nav-items';
import { PageTitle } from '../page-title';

/**
 * The signed-in area's layout (Figma "Account Details" and its siblings): a 222 px sidebar, a
 * breadcrumb + title header, then the page. On a handset the sidebar becomes a scrolling strip
 * above the page.
 */
@Component({
  selector: 'app-dashboard-layout',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, NgTemplateOutlet, MatIconModule],
  templateUrl: './dashboard-layout.html',
  styleUrl: './dashboard-layout.scss',
})
export class DashboardLayout {
  private readonly auth = inject(AuthStore);
  protected readonly page = inject(PageTitle);
  protected readonly signOut = injectSignOut();

  protected readonly isHandset = toSignal(
    inject(BreakpointObserver)
      .observe([Breakpoints.Handset])
      .pipe(map((state) => state.matches)),
    { initialValue: false },
  );

  protected readonly items = computed(() => visibleTo(this.auth, DASHBOARD_ITEMS));
  protected readonly settings = computed(() => visibleTo(this.auth, DASHBOARD_SETTINGS));
}
