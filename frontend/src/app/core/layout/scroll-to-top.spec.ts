import { ViewportScroller } from '@angular/common';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { scrollToTopOnPageChange } from './scroll-to-top';

@Component({ template: '' })
class Page {}

describe('scrollToTopOnPageChange', () => {
  let scrollTo: ReturnType<typeof vi.fn>;
  let scrollToAnchor: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    scrollTo = vi.fn();
    scrollToAnchor = vi.fn();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'courses', component: Page },
          { path: 'courses/:slug', component: Page },
        ]),
        { provide: ViewportScroller, useValue: { scrollToPosition: scrollTo, scrollToAnchor } },
      ],
    });
    TestBed.runInInjectionContext(() => scrollToTopOnPageChange());
    await TestBed.inject(Router).navigateByUrl('/courses');
  });

  it('scrolls to the top when the page changes', async () => {
    await TestBed.inject(Router).navigateByUrl('/courses/k8s');
    expect(scrollTo).toHaveBeenCalledWith([0, 0], { behavior: 'instant' });
  });

  it('keeps the place when only the query changes (a catalog filter)', async () => {
    await TestBed.inject(Router).navigateByUrl('/courses?level=BEGINNER');
    expect(scrollTo).not.toHaveBeenCalled();
  });

  it('scrolls to a #fragment once the page has rendered, not to the top', async () => {
    await TestBed.inject(Router).navigateByUrl('/courses/k8s#teach');
    expect(scrollToAnchor).not.toHaveBeenCalled(); // the anchor isn't drawn yet
    TestBed.tick();

    expect(scrollToAnchor).toHaveBeenCalledWith('teach');
    expect(scrollTo).not.toHaveBeenCalled();
  });
});
