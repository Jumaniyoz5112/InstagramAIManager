import { Injectable, computed, signal } from '@angular/core';
import { APP_LANGS, AppLang } from './i18n.models';
import { EN } from './translations/en';
import { RU } from './translations/ru';
import { UZ } from './translations/uz';

type Dict = Record<string, string>;

const DICTS: Record<AppLang, Dict> = {
  uz: UZ as unknown as Dict,
  ru: RU as unknown as Dict,
  en: EN as unknown as Dict
};

const STORAGE_KEY = 'iam_lang';

@Injectable({ providedIn: 'root' })
export class I18nService {
  private readonly langSignal = signal<AppLang>(this.readInitial());

  readonly lang = this.langSignal.asReadonly();
  readonly dict = computed(() => DICTS[this.langSignal()]);

  setLang(lang: AppLang): void {
    if (!APP_LANGS.includes(lang)) {
      return;
    }
    this.langSignal.set(lang);
    localStorage.setItem(STORAGE_KEY, lang);
    document.documentElement.lang = lang;
  }

  t(key: string, args: Array<string | number> = []): string {
    const template = this.dict()[key] ?? DICTS.en[key] ?? key;
    return args.reduce<string>(
      (text, arg, index) => text.replaceAll(`{${index}}`, String(arg)),
      template
    );
  }

  private readInitial(): AppLang {
    const saved = localStorage.getItem(STORAGE_KEY);
    if (saved && APP_LANGS.includes(saved as AppLang)) {
      document.documentElement.lang = saved;
      return saved as AppLang;
    }
    document.documentElement.lang = 'uz';
    return 'uz';
  }
}
