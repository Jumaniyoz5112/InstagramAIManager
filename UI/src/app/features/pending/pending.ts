import { Component } from '@angular/core';
import { TranslatePipe } from '../../core/i18n/translate.pipe';

@Component({
  selector: 'app-pending',
  imports: [TranslatePipe],
  template: `
    <section class="surface-card mx-auto max-w-xl p-8 text-center">
      <p class="text-sm leading-relaxed text-white/55">{{ 'pending.body' | t }}</p>
    </section>
  `
})
export class PendingPage {}
