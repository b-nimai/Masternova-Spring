import { Role } from '../auth/auth-api';

/** One entry in the app navigation. */
export interface NavItem {
  label: string;
  icon: string;
  link: string;
  /** Show only to users holding at least one of these roles; empty = no role needed. */
  roles: readonly Role[];
  /** Show only to signed-in users. */
  requiresAuth?: boolean;
}

/** The single source of truth for navigation — filtered per user by the shell (role-based UI). */
export const NAV_ITEMS: readonly NavItem[] = [
  { label: 'Home', icon: 'home', link: '/', roles: [] },
  { label: 'Courses', icon: 'school', link: '/courses', roles: [] },
  { label: 'Playground', icon: 'science', link: '/playground', roles: [] },
  {
    label: 'Teach',
    icon: 'cast_for_education',
    link: '/instructor',
    roles: ['INSTRUCTOR', 'ADMIN'],
    requiresAuth: true,
  },
  { label: 'My account', icon: 'person', link: '/account', roles: [], requiresAuth: true },
  {
    label: 'Admin',
    icon: 'admin_panel_settings',
    link: '/admin',
    roles: ['ADMIN'],
    requiresAuth: true,
  },
];
