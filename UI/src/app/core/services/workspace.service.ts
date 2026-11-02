import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, WorkspaceSummary } from '../auth/auth.models';

export interface CreateWorkspaceRequest {
  tenantId: string;
  name: string;
}

@Injectable({ providedIn: 'root' })
export class WorkspaceService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/workspaces`;

  listByTenant(tenantId: string): Observable<WorkspaceSummary[]> {
    return this.http
      .get<ApiResponse<WorkspaceSummary[]>>(this.baseUrl, {
        params: { tenantId },
        withCredentials: true
      })
      .pipe(map((res) => res.data));
  }

  create(payload: CreateWorkspaceRequest): Observable<WorkspaceSummary> {
    return this.http
      .post<ApiResponse<WorkspaceSummary>>(this.baseUrl, payload, { withCredentials: true })
      .pipe(map((res) => res.data));
  }
}
