import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { AuthApi, Role } from '../../auth/auth-api';
import { AuthStore } from '../../auth/auth-store';
import { CatalogApi } from '../../api/catalog-api';
import { SiteFooter } from './site-footer';

describe('SiteFooter', () => {
  const render = async (
    categories: { slug: string; name: string; children: [] }[] | 'error',
    roles?: Role[],
  ) => {
    await TestBed.configureTestingModule({
      imports: [SiteFooter],
      providers: [
        provideRouter([]),
        {
          provide: CatalogApi,
          useValue: {
            categories: () => (categories === 'error' ? throwError(() => 500) : of(categories)),
          },
        },
        {
          provide: AuthApi,
          useValue: {
            login: () =>
              of({
                accessToken: 't',
                tokenType: 'Bearer',
                expiresIn: 900,
                user: { id: 'u', email: 'a@x.dev', displayName: 'A', roles, emailVerified: true },
              }),
          },
        },
      ],
    }).compileComponents();
    if (roles) {
      TestBed.inject(AuthStore).login('a@x.dev', 'pw').subscribe();
    }
    const fixture = TestBed.createComponent(SiteFooter);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  };

  it('lists the real categories as subjects, each linking to its catalog filter', async () => {
    const el = await render([{ slug: 'data-science', name: 'Data Science', children: [] }]);

    const subject = el.querySelector<HTMLAnchorElement>('.subjects a');
    expect(subject?.textContent?.trim()).toBe('Data Science');
    expect(subject?.getAttribute('href')).toBe('/courses?category=data-science');
  });

  it('shows the Teach column to instructors only (its routes are role-guarded)', async () => {
    const headings = (el: HTMLElement) =>
      Array.from(el.querySelectorAll('.column h2')).map((h) => h.textContent);

    expect(headings(await render([], ['LEARNER']))).not.toContain('Teach');
    TestBed.resetTestingModule();
    expect(headings(await render([], ['INSTRUCTOR']))).toContain('Teach');
  });

  it('still renders when the categories request fails', async () => {
    const el = await render('error');

    expect(el.querySelector('.subjects')).toBeNull();
    expect(el.textContent).toContain('All courses');
  });

  it('drops the subjects column when there are no categories', async () => {
    const el = await render([]);

    expect(el.querySelector('.subjects')).toBeNull();
  });
});
