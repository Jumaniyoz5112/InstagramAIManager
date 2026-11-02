import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-auth-layout',
  imports: [RouterOutlet],
  template: `
    <div class="auth-shell">
      <router-outlet />
    </div>
  `,
  styles: `
    .auth-shell {
      min-height: 100dvh;
      display: grid;
      place-items: center;
      padding: 1.5rem;
      background:
        radial-gradient(circle at 20% 20%, rgba(15, 107, 120, 0.16), transparent 40%),
        linear-gradient(160deg, #f7f3ec, #e8eef0);
    }
  `
})
export class AuthLayout {}
