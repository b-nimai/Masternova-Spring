import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, of } from 'rxjs';
import { CatalogApi } from '../../api/catalog-api';
import { Role } from '../../auth/auth-api';
import { AuthStore } from '../../auth/auth-store';

/** One footer column. */
interface FooterColumn {
  heading: string;
  links: readonly { label: string; link: string }[];
  /** Shown only to these roles (the routes are role-guarded); left out = everyone. */
  roles?: readonly Role[];
}

/**
 * The public pages' footer (Figma "Footer"). The Figma's columns are StudyNotion's marketing
 * pages; ours link to what Masternova has, and "Subjects" is the real category list.
 */
@Component({
  selector: 'app-site-footer',
  imports: [RouterLink],
  templateUrl: './site-footer.html',
  styleUrl: './site-footer.scss',
})
export class SiteFooter {
  private readonly auth = inject(AuthStore);

  private readonly allColumns: readonly FooterColumn[] = [
    {
      heading: 'Learn',
      links: [
        { label: 'All courses', link: '/courses' },
        { label: 'Search', link: '/search' },
        { label: 'My Learning', link: '/learning' },
      ],
    },
    {
      heading: 'Teach',
      roles: ['INSTRUCTOR', 'ADMIN'],
      links: [
        { label: 'Your courses', link: '/instructor' },
        { label: 'Create a course', link: '/instructor/courses/new' },
      ],
    },
    {
      heading: 'Account',
      links: [
        { label: 'Dashboard', link: '/account' },
        { label: 'Purchase history', link: '/account/orders' },
        { label: 'Email preferences', link: '/account/notifications' },
      ],
    },
  ];

  /** ⭐ Role-based UI, as in the nav: a learner never sees links a guard would bounce. */
  protected readonly columns = computed(() =>
    this.allColumns.filter((c) => !c.roles || this.auth.hasAnyRole(c.roles)),
  );

  /** A failed request just leaves the Subjects column out (toSignal would rethrow). */
  protected readonly categories = toSignal(
    inject(CatalogApi)
      .categories()
      .pipe(catchError(() => of([]))),
    { initialValue: [] },
  );
  protected readonly year = new Date().getFullYear();
}
