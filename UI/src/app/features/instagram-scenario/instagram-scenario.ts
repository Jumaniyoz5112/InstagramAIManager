import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { translateApiError } from '../../core/i18n/api-error';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import {
  ContentScenario,
  InsightReport,
  InstagramAccount,
  InstagramService
} from '../../core/services/instagram.service';
@Component({
  selector: 'app-instagram-scenario',
  imports: [TranslatePipe, RouterLink],
  templateUrl: './instagram-scenario.html',
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
export class InstagramScenarioPage implements OnInit {
  private readonly api = inject(InstagramService);
  private readonly i18n = inject(I18nService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);
  protected readonly loadingAccounts = signal(false);
  protected readonly accounts = signal<InstagramAccount[]>([]);
  protected readonly selectedAccountId = signal<string | null>(null);

  protected readonly insightReport = signal<InsightReport | null>(null);
  protected readonly loadingInsights = signal(false);
  protected readonly scenario = signal<ContentScenario | null>(null);
  protected readonly generatingScenario = signal(false);
  protected readonly scenarioBrief = signal('');
  protected readonly copyingScenario = signal(false);

  protected readonly selectedAccount = computed(() => {
    const id = this.selectedAccountId();
    return this.accounts().find((a) => a.id === id) ?? null;
  });

  protected readonly canGenerate = computed(
    () => !!this.insightReport() && this.insightReport()?.status === 'READY' && !this.generatingScenario()
  );

  protected readonly canCopyScenario = computed(
    () => this.scenario()?.status === 'READY' && !this.copyingScenario()
  );

  ngOnInit(): void {
    const fromQuery = this.route.snapshot.queryParamMap.get('accountId');
    if (fromQuery) {
      this.selectedAccountId.set(fromQuery);
    }
    this.loadAccounts();
  }

  protected selectAccount(accountId: string): void {
    this.selectedAccountId.set(accountId);
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { accountId },
      replaceUrl: true
    });
    this.loadInsightsAndScenario();
  }

  protected scenarioBriefChange(event: Event): void {
    this.scenarioBrief.set((event.target as HTMLTextAreaElement).value);
  }

  protected async copyScenario(): Promise<void> {
    if (this.scenario()?.status !== 'READY') {
      return;
    }
    const text = this.buildScenarioCopyText();
    if (!text.trim()) {
      this.error.set(this.i18n.t('instagram.scenario_copy_failed'));
      return;
    }
    this.copyingScenario.set(true);
    this.error.set(null);
    try {
      await navigator.clipboard.writeText(text);
      this.notice.set(this.i18n.t('instagram.scenario_copied'));
    } catch {
      try {
        this.copyViaTextarea(text);
        this.notice.set(this.i18n.t('instagram.scenario_copied'));
      } catch {
        this.error.set(this.i18n.t('instagram.scenario_copy_failed'));
      }
    } finally {
      this.copyingScenario.set(false);
    }
  }

  protected generateScenario(): void {
    const report = this.insightReport();
    if (!report || report.status !== 'READY') {
      this.error.set(this.i18n.t('instagram.insights_need_report'));
      return;
    }
    this.generatingScenario.set(true);
    this.error.set(null);
    this.api
      .generateScenario(report.id, {
        brief: this.scenarioBrief().trim() || null
      })
      .subscribe({
        next: (sc) => {
          this.generatingScenario.set(false);
          this.scenario.set(sc);
          this.notice.set(this.i18n.t('instagram.scenario_ready'));
        },
        error: (err: unknown) => {
          this.generatingScenario.set(false);
          this.error.set(translateApiError(this.i18n, err, 'instagram.scenario_failed'));
        }
      });
  }

  protected scenarioText(key: string): string | null {
    const value = this.scenario()?.result?.[key];
    if (value == null || value === '') {
      return null;
    }
    if (Array.isArray(value)) {
      return value.map(String).join('\n');
    }
    return String(value);
  }

  protected scenarioList(key: string): string[] {
    const value = this.scenario()?.result?.[key];
    if (!Array.isArray(value)) {
      return [];
    }
    return value.map(String).map((s) => s.trim()).filter(Boolean);
  }

  protected scenarioShots(): Array<{
    startSec: number;
    endSec: number;
    visualUz: string;
    overlayTextUz: string;
    purpose: string;
    noteUz: string;
  }> {
    const raw = this.scenario()?.result?.['shots'];
    if (!Array.isArray(raw)) {
      return [];
    }
    return raw
      .filter((item): item is Record<string, unknown> => !!item && typeof item === 'object')
      .map((item, index) => {
        const start = Number(item['startSec'] ?? index * 2);
        const end = Number(item['endSec'] ?? start + 2);
        return {
          startSec: Number.isFinite(start) ? start : index * 2,
          endSec: Number.isFinite(end) ? end : index * 2 + 2,
          visualUz: String(item['visualUz'] ?? '').trim(),
          overlayTextUz: String(item['overlayTextUz'] ?? '').trim(),
          purpose: String(item['purpose'] ?? '').trim() || 'body',
          noteUz: String(item['noteUz'] ?? '').trim()
        };
      })
      .filter((s) => s.visualUz || s.overlayTextUz);
  }

  protected insightText(key: string): string | null {
    const value = this.insightReport()?.result?.[key];
    if (value == null || value === '') {
      return null;
    }
    return String(value);
  }

  private buildScenarioCopyText(): string {
    const lines: string[] = [];
    const title = this.scenarioText('titleUz');
    const goal = this.scenarioText('goalUz');
    const format = this.scenarioText('format');
    const aspect = this.scenarioText('aspectRatio');
    const duration = this.scenarioText('durationSec');
    const music = this.scenarioText('musicMoodUz');
    const hook = this.scenarioText('hookUz');
    const caption = this.scenarioText('captionPrimaryUz');
    const cta = this.scenarioText('ctaUz');
    const why = this.scenarioText('whyThisWorksUz');
    const tips = this.scenarioList('postingTipsUz');
    const tags = this.scenarioList('hashtagsUz');
    const shots = this.scenarioShots();

    if (title) {
      lines.push(title);
    }
    if (goal) {
      lines.push(goal);
    }
    const meta = [format, aspect, duration ? `${duration}s` : null].filter(Boolean).join(' · ');
    if (meta) {
      lines.push(meta);
    }
    if (music) {
      lines.push(`${this.i18n.t('instagram.scenario_music')}: ${music}`);
    }
    if (hook) {
      lines.push('', `${this.i18n.t('instagram.analysis_hook')}: ${hook}`);
    }
    if (shots.length) {
      lines.push('', this.i18n.t('instagram.scenario_shots') + ':');
      shots.forEach((s, i) => {
        lines.push(
          `${i + 1}) ${s.startSec}–${s.endSec}s [${s.purpose}]`,
          `   ${this.i18n.t('instagram.scenario_shot_visual')}: ${s.visualUz}`,
          `   ${this.i18n.t('instagram.scenario_shot_overlay')}: ${s.overlayTextUz}`
        );
        if (s.noteUz) {
          lines.push(`   ${this.i18n.t('instagram.scenario_shot_note')}: ${s.noteUz}`);
        }
      });
    } else {
      const legacy = this.scenarioList('shotListUz');
      if (legacy.length) {
        lines.push('', this.i18n.t('instagram.scenario_shots') + ':');
        legacy.forEach((s, i) => lines.push(`${i + 1}) ${s}`));
      }
    }
    if (caption) {
      lines.push('', `${this.i18n.t('instagram.scenario_caption')}:`, caption);
    }
    if (cta) {
      lines.push('', `${this.i18n.t('instagram.scenario_cta')}: ${cta}`);
    }
    if (why) {
      lines.push('', `${this.i18n.t('instagram.scenario_why')}: ${why}`);
    }
    if (tips.length) {
      lines.push('', `${this.i18n.t('instagram.scenario_tips')}:`);
      tips.forEach((t) => lines.push(`- ${t}`));
    }
    if (tags.length) {
      lines.push('', tags.join(' '));
    }
    return lines.join('\n').trim();
  }

  private copyViaTextarea(text: string): void {
    const el = document.createElement('textarea');
    el.value = text;
    el.setAttribute('readonly', '');
    el.style.position = 'fixed';
    el.style.left = '-9999px';
    document.body.appendChild(el);
    el.select();
    const ok = document.execCommand('copy');
    document.body.removeChild(el);
    if (!ok) {
      throw new Error('copy failed');
    }
  }

  private loadAccounts(): void {
    this.loadingAccounts.set(true);
    this.error.set(null);
    this.api.listAccounts().subscribe({
      next: (items) => {
        this.accounts.set(items);
        this.loadingAccounts.set(false);
        const current = this.selectedAccountId();
        const nextId =
          current && items.some((a) => a.id === current) ? current : (items[0]?.id ?? null);
        this.selectedAccountId.set(nextId);
        if (nextId) {
          void this.router.navigate([], {
            relativeTo: this.route,
            queryParams: { accountId: nextId },
            replaceUrl: true
          });
          this.loadInsightsAndScenario();
        }
      },
      error: (err: unknown) => {
        this.loadingAccounts.set(false);
        this.error.set(translateApiError(this.i18n, err, 'instagram.load_failed'));
      }
    });
  }

  private loadInsightsAndScenario(): void {
    const accountId = this.selectedAccountId();
    if (!accountId) {
      this.insightReport.set(null);
      this.scenario.set(null);
      return;
    }
    this.loadingInsights.set(true);
    this.api.latestInsights(accountId).subscribe({
      next: (report) => {
        this.insightReport.set(report);
        this.loadingInsights.set(false);
        if (report.status === 'READY') {
          this.api.latestScenario(report.id).subscribe({
            next: (sc) => this.scenario.set(sc),
            error: () => this.scenario.set(null)
          });
        } else {
          this.scenario.set(null);
        }
      },
      error: () => {
        this.insightReport.set(null);
        this.scenario.set(null);
        this.loadingInsights.set(false);
      }
    });
  }
}
