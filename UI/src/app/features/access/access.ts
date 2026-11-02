import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../core/auth/auth.service';
import { AccessAdminService, UserAccessRow } from '../../core/services/access.service';
import { PageAccess } from '../../core/auth/auth.models';
import { I18nService } from '../../core/i18n/i18n.service';
import { translateApiError } from '../../core/i18n/api-error';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { AppIcon } from '../../shared/ui/icon';

@Component({
  selector: 'app-access',
  imports: [AppIcon, TranslatePipe, FormsModule],
  templateUrl: './access.html'
})
export class AccessPage {
  private readonly accessApi = inject(AccessAdminService);
  private readonly auth = inject(AuthService);
  private readonly i18n = inject(I18nService);

  protected readonly loading = signal(true);
  protected readonly savingKey = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);
  protected readonly catalog = signal<PageAccess[]>([]);
  protected readonly users = signal<UserAccessRow[]>([]);
  protected readonly query = signal('');
  protected readonly selectedUserId = signal<string | null>(null);

  protected readonly filteredUsers = computed(() => {
    const q = this.query().trim().toLowerCase();
    const rows = this.users();
    if (!q) {
      return rows;
    }
    return rows.filter(
      (user) =>
        user.fullName.toLowerCase().includes(q) || user.email.toLowerCase().includes(q)
    );
  });

  protected readonly selectedUser = computed(() => {
    const id = this.selectedUserId();
    if (!id) {
      return null;
    }
    return this.users().find((user) => user.id === id) ?? null;
  });

  constructor() {
    this.reload();
  }

  protected pageLabelKey(code: string): string {
    return `page.${code}`;
  }

  protected initials(user: UserAccessRow): string {
    return user.fullName
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((part) => part[0]?.toUpperCase() ?? '')
      .join('');
  }

  protected selectUser(userId: string): void {
    if (this.selectedUserId() === userId) {
      return;
    }
    this.selectedUserId.set(userId);
    this.notice.set(null);
    this.error.set(null);
  }

  protected reload(): void {
    this.loading.set(true);
    this.error.set(null);
    this.accessApi.listPages().subscribe({
      next: (pages) => {
        this.catalog.set(pages.filter((page) => !page.adminOnly));
        this.accessApi.listUsers().subscribe({
          next: (users) => {
            this.users.set(users);
            this.loading.set(false);
            const selected = this.selectedUserId();
            if (!selected || !users.some((user) => user.id === selected)) {
              this.selectedUserId.set(users[0]?.id ?? null);
            }
          },
          error: (err: unknown) => {
            this.loading.set(false);
            this.error.set(translateApiError(this.i18n, err, 'access.load_users_failed'));
          }
        });
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.error.set(translateApiError(this.i18n, err, 'access.load_pages_failed'));
      }
    });
  }

  protected isChecked(user: UserAccessRow, code: string): boolean {
    return user.pageCodes.includes(code);
  }

  protected isSaving(userId: string, code: string): boolean {
    return this.savingKey() === `${userId}:${code}`;
  }

  protected toggle(user: UserAccessRow, code: string): void {
    if (user.platformAdmin || this.savingKey()) {
      return;
    }

    const enabled = !this.isChecked(user, code);
    const previousCodes = [...user.pageCodes];
    const nextCodes = enabled
      ? [...new Set([...previousCodes, code])]
      : previousCodes.filter((item) => item !== code);

    this.users.update((rows) =>
      rows.map((row) => (row.id === user.id ? { ...row, pageCodes: nextCodes } : row))
    );
    this.savingKey.set(`${user.id}:${code}`);
    this.notice.set(null);
    this.error.set(null);

    this.accessApi.updateUserPages(user.id, nextCodes).subscribe({
      next: (updated) => {
        this.users.update((rows) => rows.map((row) => (row.id === updated.id ? updated : row)));
        this.savingKey.set(null);
        this.notice.set(this.i18n.t('access.saved', [updated.fullName]));
        if (updated.id === this.auth.currentUser()?.id) {
          this.auth.me().subscribe();
        }
      },
      error: (err: unknown) => {
        this.users.update((rows) =>
          rows.map((row) => (row.id === user.id ? { ...row, pageCodes: previousCodes } : row))
        );
        this.savingKey.set(null);
        this.error.set(translateApiError(this.i18n, err, 'access.save_failed'));
      }
    });
  }
}
