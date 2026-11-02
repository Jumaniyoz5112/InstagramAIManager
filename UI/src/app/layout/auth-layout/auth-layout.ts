import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { LangSwitcher } from '../../shared/ui/lang-switcher';

@Component({
  selector: 'app-auth-layout',
  imports: [RouterOutlet, TranslatePipe, LangSwitcher],
  template: `
    <div class="relative min-h-dvh overflow-hidden bg-ink text-white">
      <div
        class="pointer-events-none absolute inset-0 bg-[radial-gradient(circle_at_15%_20%,rgba(0,194,168,0.28),transparent_35%),radial-gradient(circle_at_85%_10%,rgba(255,77,109,0.22),transparent_30%),linear-gradient(160deg,#07111f,#122033_55%,#07111f)]"
      ></div>
      <div
        class="pointer-events-none absolute inset-0 opacity-[0.08] [background-image:linear-gradient(rgba(255,255,255,0.12)_1px,transparent_1px),linear-gradient(90deg,rgba(255,255,255,0.12)_1px,transparent_1px)] [background-size:48px_48px]"
      ></div>

      <div class="absolute right-5 top-5 z-10">
        <app-lang-switcher tone="dark" />
      </div>

      <div class="relative mx-auto grid min-h-dvh max-w-6xl items-center gap-10 px-5 py-10 lg:grid-cols-2">
        <section class="hidden lg:block">
          <p class="mb-4 text-xs font-bold uppercase tracking-[0.22em] text-accent-2">
            {{ 'brand.name' | t }}
          </p>
          <h1 class="font-display max-w-lg text-5xl font-semibold leading-[1.05] tracking-tight">
            {{ 'brand.tagline' | t }}
          </h1>
          <p class="mt-5 max-w-md text-base leading-relaxed text-white/70">
            {{ 'brand.auth_lead' | t }}
          </p>
        </section>

        <section class="mx-auto w-full max-w-md">
          <router-outlet />
        </section>
      </div>
    </div>
  `
})
export class AuthLayout {}
