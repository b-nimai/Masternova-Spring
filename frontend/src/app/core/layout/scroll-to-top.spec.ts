import { ViewportScroller } from '@angular/common';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { scrollToTopOnPageChange } from './scroll-to-top';

@Component({ template: '' })
class Page {}

describe('scrollToTopOnPageChange', () => {
  let scrollTo: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    scrollTo = vi.fn();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'courses', component: Page },
          { path: 'courses/:slug', component: Page },
        ]),
        { provide: ViewportScroller, useValue: { scrollToPosition: scrollTo } },
      ],
    });
    TestBed.runInInjectionContext(() => scrollToTopOnPageChange());
    await TestBed.inject(Router).navigateByUrl('/courses');
  });

  it('scrolls to the top when the page changes', async () => {
    await TestBed.inject(Router).navigateByUrl('/courses/k8s');
    expect(scrollTo).toHaveBeenCalledWith([0, 0]);
  });

  it('keeps the place when only the query changes (a catalog filter)', async () => {
    await TestBed.inject(Router).navigateByUrl('/courses?level=BEGINNER');
    expect(scrollTo).not.toHaveBeenCalled();
  });

  it('leaves a #fragment to the page that owns the anchor', async () => {
    await TestBed.inject(Router).navigateByUrl('/courses/k8s#reviews');
    expect(scrollTo).not.toHaveBeenCalled();
  });
});
