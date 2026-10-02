import { FormControl, FormGroup } from '@angular/forms';
import { applyServerErrors, passwordsMatch, safeReturnUrl } from './form-utils';

describe('form utils', () => {
  it('passwordsMatch puts the error on the GROUP, only when both are filled and differ', () => {
    const form = new FormGroup(
      { password: new FormControl('secret-1'), confirm: new FormControl('') },
      { validators: passwordsMatch('password', 'confirm') },
    );
    expect(form.hasError('passwordMismatch')).toBe(false); // confirm not typed yet

    form.controls.confirm.setValue('secret-2');
    expect(form.hasError('passwordMismatch')).toBe(true);

    form.controls.confirm.setValue('secret-1');
    expect(form.hasError('passwordMismatch')).toBe(false);
  });

  it('applyServerErrors maps a 400 problem onto the matching controls', () => {
    const form = new FormGroup({ email: new FormControl('x'), password: new FormControl('y') });
    const applied = applyServerErrors(form, {
      status: 400,
      code: 'VALIDATION_FAILED',
      errors: [
        { field: 'email', code: 'Email', message: 'must be a well-formed email address' },
        { field: 'unknownField', code: 'X', message: 'ignored' },
      ],
    });

    expect(applied).toBe(true);
    expect(form.controls.email.getError('server')).toBe('must be a well-formed email address');
    expect(form.controls.password.errors).toBeNull();
  });

  it('applyServerErrors reports false when nothing matched', () => {
    const form = new FormGroup({ email: new FormControl('x') });
    expect(applyServerErrors(form, { status: 500 })).toBe(false);
    expect(applyServerErrors(form, null)).toBe(false);
  });

  it.each([
    ['/admin', '/admin'],
    ['/courses/1?tab=2', '/courses/1?tab=2'],
    ['https://evil.example', '/account'],
    ['//evil.example', '/account'], // protocol-relative → another site
    ['/\\evil.example', '/account'], // some browsers treat \ like /
    ['javascript:alert(1)', '/account'],
    [undefined, '/account'],
  ])('safeReturnUrl(%s) → %s', (input, expected) => {
    expect(safeReturnUrl(input)).toBe(expected);
  });
});
