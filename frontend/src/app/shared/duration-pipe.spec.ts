import { DurationPipe } from './duration-pipe';

describe('DurationPipe', () => {
  const pipe = new DurationPipe();

  it('writes course totals in hours and minutes', () => {
    expect(pipe.transform(5100)).toBe('1h 25m');
    expect(pipe.transform(3660)).toBe('1h 01m');
    expect(pipe.transform(420)).toBe('7m');
    expect(pipe.transform(45)).toBe('45s');
  });

  it('writes one lecture as a clock', () => {
    expect(pipe.transform(420, 'clock')).toBe('7:00');
    expect(pipe.transform(3723, 'clock')).toBe('1:02:03');
    expect(pipe.transform(5, 'clock')).toBe('0:05');
  });
});
