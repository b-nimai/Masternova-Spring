import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { AccessResponse } from '../../../core/api/entitlement-api';
import { CourseProgressResponse } from '../../../core/api/learning-api';
import { AuthStore } from '../../../core/auth/auth-store';
import { CART_MAX_ITEMS, CartStore } from '../../../core/commerce/cart-store';
import { JAVA } from '../../../../testing/commerce-fixtures';
import { AccessCta } from './access-cta';

describe('AccessCta', () => {
  let fixture: ComponentFixture<AccessCta>;
  let signedIn = false;

  beforeEach(async () => {
    signedIn = false;
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [AccessCta],
      providers: [
        provideRouter([]),
        { provide: AuthStore, useValue: { isAuthenticated: () => signedIn } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(AccessCta);
    fixture.componentRef.setInput('slug', 'k8s');
    fixture.componentRef.setInput('firstLectureId', 'l1');
    fixture.componentRef.setInput('offer', JAVA);
  });

  function show(access: AccessResponse): HTMLElement {
    fixture.componentRef.setInput('access', access);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('starts learning when allowed', () => {
    const el = show({ kind: 'ALLOWED', grant: 'ENTITLED' });
    const link = el.querySelector('a')!;
    expect(link.textContent).toContain('Start learning');
    expect(link.getAttribute('href')).toBe('/courses/k8s/lectures/l1');
  });

  it('asks a visitor to sign in, a member to buy', () => {
    expect(show({ kind: 'DENIED', reason: 'NO_ENTITLEMENT' }).textContent).toContain(
      'Sign in to buy',
    );
    signedIn = true;
    fixture.componentRef.setInput('access', { kind: 'ALLOWED', grant: 'ENTITLED' });
    fixture.detectChanges(); // re-render with the new auth state
    expect(show({ kind: 'DENIED', reason: 'NO_ENTITLEMENT' }).textContent).toContain('Buy now');
  });

  it('offers a renewal for expired access and only the truth after a refund', () => {
    expect(show({ kind: 'DENIED', reason: 'ENTITLEMENT_EXPIRED' }).textContent).toContain(
      'Renew access',
    );
    const revoked = show({ kind: 'DENIED', reason: 'ENTITLEMENT_REVOKED' });
    expect(revoked.querySelector('button, a')).toBeNull();
    expect(revoked.textContent).toContain('Contact support');
  });

  it('lets the page retry when the check itself failed', () => {
    let retried = 0;
    fixture.componentInstance.retry.subscribe(() => retried++);
    show({ kind: 'DENIED', reason: 'ACCESS_CHECK_FAILED' }).querySelector('button')!.click();
    expect(retried).toBe(1);
  });

  it('puts the course in the cart: Buy now goes there, Add to cart stays', () => {
    signedIn = true;
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const cart = TestBed.inject(CartStore);

    const el = show({ kind: 'DENIED', reason: 'NO_ENTITLEMENT' });
    el.querySelector<HTMLButtonElement>('[data-testid="add-to-cart"]')!.click();
    expect(cart.courseIds()).toEqual(['c-java']);
    expect(navigate).not.toHaveBeenCalled();

    fixture.detectChanges();
    expect(el.querySelector('[data-testid="go-to-cart"]')!.getAttribute('href')).toBe('/cart');

    cart.remove('c-java');
    fixture.detectChanges();
    el.querySelector<HTMLButtonElement>('[data-testid="buy-now"]')!.click();
    expect(cart.has('c-java')).toBe(true);
    expect(navigate).toHaveBeenCalledWith(['/cart']);
  });

  it('says the cart is full instead of silently not adding', () => {
    // review fix: "Buy now" stayed enabled, added nothing, and went to a cart without the course
    signedIn = true;
    const cart = TestBed.inject(CartStore);
    for (let i = 0; i < CART_MAX_ITEMS; i++) {
      cart.add({ ...JAVA, courseId: `other-${i}` });
    }
    const el = show({ kind: 'DENIED', reason: 'NO_ENTITLEMENT' });
    expect(el.querySelector<HTMLButtonElement>('[data-testid="buy-now"]')!.disabled).toBe(true);
    expect(el.querySelector('[data-testid="cart-full"]')).not.toBeNull();
  });

  it('renews expired access through the cart', () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    show({ kind: 'DENIED', reason: 'ENTITLEMENT_EXPIRED' })
      .querySelector<HTMLButtonElement>('[data-testid="renew"]')!
      .click();
    expect(TestBed.inject(CartStore).has('c-java')).toBe(true);
    expect(navigate).toHaveBeenCalledWith(['/cart']);
  });

  describe("with the learner's progress (Phase 10)", () => {
    const progress = (over: Partial<CourseProgressResponse>): CourseProgressResponse => ({
      courseId: '1',
      enrolled: true,
      percentComplete: 0,
      completedLectureIds: [],
      continueLectureId: 'l1',
      continuePositionSeconds: 0,
      ...over,
    });

    beforeEach(() => (signedIn = true));

    it('continues where the learner stopped', () => {
      fixture.componentRef.setInput(
        'progress',
        progress({ percentComplete: 25, continueLectureId: 'l2', continuePositionSeconds: 30 }),
      );
      const el = show({ kind: 'ALLOWED', grant: 'ENTITLED' });

      const watch = el.querySelector('[data-testid="watch"]')!;
      expect(watch.textContent).toContain('Continue learning');
      expect(watch.getAttribute('href')).toBe('/courses/k8s/lectures/l2');
      expect(el.querySelector('[data-testid="progress"]')!.textContent).toContain('25% complete');
    });

    it('starts a course enrolled but not yet watched', () => {
      fixture.componentRef.setInput('progress', progress({}));
      expect(show({ kind: 'ALLOWED', grant: 'ENTITLED' }).textContent).toContain('Start learning');
    });

    it('offers a free enrollment to a signed-in visitor of a free course', () => {
      fixture.componentRef.setInput('progress', progress({ enrolled: false }));
      const clicks: void[] = [];
      fixture.componentInstance.enroll.subscribe(() => clicks.push(undefined));
      const el = show({ kind: 'ALLOWED', grant: 'FREE_COURSE' });

      el.querySelector<HTMLButtonElement>('[data-testid="enroll"]')!.click();
      expect(clicks).toHaveLength(1);

      fixture.componentRef.setInput('enrolling', true);
      fixture.detectChanges();
      expect(el.querySelector<HTMLButtonElement>('[data-testid="enroll"]')!.disabled).toBe(true);
    });

    it('lets a finished course be reviewed', () => {
      fixture.componentRef.setInput(
        'progress',
        progress({ percentComplete: 100, continueLectureId: null }),
      );
      const el = show({ kind: 'ALLOWED', grant: 'ENTITLED' });
      expect(el.querySelector('[data-testid="review"]')!.getAttribute('href')).toBe(
        '/courses/k8s/lectures/l1',
      );
      expect(el.querySelector('[data-testid="completed"]')).not.toBeNull();
    });
  });
});
