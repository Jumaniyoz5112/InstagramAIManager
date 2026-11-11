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

  protected readonly selectedAccount = computed(() => {
    const id = this.selectedAccountId();
    return this.accounts().find((a) => a.id === id) ?? null;
  });

  protected readonly canGenerate = computed(
    () => !!this.insightReport() && this.insightReport()?.status === 'READY' && !this.generatingScenario()
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

  protected insightText(key: string): string | null {
    const value = this.insightReport()?.result?.[key];
    if (value == null || value === '') {
      return null;
    }
    return String(value);
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
