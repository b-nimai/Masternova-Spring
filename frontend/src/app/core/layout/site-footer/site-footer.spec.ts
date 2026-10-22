import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { CatalogApi } from '../../api/catalog-api';
import { SiteFooter } from './site-footer';

describe('SiteFooter', () => {
  const render = async (categories: { slug: string; name: string; children: [] }[]) => {
    await TestBed.configureTestingModule({
      imports: [SiteFooter],
      providers: [
        provideRouter([]),
        { provide: CatalogApi, useValue: { categories: () => of(categories) } },
      ],
    }).compileComponents();
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

  it('drops the subjects column when there are no categories', async () => {
    const el = await render([]);

    expect(el.querySelector('.subjects')).toBeNull();
  });
});
