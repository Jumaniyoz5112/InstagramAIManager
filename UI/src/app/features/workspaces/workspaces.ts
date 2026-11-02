import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../core/auth/auth.service';
import { WorkspaceService } from '../../core/services/workspace.service';
import { WorkspaceSummary } from '../../core/auth/auth.models';
import { I18nService } from '../../core/i18n/i18n.service';
import { translateApiError } from '../../core/i18n/api-error';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { AppIcon } from '../../shared/ui/icon';

@Component({
  selector: 'app-workspaces',
  imports: [ReactiveFormsModule, AppIcon, TranslatePipe],
  templateUrl: './workspaces.html'
})
export class WorkspacesPage {
  private readonly fb = inject(FormBuilder);
  private readonly workspacesApi = inject(WorkspaceService);
  private readonly i18n = inject(I18nService);
  protected readonly auth = inject(AuthService);

  protected readonly loading = signal(false);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly workspaces = signal<WorkspaceSummary[]>([]);

  protected readonly form = this.fb.nonNullable.group({
    tenantId: ['', Validators.required],
    name: ['', Validators.required]
  });

  constructor() {
    const firstTenant = this.auth.currentUser()?.tenants[0]?.id ?? '';
    this.form.patchValue({ tenantId: firstTenant });
    if (firstTenant) {
      this.loadWorkspaces(firstTenant);
    }
  }

  protected onTenantChange(tenantId: string): void {
    this.form.patchValue({ tenantId });
    this.loadWorkspaces(tenantId);
  }

  protected loadWorkspaces(tenantId: string): void {
    if (!tenantId) {
      this.workspaces.set([]);
      return;
    }

    this.loading.set(true);
    this.error.set(null);
    this.workspacesApi.listByTenant(tenantId).subscribe({
      next: (items) => {
        this.workspaces.set(items);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.error.set(translateApiError(this.i18n, err, 'workspaces.load_failed'));
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
    const payload = this.form.getRawValue();
    this.workspacesApi.create(payload).subscribe({
      next: () => {
        this.saving.set(false);
        this.form.patchValue({ name: '' });
        this.loadWorkspaces(payload.tenantId);
        this.auth.me().subscribe();
      },
      error: (err: unknown) => {
        this.saving.set(false);
        this.error.set(translateApiError(this.i18n, err, 'workspaces.create_failed'));
      }
    });
  }

  protected activate(workspaceId: string): void {
    this.auth.activateWorkspace(workspaceId).subscribe({
      next: () => this.loadWorkspaces(this.form.controls.tenantId.value)
    });
  }
}
