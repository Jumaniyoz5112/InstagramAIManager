import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';
import { firstAllowedPath } from '../../../core/access/page-access';
import { I18nService } from '../../../core/i18n/i18n.service';
import { translateApiError } from '../../../core/i18n/api-error';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';

@Component({
  selector: 'app-register',
  imports: [ReactiveFormsModule, RouterLink, TranslatePipe],
  templateUrl: './register.html'
})
export class RegisterPage {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly i18n = inject(I18nService);

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
      next: (user) => {
        this.loading.set(false);
        void this.router.navigateByUrl(firstAllowedPath(user));
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.error.set(translateApiError(this.i18n, err, 'auth.register.failed'));
      }
    });
  }
}
