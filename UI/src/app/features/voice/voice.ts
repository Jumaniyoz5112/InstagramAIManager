import { Component, DestroyRef, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { I18nService } from '../../core/i18n/i18n.service';
import { InstagramService } from '../../core/services/instagram.service';

@Component({
  selector: 'app-voice-page',
  imports: [TranslatePipe],
  templateUrl: './voice.html'
})
export class VoicePage {
  private readonly api = inject(InstagramService);
  private readonly i18n = inject(I18nService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly voices = [
    { id: 'uz-mms', labelKey: 'voice.uz_mms' },
    { id: 'uz-girl', labelKey: 'voice.uz_girl' }
  ] as const;
  protected readonly selected = signal('uz-mms');
  protected readonly text = signal('');
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly audioUrl = signal<string | null>(null);

  constructor() {
    this.destroyRef.onDestroy(() => this.clearAudio());
  }

  protected speak(): void {
    const line = this.text().trim();
    if (!line || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.api.speak(line, this.selected()).subscribe({
      next: (blob) => {
        this.busy.set(false);
        if (!blob || blob.size < 44 || (blob.type && blob.type.includes('json'))) {
          this.error.set(this.i18n.t('error.speech.failed'));
          return;
        }
        this.clearAudio();
        this.audioUrl.set(URL.createObjectURL(blob));
      },
      error: (err: unknown) => {
        this.busy.set(false);
        this.error.set(this.message(err));
      }
    });
  }

  private message(err: unknown): string {
    if (err instanceof HttpErrorResponse && err.error && typeof err.error === 'object' && 'messageKey' in err.error) {
      const key = String((err.error as { messageKey?: string }).messageKey ?? '');
      if (key) {
        return this.i18n.t(key);
      }
    }
    return this.i18n.t('error.speech.failed');
  }

  private clearAudio(): void {
    const url = this.audioUrl();
    if (url) {
      URL.revokeObjectURL(url);
      this.audioUrl.set(null);
    }
  }
}
