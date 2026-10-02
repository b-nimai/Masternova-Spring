import { Component, inject, input, OnInit, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { AuthApi } from '../../../core/auth/auth-api';
import { AuthStore } from '../../../core/auth/auth-store';

type VerifyState = 'verifying' | 'verified' | 'invalid' | 'missing';

/** Landing page of the email link: /verify-email?token=… */
@Component({
  selector: 'app-verify-email',
  imports: [RouterLink, MatCardModule, MatButtonModule, MatProgressSpinnerModule],
  templateUrl: './verify-email.html',
  styleUrl: './verify-email.scss',
})
export class VerifyEmail implements OnInit {
  private readonly api = inject(AuthApi);
  protected readonly auth = inject(AuthStore);

  /** ⭐ The `?token=` query param, bound straight to an input (withComponentInputBinding). */
  readonly token = input<string>();
  protected readonly state = signal<VerifyState>('verifying');

  ngOnInit(): void {
    // inputs are set before ngOnInit — the constructor would be too early to read them
    const token = this.token();
    if (!token) {
      this.state.set('missing');
      return;
    }
    this.api.verifyEmail(token).subscribe({
      next: () => this.state.set('verified'),
      // VERIFICATION_TOKEN_INVALID (422): used, expired or unknown — one answer for all three
      error: () => this.state.set('invalid'),
    });
  }
}
