import { Role } from '../auth/auth-api';
import { AuthStore } from '../auth/auth-store';

/** One link in the app's navigation. */
export interface NavItem {
  label: string;
  icon: string;
  link: string;
  /** Show only to users holding at least one of these roles; empty = no role needed. */
  roles: readonly Role[];
  /** Show only to signed-in users. */
  requiresAuth?: boolean;
}

/** The top bar's links after Home and the Catalog menu (which is built from the categories). */
export const SITE_LINKS: readonly NavItem[] = [
  { label: 'My Learning', icon: 'menu_book', link: '/learning', roles: [], requiresAuth: true },
];

/** The dashboard sidebar's main group (Figma frame 3273). */
export const DASHBOARD_ITEMS: readonly NavItem[] = [
  { label: 'My Profile', icon: 'person', link: '/account', roles: [] },
  { label: 'Enrolled Courses', icon: 'menu_book', link: '/learning', roles: [] },
  { label: 'Cart', icon: 'shopping_cart', link: '/cart', roles: [] },
  { label: 'Purchase History', icon: 'receipt_long', link: '/account/orders', roles: [] },
  { label: 'My Courses', icon: 'school', link: '/instructor', roles: ['INSTRUCTOR', 'ADMIN'] },
  { label: 'Admin', icon: 'admin_panel_settings', link: '/admin', roles: ['ADMIN'] },
];

/** Below the sidebar's divider (Log Out follows them as a button). */
export const DASHBOARD_SETTINGS: readonly NavItem[] = [
  { label: 'Settings', icon: 'settings', link: '/account/notifications', roles: [] },
];

/**
 * ⭐ ROLE-BASED UI: entries the user can't use aren't shown. This is UX only — the API enforces
 * every rule again (deny-by-default + @PreAuthorize), so hiding a link is never the security.
 * Call it inside a `computed` so the list follows sign-in and sign-out.
 */
export function visibleTo(auth: AuthStore, items: readonly NavItem[]): NavItem[] {
  return items.filter(
    (item) =>
      (!item.requiresAuth || auth.isAuthenticated()) &&
      (item.roles.length === 0 || auth.hasAnyRole(item.roles)),
  );
}
