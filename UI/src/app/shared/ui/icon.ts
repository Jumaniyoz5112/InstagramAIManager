import { Component, input } from '@angular/core';

export type AppIconName =
  | 'dashboard'
  | 'tenants'
  | 'workspaces'
  | 'logout'
  | 'activity'
  | 'shield'
  | 'plus'
  | 'check'
  | 'instagram'
  | 'media'
  | 'publish'
  | 'insights'
  | 'scenario'
  | 'switch'
  | 'voice'
  | 'menu'
  | 'close';

@Component({
  selector: 'app-icon',
  template: `
    <svg
      [attr.width]="size()"
      [attr.height]="size()"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="1.8"
      stroke-linecap="round"
      stroke-linejoin="round"
      aria-hidden="true"
    >
      @switch (name()) {
        @case ('dashboard') {
          <rect x="3" y="3" width="7" height="9" rx="1.5" />
          <rect x="14" y="3" width="7" height="5" rx="1.5" />
          <rect x="14" y="12" width="7" height="9" rx="1.5" />
          <rect x="3" y="16" width="7" height="5" rx="1.5" />
        }
        @case ('tenants') {
          <path d="M3 21h18" />
          <path d="M5 21V8l7-4 7 4v13" />
          <path d="M9 21v-6h6v6" />
        }
        @case ('workspaces') {
          <rect x="3" y="4" width="18" height="14" rx="2" />
          <path d="M3 9h18" />
          <path d="M8 21h8" />
        }
        @case ('logout') {
          <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
          <path d="M16 17l5-5-5-5" />
          <path d="M21 12H9" />
        }
        @case ('activity') {
          <path d="M22 12h-4l-3 7-6-14-3 7H2" />
        }
        @case ('shield') {
          <path d="M12 3l8 3v6c0 5-3.4 8.4-8 9-4.6-.6-8-4-8-9V6l8-3z" />
          <path d="M9.5 12.5l1.8 1.8 3.7-3.8" />
        }
        @case ('plus') {
          <path d="M12 5v14" />
          <path d="M5 12h14" />
        }
        @case ('check') {
          <path d="M20 6 9 17l-5-5" />
        }
        @case ('instagram') {
          <rect x="3" y="3" width="18" height="18" rx="5" />
          <circle cx="12" cy="12" r="4" />
          <circle cx="17.5" cy="6.5" r="1" fill="currentColor" stroke="none" />
        }
        @case ('media') {
          <rect x="3" y="5" width="18" height="14" rx="2" />
          <circle cx="9" cy="10" r="1.5" />
          <path d="m21 15-4.5-4.5L7 19" />
        }
        @case ('publish') {
          <path d="M12 19V5" />
          <path d="m6 11 6-6 6 6" />
          <path d="M5 21h14" />
        }
        @case ('insights') {
          <path d="M4 19V10" />
          <path d="M10 19V5" />
          <path d="M16 19v-7" />
          <path d="M22 19H2" />
        }
        @case ('scenario') {
          <path d="M8 4h8a2 2 0 0 1 2 2v14l-6-3-6 3V6a2 2 0 0 1 2-2z" />
          <path d="M9 9h6" />
          <path d="M9 13h6" />
        }
        @case ('switch') {
          <path d="M16 3h5v5" />
          <path d="M21 3l-7 7" />
          <path d="M8 21H3v-5" />
          <path d="M3 21l7-7" />
        }
        @case ('voice') {
          <path d="M11 5 6 9H3v6h3l5 4V5z" />
          <path d="M15.5 8.5a5 5 0 0 1 0 7" />
          <path d="M18 6a8.5 8.5 0 0 1 0 12" />
        }
        @case ('menu') {
          <path d="M4 7h16" />
          <path d="M4 12h16" />
          <path d="M4 17h16" />
        }
        @case ('close') {
          <path d="M6 6l12 12" />
          <path d="M18 6 6 18" />
        }
      }
    </svg>
  `
})
export class AppIcon {
  readonly name = input.required<AppIconName>();
  readonly size = input(18);
}
