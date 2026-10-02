import { HttpClient } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { Observable } from 'rxjs';

/** Mirrors the kernel enum `NotificationCategory` (order = the API's order). */
export type NotificationCategory =
  'ACCOUNT_SECURITY' | 'PURCHASE' | 'COURSE_ACTIVITY' | 'ENGAGEMENT' | 'PRODUCT_NEWS';

/** Mirrors `NotificationPreferenceResponse`. */
export interface NotificationPreferenceResponse {
  category: NotificationCategory;
  enabled: boolean;
  mandatory: boolean;
}

/** Mirrors `UnsubscribeResponse`. */
export interface UnsubscribeResponse {
  category: NotificationCategory;
}

/** What each category means to a user. Copy lives here, not in the API (it's presentation). */
export const CATEGORY_COPY: Record<NotificationCategory, { label: string; hint: string }> = {
  ACCOUNT_SECURITY: {
    label: 'Account & security',
    hint: 'Email verification, password changes, sign-ins. Always on.',
  },
  PURCHASE: { label: 'Purchases', hint: 'Receipts, refunds and enrollments. Always on.' },
  COURSE_ACTIVITY: {
    label: 'Course activity',
    hint: 'Your uploads finished processing, a course was published.',
  },
  ENGAGEMENT: { label: 'Reviews & questions', hint: 'Replies to your reviews and Q&A.' },
  PRODUCT_NEWS: {
    label: 'News & tips',
    hint: 'The welcome email, announcements and new features.',
  },
};

/**
 * Email consent (docs/api/conventions.md §14). In core/ because two features use it: the account
 * settings page and the public unsubscribe page.
 */
@Service()
export class NotificationApi {
  private readonly http = inject(HttpClient);

  preferences(): Observable<NotificationPreferenceResponse[]> {
    return this.http.get<NotificationPreferenceResponse[]>('/api/v1/me/notification-preferences');
  }

  /** PUT one toggle. 422 CATEGORY_MANDATORY for ACCOUNT_SECURITY / PURCHASE. */
  setPreference(
    category: NotificationCategory,
    enabled: boolean,
  ): Observable<NotificationPreferenceResponse> {
    return this.http.put<NotificationPreferenceResponse>(
      `/api/v1/me/notification-preferences/${category}`,
      { enabled },
    );
  }

  /** Public: the signed token from the email is the credential. 422 UNSUBSCRIBE_TOKEN_INVALID. */
  unsubscribe(token: string): Observable<UnsubscribeResponse> {
    return this.http.post<UnsubscribeResponse>('/api/v1/notifications/unsubscribe', { token });
  }
}
