/** One entry in the app navigation. `roles` empty = everyone (filtered by role in Phase 3.9). */
export interface NavItem {
  label: string;
  icon: string;
  link: string;
  /** Show only to signed-in users holding at least one of these roles; empty = always shown. */
  roles: readonly string[];
  /** Show only to signed-in users. */
  requiresAuth?: boolean;
}

/** The single source of truth for navigation — the sidenav and toolbar both render from it. */
export const NAV_ITEMS: readonly NavItem[] = [
  { label: 'Home', icon: 'home', link: '/', roles: [] },
  { label: 'Playground', icon: 'science', link: '/playground', roles: [] },
];
