import { TestBed } from '@angular/core/testing';
import { CartStore } from './cart-store';
import { CourseSummary } from './course-catalog';

describe('CartStore (a signal store)', () => {
  const java: CourseSummary = {
    id: 'c2',
    title: 'Java',
    category: 'Backend',
    priceMinor: 99_900,
    rating: 4.9,
  };
  const docker: CourseSummary = {
    id: 'c5',
    title: 'Docker',
    category: 'DevOps',
    priceMinor: 0,
    rating: 4.2,
  };
  let cart: CartStore;

  beforeEach(() => {
    cart = TestBed.inject(CartStore);
  });

  it('derived values follow the state automatically', () => {
    cart.add(java);
    cart.add(docker);

    expect(cart.count()).toBe(2);
    expect(cart.totalMinor()).toBe(99_900);

    cart.remove('c2');
    expect(cart.count()).toBe(1); // computed() recalculated — nobody called "recalculate"
    expect(cart.totalMinor()).toBe(0);
  });

  it('adds each course only once', () => {
    cart.add(java);
    cart.add(java);

    expect(cart.courses()).toHaveLength(1);
    expect(cart.has('c2')).toBe(true);
  });

  it('exposes a read-only view', () => {
    // asReadonly() has no set/update — the store's methods are the only way to change it
    expect('set' in cart.courses).toBe(false);
  });
});
