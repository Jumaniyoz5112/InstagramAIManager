import { Component, inject } from '@angular/core';
import { AuthService } from '../../core/auth/auth.service';
import { ApiService } from '../../core/services/api.service';
import { signal } from '@angular/core';

@Component({
  selector: 'app-dashboard',
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css'
})
export class DashboardPage {
  protected readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);

  protected readonly health = signal<string>('tekshirilmoqda…');

  constructor() {
    this.api.getHealth().subscribe({
      next: (data) => this.health.set(data['status'] ?? 'UP'),
      error: () => this.health.set('DOWN')
    });
  }
}
