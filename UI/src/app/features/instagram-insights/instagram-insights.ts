import { DecimalPipe } from '@angular/common';
import { Component, effect, inject, OnInit, signal, untracked } from '@angular/core';
import { translateApiError } from '../../core/i18n/api-error';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { InstagramSession } from '../../core/instagram/instagram-session';
import { InsightReport, InstagramService, LeaderboardItem, ContentScenario } from '../../core/services/instagram.service';
import { InstagramAccountBar } from '../instagram/instagram-account-bar';

@Component({
  selector: 'app-instagram-insights',
  imports: [TranslatePipe, DecimalPipe, InstagramAccountBar],
  templateUrl: './instagram-insights.html',
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
export class InstagramInsightsPage implements OnInit {
  private readonly api = inject(InstagramService);
  private readonly i18n = inject(I18nService);
  protected readonly session = inject(InstagramSession);

  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);
  protected readonly leaderboard = signal<LeaderboardItem[]>([]);
  protected readonly loadingLeaderboard = signal(false);
  protected readonly insightReport = signal<InsightReport | null>(null);
  protected readonly loadingInsights = signal(false);
  protected readonly generatingInsights = signal(false);
  protected readonly scenario = signal<ContentScenario | null>(null);
  protected readonly generatingScenario = signal(false);
  protected readonly scenarioBrief = signal('');
  protected readonly copyingScenario = signal(false);

  private watchedAccountId: string | null | undefined = undefined;

  constructor() {
    effect(() => {
      const ready = this.session.ready();
      const accountId = this.session.selectedAccountId();
      if (!ready || accountId === this.watchedAccountId) {
        return;
      }
      this.watchedAccountId = accountId;
      untracked(() => this.loadInsightsPanel());
    });
  }

  ngOnInit(): void {
    this.session.load();
  }

  protected generateInsights(): void {
    const accountId = this.session.selectedAccountId();
    if (!accountId) {
      return;
    }
    this.generatingInsights.set(true);
    this.error.set(null);
    this.api.generateInsights(accountId).subscribe({
      next: (report) => {
        this.generatingInsights.set(false);
        this.insightReport.set(report);
        this.notice.set(this.i18n.t('instagram.insights_ready'));
        this.loadInsightsPanel();
      },
      error: (err: unknown) => {
        this.generatingInsights.set(false);
        this.error.set(translateApiError(this.i18n, err, 'instagram.insights_failed'));
      }
    });
  }

  protected insightList(key: string): string[] {
    const value = this.insightReport()?.result?.[key];
    if (!Array.isArray(value)) {
      return [];
    }
    return value.map(String).map((item) => item.trim()).filter(Boolean);
  }

  protected insightText(key: string): string | null {
    const value = this.insightReport()?.result?.[key];
    if (value == null || value === '') {
      return null;
    }
    return String(value);
  }

  protected metricValue(value: number | null | undefined): string {
    return value == null ? '—' : String(value);
  }

  private loadInsightsPanel(): void {
    const accountId = this.session.selectedAccountId();
    if (!accountId) {
      this.leaderboard.set([]);
      this.insightReport.set(null);
      this.scenario.set(null);
      return;
    }
    this.loadingLeaderboard.set(true);
    this.api.leaderboard(accountId, 20).subscribe({
      next: (items) => {
        this.leaderboard.set(items);
        this.loadingLeaderboard.set(false);
      },
      error: (err: unknown) => {
        this.loadingLeaderboard.set(false);
        this.error.set(translateApiError(this.i18n, err, 'instagram.insights_load_failed'));
      }
    });
    this.loadingInsights.set(true);
    this.api.latestInsights(accountId).subscribe({
      next: (report) => {
        this.insightReport.set(report);
        this.loadingInsights.set(false);
        if (report.status === 'READY') {
          this.api.latestScenario(report.id).subscribe({
            next: (item) => this.scenario.set(item),
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

  protected canGenerateScenario(): boolean {
    return this.insightReport()?.status === 'READY' && !this.generatingScenario();
  }

  protected canCopyScenario(): boolean {
    return this.scenario()?.status === 'READY' && !this.copyingScenario();
  }

  protected scenarioBriefChange(event: Event): void {
    this.scenarioBrief.set((event.target as HTMLTextAreaElement).value);
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
      .generateScenario(report.id, { brief: this.scenarioBrief().trim() || null })
      .subscribe({
        next: (item) => {
          this.generatingScenario.set(false);
          this.scenario.set(item);
          this.notice.set(this.i18n.t('instagram.scenario_ready'));
        },
        error: (err: unknown) => {
          this.generatingScenario.set(false);
          this.error.set(translateApiError(this.i18n, err, 'instagram.scenario_failed'));
        }
      });
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
      this.error.set(this.i18n.t('instagram.scenario_copy_failed'));
    } finally {
      this.copyingScenario.set(false);
    }
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
    return value.map(String).map((item) => item.trim()).filter(Boolean);
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
      .filter((shot) => shot.visualUz || shot.overlayTextUz);
  }

  private buildScenarioCopyText(): string {
    const lines: string[] = [];
    const title = this.scenarioText('titleUz');
    const goal = this.scenarioText('goalUz');
    const hook = this.scenarioText('hookUz');
    const caption = this.scenarioText('captionPrimaryUz');
    const cta = this.scenarioText('ctaUz');
    if (title) {
      lines.push(title);
    }
    if (goal) {
      lines.push(goal);
    }
    if (hook) {
      lines.push('', `${this.i18n.t('instagram.analysis_hook')}: ${hook}`);
    }
    const shots = this.scenarioShots();
    if (shots.length) {
      lines.push('', this.i18n.t('instagram.scenario_shots') + ':');
      shots.forEach((shot, index) => {
        lines.push(`${index + 1}) ${shot.startSec}–${shot.endSec}s ${shot.visualUz}`);
        if (shot.overlayTextUz) {
          lines.push(`   ${shot.overlayTextUz}`);
        }
      });
    }
    if (caption) {
      lines.push('', caption);
    }
    if (cta) {
      lines.push('', cta);
    }
    return lines.join('\n').trim();
  }
}
