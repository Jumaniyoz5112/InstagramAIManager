import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './login.html',
  styleUrl: './login.css'
})
export class LoginPage {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    email: ['admin@local.dev', [Validators.required, Validators.email]],
    password: ['0110', Validators.required]
  });

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.loading.set(true);
    this.error.set(null);

    this.auth.login(this.form.getRawValue()).subscribe({
      next: (user) => {
        this.loading.set(false);
        const firstWorkspace = user.workspaces[0];
        if (firstWorkspace) {
          this.auth.activateWorkspace(firstWorkspace.id).subscribe({
            next: () => void this.router.navigateByUrl('/app/dashboard'),
            error: () => void this.router.navigateByUrl('/app/dashboard')
          });
        } else {
          void this.router.navigateByUrl('/app/dashboard');
        }
      },
      error: (err: unknown) => {
        this.loading.set(false);
        const message =
          typeof err === 'object' &&
          err !== null &&
          'error' in err &&
          typeof (err as { error?: { message?: string } }).error?.message === 'string'
            ? (err as { error: { message: string } }).error.message
            : 'Login muvaffaqiyatsiz';
        this.error.set(message);
      }
    });
  }
}
