import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter, map, startWith } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { navPages } from '../../core/access/page-access';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { AppIcon, AppIconName } from '../../shared/ui/icon';
import { LangSwitcher } from '../../shared/ui/lang-switcher';

@Component({
  selector: 'app-main-layout',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, AppIcon, TranslatePipe, LangSwitcher],
  templateUrl: './main-layout.html',
  styles: `
    :host {
      display: block;
    }

    @media (min-width: 1024px) {
      :host {
        height: 100dvh;
        overflow: hidden;
      }
    }
  `
})
export class MainLayout {
  private readonly router = inject(Router);
  protected readonly auth = inject(AuthService);

  private readonly currentUrl = toSignal(
    this.router.events.pipe(
      filter((event): event is NavigationEnd => event instanceof NavigationEnd),
      map(() => this.router.url),
      startWith(this.router.url)
    ),
    { initialValue: this.router.url }
  );

  protected readonly pages = computed(() => navPages(this.auth.currentUser()));

  protected readonly pageTitleKey = computed(() => {
    const segment = this.currentUrl().split('?')[0]?.split('/').filter(Boolean).at(-1) ?? '';
    switch (segment) {
      case 'dashboard':
        return 'page.DASHBOARD';
      case 'tenants':
        return 'page.TENANTS';
      case 'workspaces':
        return 'page.WORKSPACES';
      case 'access':
        return 'page.ACCESS';
      case 'instagram':
        return 'page.INSTAGRAM';
      case 'pending':
        return 'pending.title';
      default:
        return 'brand.console';
    }
  });

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
      case 'INSTAGRAM':
        return 'instagram';
      default:
        return 'dashboard';
    }
  }

  protected logout(): void {
    this.auth.logout().subscribe({
      error: (err: unknown) => {
        console.error('Logout failed', err);
      }
    });
  }
}
