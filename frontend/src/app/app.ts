import { Component } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';

/** App shell: toolbar + routed page. Auth menu and navigation arrive in Phase 3. */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, MatToolbarModule],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {}
