import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, TenantSummary } from '../auth/auth.models';

export interface CreateTenantRequest {
  name: string;
  slug: string;
}

@Injectable({ providedIn: 'root' })
export class TenantService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/tenants`;

  list(): Observable<TenantSummary[]> {
    return this.http
      .get<ApiResponse<TenantSummary[]>>(this.baseUrl, { withCredentials: true })
      .pipe(map((res) => res.data));
  }

  create(payload: CreateTenantRequest): Observable<TenantSummary> {
    return this.http
      .post<ApiResponse<TenantSummary>>(this.baseUrl, payload, { withCredentials: true })
      .pipe(map((res) => res.data));
  }
}
