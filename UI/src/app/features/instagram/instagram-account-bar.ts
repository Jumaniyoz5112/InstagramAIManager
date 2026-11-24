import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { InstagramSession } from '../../core/instagram/instagram-session';
import { TranslatePipe } from '../../core/i18n/translate.pipe';

@Component({
  selector: 'app-instagram-account-bar',
  imports: [TranslatePipe, RouterLink],
  template: `
    <div class="surface-card flex flex-wrap items-center gap-2 px-3 py-2.5 sm:px-4">
      <span class="text-[11px] font-bold uppercase tracking-[0.12em] text-white/40">
        {{ 'instagram.accounts' | t }}
      </span>
      @if (session.loading() && !session.accounts().length) {
        <span class="text-sm text-white/45">{{ 'action.loading' | t }}</span>
      } @else if (!session.accounts().length) {
        <span class="text-sm text-white/45">{{ 'instagram.accounts_empty' | t }}</span>
        <a routerLink="/app/instagram" class="text-xs font-semibold text-accent-2 hover:underline">
          {{ 'instagram.open_accounts' | t }}
        </a>
      } @else {
        <select
          class="field !w-auto min-w-[12rem] max-w-full !py-1.5 text-sm"
          [value]="session.selectedAccountId() ?? ''"
          (change)="onChange($event)"
        >
          @for (account of session.accounts(); track account.id) {
            <option [value]="account.id">@{{ account.username || account.igUserId }}</option>
          }
        </select>
      }
    </div>
  `
})
export class InstagramAccountBar {
  protected readonly session = inject(InstagramSession);

  protected onChange(event: Event): void {
    const id = (event.target as HTMLSelectElement).value;
    this.session.select(id || null);
  }
}
