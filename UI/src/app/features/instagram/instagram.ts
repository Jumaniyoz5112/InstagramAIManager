import { DatePipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { translateApiError } from '../../core/i18n/api-error';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import {
  InstagramAccount,
  InstagramMedia,
  InstagramService
} from '../../core/services/instagram.service';
import { AppIcon } from '../../shared/ui/icon';

@Component({
  selector: 'app-instagram',
  imports: [AppIcon, TranslatePipe, DatePipe],
  templateUrl: './instagram.html'
})
export class InstagramPage implements OnInit {
  private readonly api = inject(InstagramService);
  private readonly i18n = inject(I18nService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  protected readonly auth = inject(AuthService);

  protected readonly loadingAccounts = signal(false);
  protected readonly loadingMedia = signal(false);
  protected readonly connecting = signal(false);
  protected readonly syncingId = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);

  protected readonly accounts = signal<InstagramAccount[]>([]);
  protected readonly selectedAccountId = signal<string | null>(null);
  protected readonly media = signal<InstagramMedia[]>([]);
  protected readonly mediaPage = signal(0);
  protected readonly mediaTotalPages = signal(0);
  protected readonly selectedMedia = signal<InstagramMedia | null>(null);
  protected readonly loadingDetail = signal(false);

  ngOnInit(): void {
    const connected = this.route.snapshot.queryParamMap.get('connected');
    if (connected) {
      this.notice.set(this.i18n.t('instagram.connected'));
      void this.router.navigate([], {
        relativeTo: this.route,
        queryParams: {},
        replaceUrl: true
      });
    }
    this.reload();
  }

  protected reload(): void {
    this.loadAccounts();
  }

  protected loadAccounts(): void {
    this.loadingAccounts.set(true);
    this.error.set(null);
    this.api.listAccounts().subscribe({
      next: (items) => {
        this.accounts.set(items);
        this.loadingAccounts.set(false);
        const current = this.selectedAccountId();
        const nextId =
          current && items.some((a) => a.id === current)
            ? current
            : (items[0]?.id ?? null);
        this.selectedAccountId.set(nextId);
        this.loadMedia(0);
      },
      error: (err: unknown) => {
        this.loadingAccounts.set(false);
        this.error.set(translateApiError(this.i18n, err, 'instagram.load_failed'));
      }
    });
  }

  protected selectAccount(accountId: string): void {
    this.selectedAccountId.set(accountId);
    this.selectedMedia.set(null);
    this.loadMedia(0);
  }

  protected loadMedia(page: number): void {
    const accountId = this.selectedAccountId();
    this.loadingMedia.set(true);
    this.api.listMedia(accountId, page, 20).subscribe({
      next: (res) => {
        this.media.set(res.content);
        this.mediaPage.set(res.page);
        this.mediaTotalPages.set(res.totalPages);
        this.loadingMedia.set(false);
      },
      error: (err: unknown) => {
        this.loadingMedia.set(false);
        this.error.set(translateApiError(this.i18n, err, 'instagram.media_load_failed'));
      }
    });
  }

  protected connect(): void {
    if (!this.auth.currentUser()?.activeWorkspaceId) {
      this.error.set(this.i18n.t('error.instagram.workspace_required'));
      return;
    }
    this.connecting.set(true);
    this.error.set(null);
    this.api.startOauth().subscribe({
      next: (res) => {
        this.connecting.set(false);
        window.location.href = res.authorizationUrl;
      },
      error: (err: unknown) => {
        this.connecting.set(false);
        this.error.set(translateApiError(this.i18n, err, 'instagram.connect_failed'));
      }
    });
  }

  protected sync(accountId: string): void {
    this.syncingId.set(accountId);
    this.error.set(null);
    this.notice.set(null);
    this.api.syncAccount(accountId).subscribe({
      next: () => {
        this.syncingId.set(null);
        this.notice.set(this.i18n.t('instagram.sync_started'));
        this.loadAccounts();
      },
      error: (err: unknown) => {
        this.syncingId.set(null);
        this.error.set(translateApiError(this.i18n, err, 'instagram.sync_failed'));
      }
    });
  }

  protected disconnect(accountId: string): void {
    this.error.set(null);
    this.api.disconnectAccount(accountId).subscribe({
      next: () => {
        this.notice.set(this.i18n.t('instagram.disconnected'));
        if (this.selectedAccountId() === accountId) {
          this.selectedAccountId.set(null);
          this.selectedMedia.set(null);
        }
        this.loadAccounts();
      },
      error: (err: unknown) => {
        this.error.set(translateApiError(this.i18n, err, 'instagram.disconnect_failed'));
      }
    });
  }

  protected openMedia(item: InstagramMedia): void {
    this.loadingDetail.set(true);
    this.selectedMedia.set(item);
    this.api.getMedia(item.id).subscribe({
      next: (detail) => {
        this.selectedMedia.set(detail);
        this.loadingDetail.set(false);
      },
      error: (err: unknown) => {
        this.loadingDetail.set(false);
        this.error.set(translateApiError(this.i18n, err, 'instagram.media_load_failed'));
      }
    });
  }

  protected closeDetail(): void {
    this.selectedMedia.set(null);
  }

  protected previewUrl(item: InstagramMedia): string | null {
    return item.thumbnailUrl || item.mediaUrl;
  }

  protected statusKey(status: string): string {
    return `instagram.status.${status}`;
  }
}
