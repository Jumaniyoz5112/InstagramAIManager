import { Component, input } from '@angular/core';

export type AppIconName =
  | 'dashboard'
  | 'tenants'
  | 'workspaces'
  | 'logout'
  | 'activity'
  | 'shield'
  | 'plus'
  | 'check';

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
      }
    </svg>
  `
})
export class AppIcon {
  readonly name = input.required<AppIconName>();
  readonly size = input(18);
}
