import { TestBed } from '@angular/core/testing';
import { JAVA, SPRING } from '../../../testing/commerce-fixtures';
import { CART_MAX_ITEMS, CartStore } from './cart-store';

describe('CartStore', () => {
  beforeEach(() => localStorage.clear());

  function fresh(): CartStore {
    TestBed.resetTestingModule();
    return TestBed.inject(CartStore);
  }

  it('holds each course once', () => {
    const cart = fresh();
    cart.add(JAVA);
    cart.add(JAVA);
    cart.add(SPRING);
    expect(cart.count()).toBe(2);
    expect(cart.courseIds()).toEqual(['c-java', 'c-spring']);
    expect(cart.has('c-java')).toBe(true);
  });

  it('survives a reload (localStorage)', () => {
    fresh().add(JAVA);
    expect(fresh().items()).toEqual([JAVA]);
  });

  it('drops only what was bought', () => {
    const cart = fresh();
    cart.add(JAVA);
    cart.add(SPRING);
    cart.removeAll(['c-java']);
    expect(cart.courseIds()).toEqual(['c-spring']);
    cart.remove('c-spring');
    expect(cart.count()).toBe(0);
  });

  it('ignores garbage in storage instead of crashing', () => {
    localStorage.setItem('masternova.cart.v1', '{"not": "an array"}');
    expect(fresh().count()).toBe(0);
    localStorage.setItem('masternova.cart.v1', JSON.stringify([JAVA, { courseId: 1 }]));
    expect(fresh().items()).toEqual([JAVA]);
  });

  it('stops at the server’s limit', () => {
    const cart = fresh();
    for (let i = 0; i < CART_MAX_ITEMS + 3; i++) {
      cart.add({ ...JAVA, courseId: `c${i}` });
    }
    expect(cart.count()).toBe(CART_MAX_ITEMS);
    expect(cart.isFull()).toBe(true);
  });
});
