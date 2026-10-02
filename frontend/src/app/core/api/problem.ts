import { HttpErrorResponse } from '@angular/common/http';

/** One field problem in a 400 — the backend's ValidationException.FieldError. */
export interface FieldProblem {
  field: string;
  code: string;
  message: string;
}

/**
 * An RFC 9457 problem as the Masternova API sends it (docs/api/conventions.md §1). The `code` is
 * the stable contract — branch on it, never on `detail` (that's copy and may change).
 */
export interface Problem {
  type?: string;
  title?: string;
  status: number;
  detail?: string;
  code?: string;
  errors?: FieldProblem[];
  reason?: string;
  [extension: string]: unknown;
}

/** The problem inside an HTTP error, or null if the error isn't one (network down, etc.). */
export function asProblem(error: unknown): Problem | null {
  if (
    error instanceof HttpErrorResponse &&
    error.error &&
    typeof error.error === 'object' &&
    'status' in error.error
  ) {
    return error.error as Problem;
  }
  return null;
}

/** `{ email: 'Enter a valid email address.' }` — ready to attach to form controls. */
export function fieldErrors(problem: Problem | null): Record<string, string> {
  const result: Record<string, string> = {};
  for (const e of problem?.errors ?? []) {
    result[e.field] ??= e.message; // first problem per field wins
  }
  return result;
}
