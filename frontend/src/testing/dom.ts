import { ComponentFixture } from '@angular/core/testing';

/** Tiny DOM helpers so specs drive pages like a user does: type into inputs, submit forms. */
export function type(
  fixture: ComponentFixture<unknown>,
  formControlName: string,
  value: string,
): void {
  const input = query<HTMLInputElement>(fixture, `[formControlName="${formControlName}"]`);
  input.value = value;
  input.dispatchEvent(new Event('input'));
  input.dispatchEvent(new Event('blur'));
}

export function submit(fixture: ComponentFixture<unknown>, index = 0): void {
  const forms = (fixture.nativeElement as HTMLElement).querySelectorAll('form');
  forms[index].dispatchEvent(new Event('submit'));
}

export function text(fixture: ComponentFixture<unknown>, testId: string): string {
  return (
    (fixture.nativeElement as HTMLElement)
      .querySelector(`[data-testid="${testId}"]`)
      ?.textContent?.trim() ?? ''
  );
}

export function query<T extends Element>(fixture: ComponentFixture<unknown>, selector: string): T {
  const el = (fixture.nativeElement as HTMLElement).querySelector<T>(selector);
  if (!el) {
    throw new Error(`no element matches ${selector}`);
  }
  return el;
}
