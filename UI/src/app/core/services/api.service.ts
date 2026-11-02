import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiStatus } from '../models/api-status';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = environment.apiBaseUrl;

  getHealth(): Observable<ApiStatus> {
    return this.http.get<ApiStatus>(`${this.baseUrl}/health`);
  }

  ping(): Observable<{ message: string }> {
    return this.http.get<{ message: string }>(`${this.baseUrl}/ping`);
  }
}
