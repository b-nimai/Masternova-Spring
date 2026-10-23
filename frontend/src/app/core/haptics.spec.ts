import { TestBed } from '@angular/core/testing';
import { Haptics } from './haptics';

describe('Haptics', () => {
  let vibrate: ReturnType<typeof vi.fn>;
  let reduceMotion: boolean;

  beforeEach(() => {
    vibrate = vi.fn(() => true);
    reduceMotion = false;
    Object.defineProperty(navigator, 'vibrate', { value: vibrate, configurable: true });
    vi.stubGlobal('matchMedia', () => ({ matches: reduceMotion }) as MediaQueryList);
  });

  afterEach(() => {
    Reflect.deleteProperty(navigator, 'vibrate');
    vi.unstubAllGlobals();
  });

  it('plays a distinct pattern for a tap, a success and an error', () => {
    const haptics = TestBed.inject(Haptics);
    haptics.tap();
    haptics.success();
    haptics.error();

    expect(vibrate.mock.calls).toEqual([[10], [[14, 70, 22]], [[40, 60, 40]]]);
  });

  it('stays still under "reduce motion", and without the API (iPhone Safari) it is a no-op', () => {
    reduceMotion = true;
    TestBed.inject(Haptics).success();
    expect(vibrate).not.toHaveBeenCalled();

    Reflect.deleteProperty(navigator, 'vibrate');
    reduceMotion = false;
    expect(() => TestBed.inject(Haptics).success()).not.toThrow();
  });

  it('swallows a refused vibration (no user gesture)', () => {
    vibrate.mockImplementation(() => {
      throw new Error('blocked');
    });
    expect(() => TestBed.inject(Haptics).tap()).not.toThrow();
  });
});
