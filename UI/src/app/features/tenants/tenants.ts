import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../core/auth/auth.service';
import { TenantService } from '../../core/services/tenant.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { translateApiError } from '../../core/i18n/api-error';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { AppIcon } from '../../shared/ui/icon';

@Component({
  selector: 'app-tenants',
  imports: [ReactiveFormsModule, AppIcon, TranslatePipe],
  templateUrl: './tenants.html'
})
export class TenantsPage {
  private readonly fb = inject(FormBuilder);
  private readonly tenantsApi = inject(TenantService);
  private readonly auth = inject(AuthService);
  private readonly i18n = inject(I18nService);

  protected readonly loading = signal(false);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly tenants = signal(this.auth.currentUser()?.tenants ?? []);

  protected readonly form = this.fb.nonNullable.group({
    name: ['', Validators.required],
    slug: ['', [Validators.required, Validators.pattern(/^[a-z0-9-]+$/)]]
  });

  constructor() {
    this.reload();
  }

  protected reload(): void {
    this.loading.set(true);
    this.error.set(null);
    this.tenantsApi.list().subscribe({
      next: (items) => {
        this.tenants.set(items);
        this.loading.set(false);
        this.auth.me().subscribe();
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.error.set(translateApiError(this.i18n, err, 'tenants.load_failed'));
      }
    });
  }

  protected create(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.saving.set(true);
    this.error.set(null);
    this.tenantsApi.create(this.form.getRawValue()).subscribe({
      next: () => {
        this.saving.set(false);
        this.form.reset({ name: '', slug: '' });
        this.reload();
      },
      error: (err: unknown) => {
        this.saving.set(false);
        this.error.set(translateApiError(this.i18n, err, 'tenants.create_failed'));
      }
    });
  }
}
