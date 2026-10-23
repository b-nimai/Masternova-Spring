import { BreakpointObserver } from '@angular/cdk/layout';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { BehaviorSubject, of } from 'rxjs';
import { CatalogApi } from '../../api/catalog-api';
import { AuthApi, TokenResponse } from '../../auth/auth-api';
import { AuthStore } from '../../auth/auth-store';
import { NavBar } from './nav-bar';

describe('NavBar', () => {
  const tokens: TokenResponse = {
    accessToken: 't',
    tokenType: 'Bearer',
    expiresIn: 900,
    user: {
      id: 'u1',
      email: 'a@x.dev',
      displayName: 'Ada Lovelace',
      roles: ['LEARNER'],
      emailVerified: true,
    },
  };
  const handset = new BehaviorSubject({ matches: false, breakpoints: {} });

  beforeEach(async () => {
    handset.next({ matches: false, breakpoints: {} });
    await TestBed.configureTestingModule({
      imports: [NavBar],
      providers: [
        provideRouter([]),
        // ⭐ control the "screen size" from the test
        { provide: BreakpointObserver, useValue: { observe: () => handset } },
        { provide: AuthApi, useValue: { login: () => of(tokens), logout: () => of(undefined) } },
        {
          provide: CatalogApi,
          useValue: { categories: () => of([{ slug: 'web', name: 'Web', children: [] }]) },
        },
      ],
    }).compileComponents();
  });

  const render = async () => {
    const fixture = TestBed.createComponent(NavBar);
    await fixture.whenStable();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  };

  it('offers log in and sign up to anonymous visitors, and no learning link', async () => {
    const { el } = await render();

    expect(el.querySelector('.brand')?.textContent).toContain('Masternova');
    expect(el.textContent).toContain('Log in');
    expect(el.textContent).toContain('Sign up');
    expect(el.textContent).not.toContain('My Learning');
    expect(el.querySelector('[data-testid="user-menu"]')).toBeNull();
  });

  it("shows a signed-in user's initials and their learning link", async () => {
    TestBed.inject(AuthStore).login('a@x.dev', 'pw').subscribe();
    const { el } = await render();

    const avatar = el.querySelector('[data-testid="user-menu"]');
    expect(avatar?.textContent).toContain('AL');
    expect(avatar?.textContent).toContain('Ada Lovelace'); // for screen readers
    expect(el.textContent).toContain('My Learning');
    expect(el.textContent).not.toContain('Log in');
  });

  it('lists the categories in the Catalog menu', async () => {
    const { fixture, el } = await render();
    (el.querySelector('[data-testid="catalog-menu"]') as HTMLButtonElement).click();
    await fixture.whenStable();

    const items = Array.from(document.querySelectorAll('.catalog-menu a')).map((a) =>
      a.textContent?.trim(),
    );
    expect(items).toEqual(['All courses', 'Web']);
  });

  it('opens the search box only when asked', async () => {
    const { fixture, el } = await render();
    expect(el.querySelector('app-search-box')).toBeNull();

    (el.querySelector('[data-testid="search-toggle"]') as HTMLButtonElement).click();
    await fixture.whenStable();
    expect(el.querySelector('app-search-box')).not.toBeNull();
  });

  it('collapses the links into a menu on handsets', async () => {
    const { fixture, el } = await render();
    expect(el.querySelector('[aria-label="Open navigation"]')).toBeNull();

    handset.next({ matches: true, breakpoints: {} });
    await fixture.whenStable();
    expect(el.querySelector('[aria-label="Open navigation"]')).not.toBeNull();
    expect(el.querySelector('[data-testid="catalog-menu"]')).toBeNull();
  });
});
