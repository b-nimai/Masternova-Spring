import { BreakpointObserver } from '@angular/cdk/layout';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router, TitleStrategy } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of } from 'rxjs';
import { AuthApi, Role, TokenResponse } from '../../auth/auth-api';
import { AuthStore } from '../../auth/auth-store';
import { PageTitle } from '../page-title';
import { DashboardLayout } from './dashboard-layout';

@Component({ template: '<p>page</p>' })
class Page {}

describe('DashboardLayout', () => {
  const tokens = (roles: Role[]): TokenResponse => ({
    accessToken: 't',
    tokenType: 'Bearer',
    expiresIn: 900,
    user: { id: 'u1', email: 'a@x.dev', displayName: 'Ada', roles, emailVerified: true },
  });

  const setUp = async (roles: Role[]) => {
    await TestBed.configureTestingModule({
      providers: [
        provideRouter([
          {
            path: '',
            component: DashboardLayout,
            children: [
              { path: 'cart', title: 'Cart', component: Page },
              { path: 'wizard', title: 'Edit', component: Page, data: { pageHeader: false } },
            ],
          },
        ]),
        { provide: TitleStrategy, useExisting: PageTitle },
        { provide: BreakpointObserver, useValue: { observe: () => of({ matches: false }) } },
        {
          provide: AuthApi,
          useValue: { login: () => of(tokens(roles)), logout: () => of(undefined) },
        },
      ],
    }).compileComponents();
    TestBed.inject(AuthStore).login('a@x.dev', 'pw').subscribe();
  };

  /** ⭐ The harness renders the route like the app does: the layout, its child in the outlet. */
  const open = async (url: string) => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url);
    return harness.routeNativeElement as HTMLElement;
  };

  const sidebar = (el: HTMLElement) =>
    Array.from(el.querySelectorAll('.sidebar .item')).map((a) =>
      a.textContent?.replace(/^\s*\w+\s+/, '').trim(),
    );

  it("shows a learner the learner's entries only (role-based UI)", async () => {
    await setUp(['LEARNER']);
    const el = await open('/cart');

    expect(sidebar(el)).toEqual([
      'My Profile',
      'Enrolled Courses',
      'Cart',
      'Purchase History',
      'Settings',
      'Log Out',
    ]);
  });

  it('adds the instructor and admin entries for an admin', async () => {
    await setUp(['LEARNER', 'ADMIN']);
    const el = await open('/cart');

    expect(sidebar(el)).toContain('My Courses');
    expect(sidebar(el)).toContain('Admin');
  });

  it("titles the page from the route, in the header and the breadcrumb's last step", async () => {
    await setUp(['LEARNER']);
    const el = await open('/cart');

    expect(el.querySelector('.page-header h1')?.textContent).toBe('Cart');
    expect(el.querySelector('[aria-current="page"]')?.textContent).toBe('Cart');
  });

  it('logs out from the sidebar and goes home', async () => {
    await setUp(['LEARNER']);
    const el = await open('/cart');
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    el.querySelector<HTMLButtonElement>('[data-testid="sidebar-logout"]')!.click();

    expect(TestBed.inject(AuthStore).isAuthenticated()).toBe(false);
    expect(navigate).toHaveBeenCalledWith(['/']);
  });

  it('leaves the heading to a page that draws its own', async () => {
    await setUp(['LEARNER']);
    const el = await open('/wizard');

    expect(el.querySelector('.page-header')).toBeNull();
  });
});
