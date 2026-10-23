import { TestBed } from '@angular/core/testing';
import { ScrollReveals } from './scroll-reveals';

describe('ScrollReveals', () => {
  let created: number;
  let fire: (entries: Partial<IntersectionObserverEntry>[]) => void;
  let unobserve: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    created = 0;
    unobserve = vi.fn();
    vi.stubGlobal(
      'IntersectionObserver',
      class {
        constructor(cb: typeof fire) {
          created++;
          fire = cb;
        }
        observe = vi.fn();
        unobserve = unobserve;
      },
    );
  });

  afterEach(() => vi.unstubAllGlobals());

  it('⭐ watches every element with ONE observer and lets each go once revealed', () => {
    const reveals = TestBed.inject(ScrollReveals);
    const a = document.createElement('div');
    const b = document.createElement('div');
    reveals.watch(a);
    reveals.watch(b);

    fire([
      { target: a, isIntersecting: true },
      { target: b, isIntersecting: false },
    ]);
    fire([{ target: a, isIntersecting: true }]); // a again: already let go

    expect(created).toBe(1);
    expect(a.classList).toContain('mn-revealed');
    expect(b.classList).not.toContain('mn-revealed');
    expect(unobserve).toHaveBeenCalledWith(a);
  });
});
