import { DatePipe } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { translateApiError } from '../../core/i18n/api-error';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import {
  InstagramAccount,
  InstagramMedia,
  InstagramMediaComment,
  InstagramMediaType,
  InstagramService,
  PublishType
} from '../../core/services/instagram.service';
import { AppIcon } from '../../shared/ui/icon';

type MediaFilter = 'ALL' | 'IMAGE' | 'VIDEO' | 'CAROUSEL_ALBUM';

@Component({
  selector: 'app-instagram',
  imports: [AppIcon, TranslatePipe, DatePipe],
  templateUrl: './instagram.html',
  styles: `
    :host {
      display: block;
    }

    @media (min-width: 1024px) {
      :host {
        display: flex;
        flex: 1 1 auto;
        flex-direction: column;
        min-height: 0;
        overflow: hidden;
      }
    }
  `
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
  protected readonly mediaFilter = signal<MediaFilter>('ALL');
  protected readonly selectedMedia = signal<InstagramMedia | null>(null);
  protected readonly loadingDetail = signal(false);
  protected readonly comments = signal<InstagramMediaComment[]>([]);
  protected readonly loadingComments = signal(false);
  protected readonly carouselIndex = signal(0);

  protected readonly publishing = signal(false);
  protected readonly publishType = signal<PublishType>('FEED_IMAGE');
  protected readonly publishCaption = signal('');
  protected readonly publishFiles = signal<File[]>([]);
  protected readonly showPublish = signal(false);

  protected readonly mediaFilters: MediaFilter[] = ['ALL', 'IMAGE', 'VIDEO', 'CAROUSEL_ALBUM'];
  protected readonly publishTypes: PublishType[] = [
    'FEED_IMAGE',
    'REELS',
    'STORY_IMAGE',
    'STORY_VIDEO',
    'CAROUSEL'
  ];

  protected readonly topLevelComments = computed(() =>
    this.comments().filter((c) => !c.parentIgCommentId)
  );

  protected readonly selectedAccount = computed(() => {
    const id = this.selectedAccountId();
    return this.accounts().find((a) => a.id === id) ?? null;
  });

  ngOnInit(): void {
    const connected = this.route.snapshot.queryParamMap.get('connected');
    const igError = this.route.snapshot.queryParamMap.get('ig_error');
    if (connected) {
      this.notice.set(this.i18n.t('instagram.connected'));
      void this.router.navigate([], {
        relativeTo: this.route,
        queryParams: {},
        replaceUrl: true
      });
    } else if (igError) {
      const key = decodeURIComponent(igError);
      const translated = this.i18n.t(key);
      this.error.set(translated !== key ? translated : this.i18n.t('instagram.connect_failed'));
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

  protected setMediaFilter(filter: MediaFilter): void {
    if (this.mediaFilter() === filter) {
      return;
    }
    this.mediaFilter.set(filter);
    this.loadMedia(0);
  }

  protected loadMedia(page: number): void {
    const accountId = this.selectedAccountId();
    const filter = this.mediaFilter();
    const mediaType: InstagramMediaType | null = filter === 'ALL' ? null : filter;
    this.loadingMedia.set(true);
    this.api.listMedia(accountId, page, 20, mediaType).subscribe({
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
    this.loadingComments.set(true);
    this.selectedMedia.set(item);
    this.comments.set([]);
    this.carouselIndex.set(0);
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
    this.api.listComments(item.id).subscribe({
      next: (items) => {
        this.comments.set(items);
        this.loadingComments.set(false);
      },
      error: (err: unknown) => {
        this.loadingComments.set(false);
        this.error.set(translateApiError(this.i18n, err, 'instagram.comments_load_failed'));
      }
    });
  }

  protected closeDetail(): void {
    this.selectedMedia.set(null);
    this.comments.set([]);
    this.carouselIndex.set(0);
  }

  protected previewUrl(item: InstagramMedia): string | null {
    return item.thumbnailUrl || item.mediaUrl;
  }

  protected isVideo(type: InstagramMediaType | string | null | undefined): boolean {
    return type === 'VIDEO' || type === 'REELS';
  }

  protected metricValue(value: number | null | undefined): string {
    return value == null ? '—' : String(value);
  }

  protected filterLabelKey(filter: MediaFilter): string {
    return `instagram.filter.${filter}`;
  }

  protected repliesFor(parent: InstagramMediaComment): InstagramMediaComment[] {
    return this.comments().filter((c) => c.parentIgCommentId === parent.igCommentId);
  }

  protected carouselChild() {
    const detail = this.selectedMedia();
    const children = detail?.children ?? [];
    if (!children.length) {
      return null;
    }
    const idx = Math.min(Math.max(this.carouselIndex(), 0), children.length - 1);
    return children[idx];
  }

  protected prevCarousel(): void {
    const children = this.selectedMedia()?.children ?? [];
    if (!children.length) {
      return;
    }
    const next = this.carouselIndex() - 1;
    this.carouselIndex.set(next < 0 ? children.length - 1 : next);
  }

  protected nextCarousel(): void {
    const children = this.selectedMedia()?.children ?? [];
    if (!children.length) {
      return;
    }
    this.carouselIndex.set((this.carouselIndex() + 1) % children.length);
  }

  protected statusKey(status: string): string {
    return `instagram.status.${status}`;
  }

  protected statusTone(status: string): string {
    switch (status) {
      case 'ACTIVE':
        return 'bg-accent-2/15 text-accent-2';
      case 'NEEDS_REAUTH':
        return 'bg-warn/15 text-warn';
      case 'DISCONNECTED':
        return 'bg-white/10 text-white/50';
      default:
        return 'bg-accent/15 text-accent';
    }
  }

  protected accountInitials(account: InstagramAccount): string {
    const raw = (account.username || account.igUserId || '?').trim();
    return raw.slice(0, 2).toUpperCase();
  }

  protected togglePublish(): void {
    this.showPublish.update((v) => !v);
  }

  protected setPublishType(type: PublishType): void {
    this.publishType.set(type);
    this.publishFiles.set([]);
  }

  protected onPublishFilesSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const list = input.files ? Array.from(input.files) : [];
    this.publishFiles.set(list);
  }

  protected publishCaptionChange(event: Event): void {
    this.publishCaption.set((event.target as HTMLTextAreaElement).value);
  }

  protected publishAccept(): string {
    switch (this.publishType()) {
      case 'FEED_IMAGE':
      case 'STORY_IMAGE':
        return 'image/jpeg,.jpg,.jpeg';
      case 'REELS':
      case 'STORY_VIDEO':
        return 'video/mp4,video/quicktime,.mp4,.mov';
      case 'CAROUSEL':
        return 'image/jpeg,.jpg,.jpeg,video/mp4,video/quicktime,.mp4,.mov';
    }
  }

  protected publishMultiple(): boolean {
    return this.publishType() === 'CAROUSEL';
  }

  protected submitPublish(): void {
    const accountId = this.selectedAccountId();
    if (!accountId) {
      this.error.set(this.i18n.t('instagram.need_account'));
      return;
    }
    const files = this.publishFiles();
    if (!files.length) {
      this.error.set(this.i18n.t('instagram.publish_need_files'));
      return;
    }
    this.publishing.set(true);
    this.error.set(null);
    this.notice.set(null);
    this.api
      .publish({
        accountId,
        publishType: this.publishType(),
        caption: this.publishCaption(),
        files,
        idempotencyKey: crypto.randomUUID()
      })
      .subscribe({
        next: (job) => {
          this.publishing.set(false);
          if (job.status === 'PUBLISHED') {
            this.notice.set(this.i18n.t('instagram.publish_ok'));
            this.publishCaption.set('');
            this.publishFiles.set([]);
            this.showPublish.set(false);
            this.sync(accountId);
          } else {
            this.error.set(job.errorMessage || this.i18n.t('instagram.publish_failed'));
          }
        },
        error: (err: unknown) => {
          this.publishing.set(false);
          this.error.set(translateApiError(this.i18n, err, 'instagram.publish_failed'));
        }
      });
  }

  protected publishTypeKey(type: PublishType): string {
    return `instagram.publish.type.${type}`;
  }
}
