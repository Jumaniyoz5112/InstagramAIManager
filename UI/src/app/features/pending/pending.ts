import { Component } from '@angular/core';
import { TranslatePipe } from '../../core/i18n/translate.pipe';

@Component({
  selector: 'app-pending',
  imports: [TranslatePipe],
  template: `
    <section class="surface-card mx-auto max-w-xl p-8 text-center">
      <p class="text-xs font-bold uppercase tracking-[0.16em] text-warn">{{ 'pending.eyebrow' | t }}</p>
      <h2 class="font-display mt-3 text-3xl font-semibold tracking-tight text-white">{{ 'pending.title' | t }}</h2>
      <p class="mt-3 text-sm leading-relaxed text-white/55">{{ 'pending.body' | t }}</p>
    </section>
  `
})
export class PendingPage {}
