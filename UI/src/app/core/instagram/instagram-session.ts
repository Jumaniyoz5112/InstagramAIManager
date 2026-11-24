import { Injectable, computed, inject, signal } from '@angular/core';
import { translateApiError } from '../i18n/api-error';
import { I18nService } from '../i18n/i18n.service';
import { InstagramAccount, InstagramService } from '../services/instagram.service';

const STORAGE_KEY = 'ig.selectedAccountId';

@Injectable({ providedIn: 'root' })
export class InstagramSession {
  private readonly api = inject(InstagramService);
  private readonly i18n = inject(I18nService);

  readonly accounts = signal<InstagramAccount[]>([]);
  readonly selectedAccountId = signal<string | null>(this.readStored());
  readonly loading = signal(false);
  readonly ready = signal(false);
  readonly error = signal<string | null>(null);

  readonly selectedAccount = computed(() => {
    const id = this.selectedAccountId();
    return this.accounts().find((account) => account.id === id) ?? null;
  });

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.listAccounts().subscribe({
      next: (items) => {
        const current = this.selectedAccountId();
        const nextId =
          current && items.some((account) => account.id === current)
            ? current
            : (items[0]?.id ?? null);
        this.accounts.set(items);
        this.persist(nextId);
        this.selectedAccountId.set(nextId);
        this.loading.set(false);
        this.ready.set(true);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.ready.set(true);
        this.error.set(translateApiError(this.i18n, err, 'instagram.load_failed'));
      }
    });
  }

  select(accountId: string | null): void {
    this.persist(accountId);
    this.selectedAccountId.set(accountId);
  }

  private persist(accountId: string | null): void {
    if (typeof sessionStorage === 'undefined') {
      return;
    }
    if (accountId) {
      sessionStorage.setItem(STORAGE_KEY, accountId);
    } else {
      sessionStorage.removeItem(STORAGE_KEY);
    }
  }

  private readStored(): string | null {
    if (typeof sessionStorage === 'undefined') {
      return null;
    }
    return sessionStorage.getItem(STORAGE_KEY);
  }
}
