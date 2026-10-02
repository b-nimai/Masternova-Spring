import { LOCALE_ID } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MoneyPipe } from './money-pipe';

describe('MoneyPipe', () => {
  let pipe: MoneyPipe;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [{ provide: LOCALE_ID, useValue: 'en-US' }] });
    pipe = TestBed.runInInjectionContext(() => new MoneyPipe());
  });

  it('formats minor units with the currency’s own digits', () => {
    expect(pipe.transform(149900, 'INR')).toBe('₹1,499.00');
    expect(pipe.transform(500, 'JPY')).toBe('¥500'); // no minor unit: not ¥5.00
  });

  it('calls zero free', () => {
    expect(pipe.transform(0, 'INR')).toBe('Free');
  });
});
