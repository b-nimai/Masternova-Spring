import { Component } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule } from '@angular/material/dialog';

/** Shown when a save hits 409 VERSION_CONFLICT: another tab or person changed the course first. */
@Component({
  imports: [MatDialogModule, MatButtonModule],
  selector: 'app-conflict-dialog',
  templateUrl: './conflict-dialog.html',
})
export class ConflictDialog {}
