import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { CatalogApi } from '../../api/catalog-api';

/** One footer column. */
interface FooterColumn {
  heading: string;
  links: readonly { label: string; link: string }[];
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
  protected readonly columns: readonly FooterColumn[] = [
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

  protected readonly categories = toSignal(inject(CatalogApi).categories(), { initialValue: [] });
  protected readonly year = new Date().getFullYear();
}
