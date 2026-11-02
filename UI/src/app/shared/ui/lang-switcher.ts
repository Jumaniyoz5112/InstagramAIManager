import { Component, inject, input } from '@angular/core';
import { APP_LANGS, APP_LANG_LABELS, AppLang } from '../../core/i18n/i18n.models';
import { I18nService } from '../../core/i18n/i18n.service';

@Component({
  selector: 'app-lang-switcher',
  template: `
    <div class="inline-flex items-center gap-1 rounded-xl border p-1" [class]="tone() === 'dark' ? 'border-white/15 bg-white/5' : 'border-mist bg-white'">
      @for (lang of langs; track lang) {
        <button
          type="button"
          class="rounded-lg px-2.5 py-1 text-[11px] font-bold tracking-wide transition"
          [class]="
            i18n.lang() === lang
              ? tone() === 'dark'
                ? 'bg-white text-ink'
                : 'bg-ink text-white'
              : tone() === 'dark'
                ? 'text-white/60 hover:text-white'
                : 'text-ink/50 hover:text-ink'
          "
          (click)="i18n.setLang(lang)"
        >
          {{ labels[lang] }}
        </button>
      }
    </div>
  `
})
export class LangSwitcher {
  protected readonly i18n = inject(I18nService);
  protected readonly langs = APP_LANGS;
  protected readonly labels = APP_LANG_LABELS;
  readonly tone = input<'light' | 'dark'>('light');
}
