import { HttpErrorResponse } from '@angular/common/http';
import { asProblem, fieldErrors } from './problem';

describe('problem helpers', () => {
  it('extracts the problem and its field errors from an HTTP error', () => {
    const error = new HttpErrorResponse({
      status: 400,
      error: {
        status: 400,
        code: 'VALIDATION_FAILED',
        errors: [
          { field: 'email', code: 'Email', message: 'Enter a valid email address.' },
          { field: 'email', code: 'Size', message: 'second problem, ignored' },
          { field: 'password', code: 'Size', message: 'Too short.' },
        ],
      },
    });

    const problem = asProblem(error);

    expect(problem?.code).toBe('VALIDATION_FAILED');
    expect(fieldErrors(problem)).toEqual({
      email: 'Enter a valid email address.',
      password: 'Too short.',
    });
  });

  it('is null for errors that are not problems', () => {
    expect(
      asProblem(new HttpErrorResponse({ status: 0, error: new ProgressEvent('error') })),
    ).toBeNull();
    expect(asProblem(new Error('boom'))).toBeNull();
    expect(fieldErrors(null)).toEqual({});
  });
});
