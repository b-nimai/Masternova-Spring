import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Title } from '@angular/platform-browser';
import { provideRouter, Router, TitleStrategy } from '@angular/router';
import { PageTitle } from './page-title';

@Component({ template: '' })
class Blank {}

describe('PageTitle', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          {
            path: '',
            children: [
              { path: 'cart', title: 'Cart', component: Blank },
              {
                path: 'wizard',
                title: 'Edit course',
                component: Blank,
                data: { pageHeader: false },
              },
            ],
          },
          { path: 'untitled', component: Blank },
        ]),
        { provide: TitleStrategy, useExisting: PageTitle },
      ],
    });
  });

  it("names the tab and publishes the deepest route's title for the header", async () => {
    await TestBed.inject(Router).navigateByUrl('/cart');
    const page = TestBed.inject(PageTitle);

    expect(TestBed.inject(Title).getTitle()).toBe('Cart · Masternova');
    expect(page.title()).toBe('Cart');
    expect(page.showHeader()).toBe(true);
  });

  it('keeps the tab title but hides the header when the page draws its own', async () => {
    await TestBed.inject(Router).navigateByUrl('/wizard');
    const page = TestBed.inject(PageTitle);

    expect(TestBed.inject(Title).getTitle()).toBe('Edit course · Masternova');
    expect(page.showHeader()).toBe(false);
  });

  it('falls back to the brand for a route without a title', async () => {
    await TestBed.inject(Router).navigateByUrl('/untitled');

    expect(TestBed.inject(Title).getTitle()).toBe('Masternova');
    expect(TestBed.inject(PageTitle).title()).toBeUndefined();
  });
});
