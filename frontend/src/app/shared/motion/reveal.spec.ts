import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Reveal } from './reveal';

@Component({
  imports: [Reveal],
  template: `<section appReveal [appRevealDelay]="120">Hello</section>`,
})
class Host {}

describe('Reveal', () => {
  let callback: (entries: Partial<IntersectionObserverEntry>[]) => void;
  let reduceMotion: boolean;

  beforeEach(() => {
    reduceMotion = false;
    vi.stubGlobal(
      'matchMedia',
      (query: string) => ({ matches: query.includes('reduce') && reduceMotion }) as MediaQueryList,
    );
    vi.stubGlobal(
      'IntersectionObserver',
      class {
        constructor(cb: typeof callback) {
          callback = cb;
        }
        observe = vi.fn();
        unobserve = vi.fn();
      },
    );
  });

  afterEach(() => vi.unstubAllGlobals());

  async function render() {
    const fixture = TestBed.createComponent(Host);
    await fixture.whenStable();
    return fixture.nativeElement.querySelector('section') as HTMLElement;
  }

  it('hides the element until it scrolls in, then reveals it for good', async () => {
    const section = await render();
    expect(section.classList).toContain('mn-reveal');
    expect(section.classList).not.toContain('mn-revealed');
    expect(section.style.getPropertyValue('--mn-reveal-delay')).toBe('120ms');

    callback([{ target: section, isIntersecting: true }]);
    expect(section.classList).toContain('mn-revealed');
  });

  it('⭐ never hides anything under "reduce motion"', async () => {
    reduceMotion = true;
    const section = await render();
    expect(section.classList).not.toContain('mn-reveal');
  });
});
