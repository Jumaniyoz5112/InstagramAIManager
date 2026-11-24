import { DatePipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { translateApiError } from '../../core/i18n/api-error';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { InstagramSession } from '../../core/instagram/instagram-session';
import {
  InstagramAccount,
  InstagramService,
  OauthPickCandidate
} from '../../core/services/instagram.service';
import { AppIcon } from '../../shared/ui/icon';

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
  protected readonly session = inject(InstagramSession);
  protected readonly auth = inject(AuthService);

  protected readonly connecting = signal(false);
  protected readonly syncingId = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);
  protected readonly oauthPickId = signal<string | null>(null);
  protected readonly oauthPickCandidates = signal<OauthPickCandidate[]>([]);
  protected readonly loadingOauthPick = signal(false);
  protected readonly confirmingOauthPick = signal(false);

  ngOnInit(): void {
    const connected = this.route.snapshot.queryParamMap.get('connected');
    const igError = this.route.snapshot.queryParamMap.get('ig_error');
    const igPick = this.route.snapshot.queryParamMap.get('ig_pick');
    if (connected || igPick || igError) {
      void this.router.navigate([], {
        relativeTo: this.route,
        queryParams: {},
        replaceUrl: true
      });
    }
    if (connected) {
      this.notice.set(this.i18n.t('instagram.connected'));
    } else if (igPick) {
      this.openOauthPick(igPick);
    } else if (igError) {
      const key = decodeURIComponent(igError);
      const translated = this.i18n.t(key);
      this.error.set(translated !== key ? translated : this.i18n.t('instagram.connect_failed'));
    }
    this.session.load();
  }

  protected reload(): void {
    this.session.load();
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
        this.session.load();
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
        if (this.session.selectedAccountId() === accountId) {
          this.session.select(null);
        }
        this.session.load();
      },
      error: (err: unknown) => {
        this.error.set(translateApiError(this.i18n, err, 'instagram.disconnect_failed'));
      }
    });
  }

  protected selectAccount(accountId: string): void {
    this.session.select(accountId);
  }

  protected openOauthPick(pickId: string): void {
    this.oauthPickId.set(pickId);
    this.oauthPickCandidates.set([]);
    this.loadingOauthPick.set(true);
    this.error.set(null);
    this.api.getOauthPickSession(pickId).subscribe({
      next: (session) => {
        this.oauthPickCandidates.set(session.candidates || []);
        this.loadingOauthPick.set(false);
      },
      error: (err: unknown) => {
        this.loadingOauthPick.set(false);
        this.closeOauthPick();
        this.error.set(translateApiError(this.i18n, err, 'instagram.pick_load_failed'));
      }
    });
  }

  protected closeOauthPick(): void {
    this.oauthPickId.set(null);
    this.oauthPickCandidates.set([]);
    this.loadingOauthPick.set(false);
    this.confirmingOauthPick.set(false);
  }

  protected confirmOauthPick(candidate: OauthPickCandidate): void {
    const pickId = this.oauthPickId();
    if (!pickId) {
      return;
    }
    this.confirmingOauthPick.set(true);
    this.error.set(null);
    this.api.confirmOauthPick(pickId, candidate.igUserId).subscribe({
      next: (account) => {
        this.confirmingOauthPick.set(false);
        this.closeOauthPick();
        this.notice.set(this.i18n.t('instagram.connected'));
        this.session.select(account.id);
        this.session.load();
      },
      error: (err: unknown) => {
        this.confirmingOauthPick.set(false);
        this.error.set(translateApiError(this.i18n, err, 'instagram.pick_confirm_failed'));
      }
    });
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
}
