import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { ApiService } from '../../core/services/api.service';
import { hasPage } from '../../core/access/page-access';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { AppIcon } from '../../shared/ui/icon';

@Component({
  selector: 'app-dashboard',
  imports: [RouterLink, AppIcon, TranslatePipe],
  templateUrl: './dashboard.html'
})
export class DashboardPage {
  protected readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);

  protected readonly health = signal<string>('…');
  protected readonly tenantCount = computed(() => this.auth.currentUser()?.tenants.length ?? 0);
  protected readonly workspaceCount = computed(() => this.auth.currentUser()?.workspaces.length ?? 0);
  protected readonly canTenants = computed(() => hasPage(this.auth.currentUser(), 'TENANTS'));
  protected readonly canWorkspaces = computed(() => hasPage(this.auth.currentUser(), 'WORKSPACES'));

  constructor() {
    this.api.getHealth().subscribe({
      next: (data) => this.health.set(data['status'] ?? 'UP'),
      error: () => this.health.set('DOWN')
    });
  }

  protected activateWorkspace(workspaceId: string): void {
    this.auth.activateWorkspace(workspaceId).subscribe();
  }
}
