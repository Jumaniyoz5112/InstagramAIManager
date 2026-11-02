import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse } from '../auth/auth.models';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  /** Legacy health check (non-versioned). */
  getHealth(): Observable<Record<string, string>> {
    return this.http
      .get<ApiResponse<Record<string, string>>>('/api/health', { withCredentials: true })
      .pipe(map((res) => res.data));
  }

  get<T>(path: string): Observable<T> {
    return this.http
      .get<ApiResponse<T>>(`${environment.apiBaseUrl}${path}`, { withCredentials: true })
      .pipe(map((res) => res.data));
  }

  post<T>(path: string, body: unknown): Observable<T> {
    return this.http
      .post<ApiResponse<T>>(`${environment.apiBaseUrl}${path}`, body, { withCredentials: true })
      .pipe(map((res) => res.data));
  }
}
