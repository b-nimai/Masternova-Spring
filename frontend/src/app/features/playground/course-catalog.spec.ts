import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { CourseCatalog } from './course-catalog';

describe('CourseCatalog (fake search API)', () => {
  let catalog: CourseCatalog;

  beforeEach(() => {
    catalog = TestBed.inject(CourseCatalog);
  });

  it('matches title or category, case-insensitively', async () => {
    const devops = await firstValueFrom(catalog.search('DEVOPS'));

    expect(devops.map((c) => c.id)).toEqual(['c4', 'c5']);
  });

  it('fails for the term "error"', async () => {
    await expect(firstValueFrom(catalog.search('error'))).rejects.toThrow(
      'Search is temporarily down',
    );
  });
});
