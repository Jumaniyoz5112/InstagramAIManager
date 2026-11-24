import { DatePipe } from '@angular/common';
import { Component, effect, inject, OnDestroy, OnInit, signal, untracked } from '@angular/core';
import { AuthService } from '../../core/auth/auth.service';
import { translateApiError } from '../../core/i18n/api-error';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { InstagramSession } from '../../core/instagram/instagram-session';
import {
  ContentAnalysis,
  InstagramMedia,
  InstagramMediaComment,
  InstagramMediaType,
  InstagramService,
  MediaAnalysisStatus,
  PublishType
} from '../../core/services/instagram.service';
import { AppIcon } from '../../shared/ui/icon';
import { InstagramAccountBar } from '../instagram/instagram-account-bar';

type MediaFilter = 'ALL' | 'IMAGE' | 'VIDEO' | 'CAROUSEL_ALBUM';

@Component({
  selector: 'app-instagram-media',
  imports: [AppIcon, TranslatePipe, DatePipe, InstagramAccountBar],
  templateUrl: './instagram-media.html',
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

    @keyframes ig-analyze-spin {
      to {
        transform: rotate(360deg);
      }
    }

    @keyframes ig-analyze-pulse {
      0%,
      100% {
        opacity: 0.45;
      }
      50% {
        opacity: 1;
      }
    }

    .ig-analyze-spin {
      animation: ig-analyze-spin 0.9s linear infinite;
    }

    .ig-analyze-pulse {
      animation: ig-analyze-pulse 1.4s ease-in-out infinite;
    }
  `
})
export class InstagramMediaPage implements OnInit, OnDestroy {
  private readonly api = inject(InstagramService);
  private readonly i18n = inject(I18nService);
  protected readonly session = inject(InstagramSession);
  protected readonly auth = inject(AuthService);

  protected readonly loadingMedia = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);
  protected readonly media = signal<InstagramMedia[]>([]);
  protected readonly mediaPage = signal(0);
  protected readonly mediaTotalPages = signal(0);
  protected readonly mediaFilter = signal<MediaFilter>('ALL');
  protected readonly selectedMedia = signal<InstagramMedia | null>(null);
  protected readonly loadingDetail = signal(false);
  protected readonly comments = signal<InstagramMediaComment[]>([]);
  protected readonly loadingComments = signal(false);
  protected readonly carouselIndex = signal(0);
  protected readonly selectedForAnalysis = signal<Set<string>>(new Set());
  protected readonly analysisActionBusy = signal(false);
  protected readonly mediaAnalysis = signal<ContentAnalysis | null>(null);
  protected readonly loadingAnalysis = signal(false);
  protected readonly analysisDialogMedia = signal<InstagramMedia | null>(null);
  protected readonly analysisDialogData = signal<ContentAnalysis | null>(null);
  protected readonly loadingAnalysisDialog = signal(false);
  protected readonly savingAnalysisFeedback = signal(false);
  protected readonly feedbackSummary = signal('');
  protected readonly feedbackNote = signal('');
  protected readonly showPublish = signal(false);
  protected readonly publishing = signal(false);
  protected readonly publishType = signal<PublishType>('FEED_IMAGE');
  protected readonly publishCaption = signal('');
  protected readonly publishFiles = signal<File[]>([]);
  protected readonly publishTypes: PublishType[] = [
    'FEED_IMAGE',
    'REELS',
    'STORY_IMAGE',
    'STORY_VIDEO',
    'CAROUSEL'
  ];

  private analysisPollTimer: ReturnType<typeof setInterval> | null = null;
  private watchedAccountId: string | null | undefined = undefined;

  protected readonly mediaFilters: MediaFilter[] = ['ALL', 'IMAGE', 'VIDEO', 'CAROUSEL_ALBUM'];

  protected readonly topLevelComments = () =>
    this.comments().filter((comment) => !comment.parentIgCommentId);

  protected readonly selectedCount = () => this.selectedForAnalysis().size;

  protected readonly analyzingCount = () =>
    this.media().filter(
      (item) => item.analysisStatus === 'QUEUED' || item.analysisStatus === 'RUNNING'
    ).length;

  constructor() {
    effect(() => {
      const ready = this.session.ready();
      const accountId = this.session.selectedAccountId();
      if (!ready || accountId === this.watchedAccountId) {
        return;
      }
      this.watchedAccountId = accountId;
      untracked(() => this.onAccount(accountId));
    });
  }

  ngOnInit(): void {
    this.session.load();
  }

  ngOnDestroy(): void {
    this.stopAnalysisPoll();
  }

  private onAccount(accountId: string | null): void {
    this.selectedMedia.set(null);
    this.comments.set([]);
    this.mediaAnalysis.set(null);
    this.carouselIndex.set(0);
    this.clearAnalysisSelection();
    if (!accountId) {
      this.media.set([]);
      this.mediaPage.set(0);
      this.mediaTotalPages.set(0);
      this.stopAnalysisPoll();
      return;
    }
    this.loadMedia(0);
  }

  protected setMediaFilter(filter: MediaFilter): void {
    if (this.mediaFilter() === filter) {
      return;
    }
    this.mediaFilter.set(filter);
    this.clearAnalysisSelection();
    this.loadMedia(0);
  }

  protected loadMedia(page: number, opts?: { silent?: boolean }): void {
    const accountId = this.session.selectedAccountId();
    const filter = this.mediaFilter();
    const mediaType: InstagramMediaType | null = filter === 'ALL' ? null : filter;
    if (!accountId) {
      this.media.set([]);
      this.loadingMedia.set(false);
      return;
    }
    if (!opts?.silent) {
      this.loadingMedia.set(true);
    }
    this.api.listMedia(accountId, page, 20, mediaType).subscribe({
      next: (res) => {
        this.media.set(res.content);
        this.mediaPage.set(res.page);
        this.mediaTotalPages.set(res.totalPages);
        this.loadingMedia.set(false);
        this.syncAnalysisPoll(res.content);
        this.refreshOpenDetailAnalysis(res.content);
      },
      error: (err: unknown) => {
        this.loadingMedia.set(false);
        this.error.set(translateApiError(this.i18n, err, 'instagram.media_load_failed'));
      }
    });
  }

  protected openMedia(item: InstagramMedia): void {
    this.loadingDetail.set(true);
    this.loadingComments.set(true);
    this.loadingAnalysis.set(true);
    this.selectedMedia.set(item);
    this.comments.set([]);
    this.mediaAnalysis.set(null);
    this.carouselIndex.set(0);
    this.api.getMedia(item.id).subscribe({
      next: (detail) => {
        this.selectedMedia.set(detail);
        this.loadingDetail.set(false);
        this.loadAnalysisIfAny(detail);
      },
      error: (err: unknown) => {
        this.loadingDetail.set(false);
        this.loadingAnalysis.set(false);
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
    this.mediaAnalysis.set(null);
    this.carouselIndex.set(0);
  }

  protected openAnalysisDialog(item: InstagramMedia, event?: Event): void {
    event?.preventDefault();
    event?.stopPropagation();
    if (item.analysisStatus !== 'ANALYZED' && item.analysisStatus !== 'FAILED') {
      return;
    }
    this.analysisDialogMedia.set(item);
    this.analysisDialogData.set(null);
    this.feedbackSummary.set('');
    this.feedbackNote.set('');
    this.loadingAnalysisDialog.set(true);
    this.api.getAnalysis(item.id).subscribe({
      next: (analysis) => {
        this.analysisDialogData.set(analysis);
        this.feedbackSummary.set(String(analysis.result?.['correctedSummary'] ?? ''));
        this.feedbackNote.set(String(analysis.result?.['userNote'] ?? ''));
        this.loadingAnalysisDialog.set(false);
      },
      error: (err: unknown) => {
        this.loadingAnalysisDialog.set(false);
        this.closeAnalysisDialog();
        this.error.set(translateApiError(this.i18n, err, 'instagram.analysis_failed'));
      }
    });
  }

  protected closeAnalysisDialog(): void {
    this.analysisDialogMedia.set(null);
    this.analysisDialogData.set(null);
    this.loadingAnalysisDialog.set(false);
    this.savingAnalysisFeedback.set(false);
    this.feedbackSummary.set('');
    this.feedbackNote.set('');
  }

  protected feedbackSummaryChange(event: Event): void {
    this.feedbackSummary.set((event.target as HTMLTextAreaElement).value);
  }

  protected feedbackNoteChange(event: Event): void {
    this.feedbackNote.set((event.target as HTMLTextAreaElement).value);
  }

  protected saveAnalysisFeedback(): void {
    const media = this.analysisDialogMedia();
    if (!media || media.analysisStatus !== 'ANALYZED') {
      return;
    }
    this.savingAnalysisFeedback.set(true);
    this.error.set(null);
    this.api
      .saveAnalysisFeedback(media.id, {
        correctedSummary: this.feedbackSummary().trim() || null,
        userNote: this.feedbackNote().trim() || null
      })
      .subscribe({
        next: (analysis) => {
          this.savingAnalysisFeedback.set(false);
          this.analysisDialogData.set(analysis);
          const summary = this.analysisResultText(analysis) || media.analysisSummary || null;
          this.media.update((list) =>
            list.map((item) => (item.id === media.id ? { ...item, analysisSummary: summary } : item))
          );
          this.analysisDialogMedia.set({ ...media, analysisSummary: summary });
          const open = this.selectedMedia();
          if (open?.id === media.id) {
            this.selectedMedia.set({ ...open, analysisSummary: summary });
            this.mediaAnalysis.set(analysis);
          }
          this.notice.set(this.i18n.t('instagram.analysis_feedback_saved'));
        },
        error: (err: unknown) => {
          this.savingAnalysisFeedback.set(false);
          this.error.set(translateApiError(this.i18n, err, 'instagram.analysis_feedback_failed'));
        }
      });
  }

  protected isSelectedForAnalysis(id: string): boolean {
    return this.selectedForAnalysis().has(id);
  }

  protected toggleAnalysisSelect(item: InstagramMedia, event: Event): void {
    event.preventDefault();
    event.stopPropagation();
    const next = new Set(this.selectedForAnalysis());
    if (next.has(item.id)) {
      next.delete(item.id);
    } else {
      next.add(item.id);
    }
    this.selectedForAnalysis.set(next);
  }

  protected clearAnalysisSelection(): void {
    this.selectedForAnalysis.set(new Set());
  }

  protected selectAllVisibleForAnalysis(): void {
    const next = new Set(this.selectedForAnalysis());
    for (const item of this.media()) {
      if (item.analysisStatus === 'NOT_ANALYZED' || item.analysisStatus === 'FAILED') {
        next.add(item.id);
      }
    }
    this.selectedForAnalysis.set(next);
  }

  protected analyzeSelected(): void {
    const ids = [...this.selectedForAnalysis()];
    if (!ids.length) {
      this.error.set(this.i18n.t('instagram.analysis_need_selection'));
      return;
    }
    this.runAnalyze(ids);
  }

  protected skipSelected(): void {
    const ids = [...this.selectedForAnalysis()];
    if (!ids.length) {
      this.error.set(this.i18n.t('instagram.analysis_need_selection'));
      return;
    }
    this.runSkip(ids);
  }

  protected analyzeOne(item: InstagramMedia, event?: Event): void {
    event?.preventDefault();
    event?.stopPropagation();
    this.runAnalyze([item.id]);
  }

  protected skipOne(item: InstagramMedia, event?: Event): void {
    event?.preventDefault();
    event?.stopPropagation();
    this.runSkip([item.id]);
  }

  private runAnalyze(ids: string[]): void {
    this.analysisActionBusy.set(true);
    this.error.set(null);
    this.notice.set(null);
    this.api.analyzeMedia(ids).subscribe({
      next: (res) => {
        this.analysisActionBusy.set(false);
        this.clearAnalysisSelection();
        this.notice.set(this.i18n.t('instagram.analysis_queued', [res.queued]));
        this.loadMedia(this.mediaPage(), { silent: true });
      },
      error: (err: unknown) => {
        this.analysisActionBusy.set(false);
        this.error.set(translateApiError(this.i18n, err, 'instagram.analysis_failed'));
      }
    });
  }

  private runSkip(ids: string[]): void {
    this.analysisActionBusy.set(true);
    this.error.set(null);
    this.notice.set(null);
    this.api.skipAnalysis(ids).subscribe({
      next: (res) => {
        this.analysisActionBusy.set(false);
        this.clearAnalysisSelection();
        this.notice.set(this.i18n.t('instagram.analysis_skipped', [res.skipped]));
        this.loadMedia(this.mediaPage(), { silent: true });
      },
      error: (err: unknown) => {
        this.analysisActionBusy.set(false);
        this.error.set(translateApiError(this.i18n, err, 'instagram.analysis_skip_failed'));
      }
    });
  }

  private loadAnalysisIfAny(detail: InstagramMedia): void {
    if (
      detail.analysisStatus !== 'ANALYZED' &&
      detail.analysisStatus !== 'FAILED' &&
      detail.analysisStatus !== 'QUEUED' &&
      detail.analysisStatus !== 'RUNNING'
    ) {
      this.mediaAnalysis.set(null);
      this.loadingAnalysis.set(false);
      return;
    }
    this.loadingAnalysis.set(true);
    this.api.getAnalysis(detail.id).subscribe({
      next: (analysis) => {
        this.mediaAnalysis.set(analysis);
        this.loadingAnalysis.set(false);
      },
      error: () => {
        this.mediaAnalysis.set(null);
        this.loadingAnalysis.set(false);
      }
    });
  }

  private syncAnalysisPoll(items: InstagramMedia[]): void {
    const busy = items.some(
      (item) => item.analysisStatus === 'QUEUED' || item.analysisStatus === 'RUNNING'
    );
    if (busy) {
      this.startAnalysisPoll();
    } else {
      this.stopAnalysisPoll();
    }
  }

  private startAnalysisPoll(): void {
    if (this.analysisPollTimer) {
      return;
    }
    this.analysisPollTimer = setInterval(() => {
      this.loadMedia(this.mediaPage(), { silent: true });
    }, 3000);
  }

  private stopAnalysisPoll(): void {
    if (this.analysisPollTimer) {
      clearInterval(this.analysisPollTimer);
      this.analysisPollTimer = null;
    }
  }

  private refreshOpenDetailAnalysis(items: InstagramMedia[]): void {
    const open = this.selectedMedia();
    if (!open) {
      return;
    }
    const updated = items.find((item) => item.id === open.id);
    if (!updated) {
      return;
    }
    if (
      updated.analysisStatus !== open.analysisStatus ||
      updated.analysisSummary !== open.analysisSummary
    ) {
      this.selectedMedia.set({ ...open, ...updated });
      this.loadAnalysisIfAny(updated);
    }
  }

  protected analysisStatusKey(status: MediaAnalysisStatus | string): string {
    return `instagram.analysis.status.${status}`;
  }

  protected analysisStatusTone(status: MediaAnalysisStatus | string): string {
    switch (status) {
      case 'ANALYZED':
        return 'bg-accent-2/20 text-accent-2';
      case 'QUEUED':
      case 'RUNNING':
        return 'bg-warn/20 text-warn';
      case 'FAILED':
        return 'bg-accent/20 text-accent';
      case 'SKIPPED':
        return 'bg-white/10 text-white/45';
      default:
        return 'bg-white/10 text-white/55';
    }
  }

  protected isAnalyzing(status: MediaAnalysisStatus | string): boolean {
    return status === 'QUEUED' || status === 'RUNNING';
  }

  protected analysisResultText(analysis: ContentAnalysis | null): string | null {
    const result = analysis?.result;
    if (!result) {
      return null;
    }
    const summary = result['correctedSummary'] ?? result['summaryUz'] ?? result['mainTopic'];
    return summary != null && String(summary).trim() ? String(summary) : null;
  }

  protected analysisAiSummary(analysis: ContentAnalysis | null): string | null {
    const result = analysis?.result;
    if (!result) {
      return null;
    }
    const summary = result['summaryUz'] ?? result['mainTopic'];
    return summary != null && String(summary).trim() ? String(summary) : null;
  }

  protected analysisList(analysis: ContentAnalysis | null, key: string): string[] {
    const value = analysis?.result?.[key];
    if (!Array.isArray(value)) {
      return [];
    }
    return value.map(String).map((item) => item.trim()).filter(Boolean);
  }

  protected analysisStyleText(analysis: ContentAnalysis | null): string | null {
    const style = analysis?.result?.['visualStyle'];
    if (!style || typeof style !== 'object') {
      return this.analysisField(analysis, 'visualStyle');
    }
    const obj = style as Record<string, unknown>;
    const parts = [
      obj['mood'],
      obj['composition'],
      Array.isArray(obj['colors']) ? obj['colors'].join(', ') : obj['colors']
    ]
      .map((value) => (value != null ? String(value).trim() : ''))
      .filter(Boolean);
    return parts.length ? parts.join(' · ') : null;
  }

  protected analysisField(analysis: ContentAnalysis | null, key: string): string | null {
    const value = analysis?.result?.[key];
    if (value == null || value === '') {
      return null;
    }
    if (Array.isArray(value)) {
      return value.map(String).join(', ');
    }
    return String(value);
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

  protected openPublish(): void {
    this.showPublish.set(true);
  }

  protected closePublish(): void {
    if (this.publishing()) {
      return;
    }
    this.showPublish.set(false);
  }

  protected setPublishType(type: PublishType): void {
    this.publishType.set(type);
    this.publishFiles.set([]);
  }

  protected onPublishFilesSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.publishFiles.set(input.files ? Array.from(input.files) : []);
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

  protected publishTypeKey(type: PublishType): string {
    return `instagram.publish.type.${type}`;
  }

  protected submitPublish(): void {
    const accountId = this.session.selectedAccountId();
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
            this.loadMedia(0);
            this.api.syncAccount(accountId).subscribe({ error: () => undefined });
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

  protected repliesFor(parent: InstagramMediaComment): InstagramMediaComment[] {
    return this.comments().filter((comment) => comment.parentIgCommentId === parent.igCommentId);
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
}
