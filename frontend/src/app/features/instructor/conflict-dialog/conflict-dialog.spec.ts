import { TestBed } from '@angular/core/testing';
import { MatDialogRef } from '@angular/material/dialog';
import { ConflictDialog } from './conflict-dialog';

describe('ConflictDialog', () => {
  it('explains that nothing was overwritten', async () => {
    await TestBed.configureTestingModule({
      imports: [ConflictDialog],
      providers: [{ provide: MatDialogRef, useValue: { close: vi.fn() } }],
    }).compileComponents();
    const fixture = TestBed.createComponent(ConflictDialog);
    await fixture.whenStable();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('nothing was overwritten');
  });
});
