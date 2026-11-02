import { Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { navPages } from '../../core/access/page-access';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { AppIcon, AppIconName } from '../../shared/ui/icon';
import { LangSwitcher } from '../../shared/ui/lang-switcher';

@Component({
  selector: 'app-main-layout',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, AppIcon, TranslatePipe, LangSwitcher],
  templateUrl: './main-layout.html'
})
export class MainLayout {
  protected readonly auth = inject(AuthService);

  protected readonly pages = computed(() => navPages(this.auth.currentUser()));

  protected readonly activeWorkspaceName = computed(() => {
    const user = this.auth.currentUser();
    if (!user?.activeWorkspaceId) {
      return null;
    }
    return user.workspaces.find((w) => w.id === user.activeWorkspaceId)?.name ?? null;
  });

  protected readonly activeTenantName = computed(() => {
    const user = this.auth.currentUser();
    if (!user?.activeTenantId) {
      return null;
    }
    return user.tenants.find((t) => t.id === user.activeTenantId)?.name ?? null;
  });

  protected pageLabelKey(code: string): string {
    return `page.${code}`;
  }

  protected iconFor(code: string): AppIconName {
    switch (code) {
      case 'TENANTS':
        return 'tenants';
      case 'WORKSPACES':
        return 'workspaces';
      case 'ACCESS':
        return 'shield';
      default:
        return 'dashboard';
    }
  }

  protected logout(): void {
    this.auth.logout().subscribe();
  }
}
