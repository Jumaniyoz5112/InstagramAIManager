import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';
import { firstAllowedPath } from '../../../core/access/page-access';
import { I18nService } from '../../../core/i18n/i18n.service';
import { translateApiError } from '../../../core/i18n/api-error';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';
import { environment } from '../../../../environments/environment';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink, TranslatePipe],
  templateUrl: './login.html'
})
export class LoginPage {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly i18n = inject(I18nService);

  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    email: [environment.production ? '' : 'admin@local.dev', [Validators.required, Validators.email]],
    password: [environment.production ? '' : '0110', Validators.required]
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
        const target = firstAllowedPath(user);
        const firstWorkspace = user.workspaces[0];
        if (firstWorkspace && !user.activeWorkspaceId) {
          this.auth.activateWorkspace(firstWorkspace.id).subscribe({
            next: () => void this.router.navigateByUrl(target),
            error: () => void this.router.navigateByUrl(target)
          });
        } else {
          void this.router.navigateByUrl(target);
        }
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.error.set(translateApiError(this.i18n, err, 'auth.login.failed'));
      }
    });
  }
}
