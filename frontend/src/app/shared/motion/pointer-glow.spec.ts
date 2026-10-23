import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { PointerGlow } from './pointer-glow';

@Component({ imports: [PointerGlow], template: `<div appPointerGlow>card</div>` })
class Host {}

describe('PointerGlow', () => {
  let media: Record<string, boolean>;

  beforeEach(() => {
    vi.stubGlobal(
      'matchMedia',
      (query: string) => ({ matches: media[query] ?? false }) as MediaQueryList,
    );
    vi.stubGlobal('requestAnimationFrame', (draw: FrameRequestCallback) => {
      draw(0);
      return 1;
    });
  });

  afterEach(() => vi.unstubAllGlobals());

  async function render() {
    const fixture = TestBed.createComponent(Host);
    await fixture.whenStable();
    return fixture.nativeElement.querySelector('div') as HTMLElement;
  }

  it('follows a fine pointer with two CSS variables', async () => {
    media = { '(hover: hover) and (pointer: fine)': true };
    const card = await render();
    card.getBoundingClientRect = () => ({ left: 100, top: 50 }) as DOMRect;

    card.dispatchEvent(new PointerEvent('pointermove', { clientX: 130, clientY: 90 }));

    expect(card.classList).toContain('mn-pointer-glow');
    expect(card.style.getPropertyValue('--mn-pointer-x')).toBe('30px');
    expect(card.style.getPropertyValue('--mn-pointer-y')).toBe('40px');
  });

  it('does nothing on a touch screen or under "reduce motion"', async () => {
    media = {
      '(hover: hover) and (pointer: fine)': true,
      '(prefers-reduced-motion: reduce)': true,
    };
    expect((await render()).classList).not.toContain('mn-pointer-glow');
    media = {};
    expect((await render()).classList).not.toContain('mn-pointer-glow');
  });
});
