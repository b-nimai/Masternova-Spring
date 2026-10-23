import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router, ViewTransitionInfo } from '@angular/router';
import { skipUnlessPageChanges } from './page-transitions';

@Component({ template: '' })
class Page {}

describe('skipUnlessPageChanges', () => {
  /** The router state after navigating to `url` (its root snapshot). */
  async function at(url: string) {
    await TestBed.inject(Router).navigateByUrl(url);
    return TestBed.inject(Router).routerState.snapshot.root;
  }

  async function decide(fromUrl: string, toUrl: string) {
    const from = await at(fromUrl);
    const to = await at(toUrl);
    const skipTransition = vi.fn();
    skipUnlessPageChanges({
      transition: { skipTransition },
      from,
      to,
    } as unknown as ViewTransitionInfo);
    return skipTransition;
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'courses', component: Page },
          { path: 'courses/:slug', component: Page },
        ]),
      ],
    });
  });

  it('animates a move to another page', async () => {
    expect(await decide('/courses', '/courses/k8s')).not.toHaveBeenCalled();
  });

  it('skips a filter (only the query changed)', async () => {
    expect(await decide('/courses', '/courses?level=BEGINNER')).toHaveBeenCalled();
  });
});
