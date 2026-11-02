import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ApiService } from './core/services/api.service';
import { ApiStatus } from './core/models/api-status';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App implements OnInit {
  private readonly api = inject(ApiService);

  protected readonly title = signal('Instagram AI Manager');
  protected readonly backendStatus = signal<'loading' | 'up' | 'down'>('loading');
  protected readonly health = signal<ApiStatus | null>(null);
  protected readonly errorMessage = signal<string | null>(null);

  ngOnInit(): void {
    this.checkBackend();
  }

  protected checkBackend(): void {
    this.backendStatus.set('loading');
    this.errorMessage.set(null);

    this.api.getHealth().subscribe({
      next: (response) => {
        this.health.set(response);
        this.backendStatus.set(response.status === 'UP' ? 'up' : 'down');
      },
      error: (err: unknown) => {
        this.health.set(null);
        this.backendStatus.set('down');
        this.errorMessage.set(
          err instanceof Error
            ? err.message
            : 'Backendga ulanib bo‘lmadi. BE ishlayotganini tekshiring (http://localhost:8080).'
        );
      }
    });
  }
}
