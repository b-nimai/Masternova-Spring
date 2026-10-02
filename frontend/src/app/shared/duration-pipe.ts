import { Pipe, PipeTransform } from '@angular/core';

/**
 * Seconds → text. `long` (course totals): "1h 25m", "7m". `clock` (one lecture): "7:00", "1:02:03".
 */
@Pipe({ name: 'duration' })
export class DurationPipe implements PipeTransform {
  transform(seconds: number, style: 'long' | 'clock' = 'long'): string {
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    const s = seconds % 60;
    if (style === 'clock') {
      const mm = h > 0 ? String(m).padStart(2, '0') : String(m);
      return `${h > 0 ? h + ':' : ''}${mm}:${String(s).padStart(2, '0')}`;
    }
    if (h > 0) {
      return `${h}h ${String(m).padStart(2, '0')}m`;
    }
    return m > 0 ? `${m}m` : `${s}s`;
  }
}
