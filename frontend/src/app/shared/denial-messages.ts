import { DenialReason } from '../core/api/entitlement-api';

/**
 * What to tell a viewer for each denial (API conventions §10). Copy lives in the UI; the API sends
 * only the stable reason. ⭐ A `Record` over the union: a new reason is a compile error here until
 * it gets its sentence.
 */
export const DENIAL_MESSAGES: Record<DenialReason, string> = {
  NO_ENTITLEMENT: 'Buy this course to watch every lecture.',
  ENTITLEMENT_EXPIRED: 'Your access to this course has ended.',
  ENTITLEMENT_REVOKED:
    'Your access to this course was withdrawn (for example after a refund). Contact support if this is a mistake.',
  COURSE_NOT_PUBLISHED: 'This course is no longer available.',
  ACCESS_CHECK_FAILED: 'We could not check your access just now. Please try again.',
};

/** For strings from outside (a problem body, `?locked=`): is this one of OUR reasons? */
export function isDenialReason(value: unknown): value is DenialReason {
  return typeof value === 'string' && Object.hasOwn(DENIAL_MESSAGES, value);
}
