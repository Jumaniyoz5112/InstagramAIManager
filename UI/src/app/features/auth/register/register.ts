import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';

@Component({
  selector: 'app-register',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './register.html',
  styleUrl: './register.css'
})
export class RegisterPage {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    fullName: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(4)]],
    tenantName: ['', Validators.required],
    tenantSlug: ['', [Validators.required, Validators.pattern(/^[a-z0-9-]+$/)]]
  });

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.loading.set(true);
    this.error.set(null);

    this.auth.register(this.form.getRawValue()).subscribe({
      next: () => {
        this.loading.set(false);
        void this.router.navigateByUrl('/app/dashboard');
      },
      error: (err: unknown) => {
        this.loading.set(false);
        const message =
          typeof err === 'object' &&
          err !== null &&
          'error' in err &&
          typeof (err as { error?: { message?: string } }).error?.message === 'string'
            ? (err as { error: { message: string } }).error.message
            : 'Ro‘yxatdan o‘tish muvaffaqiyatsiz';
        this.error.set(message);
      }
    });
  }
}
