import { inject, LOCALE_ID, Pipe, PipeTransform } from '@angular/core';

/**
 * `149900 | money: 'INR'` → "₹1,499.00"; `0 | money: 'INR'` → "Free".
 *
 * ⭐ The API sends money in MINOR units (API conventions §5); formatting is the client's job. How
 * many minor digits a currency has comes from Intl (INR 2, JPY 0), never a hard-coded `/ 100`.
 */
@Pipe({ name: 'money' })
export class MoneyPipe implements PipeTransform {
  private readonly locale = inject(LOCALE_ID);

  transform(amountMinor: number, currency: string): string {
    if (amountMinor === 0) {
      return 'Free';
    }
    const format = new Intl.NumberFormat(this.locale, { style: 'currency', currency });
    const digits = format.resolvedOptions().maximumFractionDigits ?? 2;
    return format.format(amountMinor / 10 ** digits);
  }
}
