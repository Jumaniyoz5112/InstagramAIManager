import { Component, inject, OnInit, signal } from '@angular/core';
import { AuthService } from '../../core/auth/auth.service';
import { translateApiError } from '../../core/i18n/api-error';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { InstagramSession } from '../../core/instagram/instagram-session';
import { InstagramService, PublishType } from '../../core/services/instagram.service';
import { InstagramAccountBar } from '../instagram/instagram-account-bar';

@Component({
  selector: 'app-instagram-publish',
  imports: [TranslatePipe, InstagramAccountBar],
  templateUrl: './instagram-publish.html'
})
export class InstagramPublishPage implements OnInit {
  private readonly api = inject(InstagramService);
  private readonly i18n = inject(I18nService);
  protected readonly session = inject(InstagramSession);
  protected readonly auth = inject(AuthService);

  protected readonly publishing = signal(false);
  protected readonly publishType = signal<PublishType>('FEED_IMAGE');
  protected readonly publishCaption = signal('');
  protected readonly publishFiles = signal<File[]>([]);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);

  protected readonly publishTypes: PublishType[] = [
    'FEED_IMAGE',
    'REELS',
    'STORY_IMAGE',
    'STORY_VIDEO',
    'CAROUSEL'
  ];

  protected readonly selectedAccountId = this.session.selectedAccountId;

  ngOnInit(): void {
    this.session.load();
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
}
