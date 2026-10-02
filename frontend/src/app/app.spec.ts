import { BreakpointObserver } from '@angular/cdk/layout';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { BehaviorSubject, of } from 'rxjs';
import { App } from './app';
import { AuthApi, TokenResponse } from './core/auth/auth-api';
import { AuthStore } from './core/auth/auth-store';

describe('App shell', () => {
  const adminTokens: TokenResponse = {
    accessToken: 't',
    tokenType: 'Bearer',
    expiresIn: 900,
    user: {
      id: 'u1',
      email: 'a@x.dev',
      displayName: 'Ada',
      roles: ['LEARNER', 'ADMIN'],
      emailVerified: true,
    },
  };
  const handset = new BehaviorSubject({ matches: false, breakpoints: {} });

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter([]),
        // ⭐ control the "screen size" from the test
        { provide: BreakpointObserver, useValue: { observe: () => handset } },
        {
          provide: AuthApi,
          useValue: { login: () => of(adminTokens), logout: () => of(undefined) },
        },
      ],
    }).compileComponents();
  });

  const navLabels = (el: HTMLElement) =>
    Array.from(el.querySelectorAll('mat-nav-list a')).map((a) => a.textContent?.trim());

  it('shows public navigation and the log-in buttons to anonymous visitors', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;

    expect(el.querySelector('.brand')?.textContent).toContain('Masternova');
    expect(navLabels(el)).toEqual(['homeHome', 'schoolCourses', 'sciencePlayground']);
    expect(el.textContent).toContain('Log in');
  });

  it('shows account and admin entries to a signed-in admin (role-based UI)', async () => {
    TestBed.inject(AuthStore).login('a@x.dev', 'pw').subscribe();
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;

    expect(navLabels(el)).toContain('personMy account');
    expect(navLabels(el)).toContain('admin_panel_settingsAdmin');
    expect(el.querySelector('[data-testid="user-menu"]')?.textContent).toContain('Ada');
  });

  it('shows the menu button only on handsets', async () => {
    const fixture = TestBed.createComponent(App);
    handset.next({ matches: false, breakpoints: {} });
    await fixture.whenStable();
    expect(
      (fixture.nativeElement as HTMLElement).querySelector('[aria-label="Open navigation"]'),
    ).toBeNull();

    handset.next({ matches: true, breakpoints: {} });
    await fixture.whenStable();
    expect(
      (fixture.nativeElement as HTMLElement).querySelector('[aria-label="Open navigation"]'),
    ).not.toBeNull();
  });
});
