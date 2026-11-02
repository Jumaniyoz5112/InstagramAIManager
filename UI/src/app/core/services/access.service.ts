import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, PageAccess } from '../auth/auth.models';

export interface UserAccessRow {
  id: string;
  email: string;
  fullName: string;
  platformAdmin: boolean;
  enabled: boolean;
  pageCodes: string[];
}

@Injectable({ providedIn: 'root' })
export class AccessAdminService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/access`;

  listPages(): Observable<PageAccess[]> {
    return this.http
      .get<ApiResponse<PageAccess[]>>(`${this.baseUrl}/pages`, { withCredentials: true })
      .pipe(map((res) => res.data));
  }

  listUsers(): Observable<UserAccessRow[]> {
    return this.http
      .get<ApiResponse<UserAccessRow[]>>(`${this.baseUrl}/users`, { withCredentials: true })
      .pipe(map((res) => res.data));
  }

  updateUserPages(userId: string, pageCodes: string[]): Observable<UserAccessRow> {
    return this.http
      .put<ApiResponse<UserAccessRow>>(
        `${this.baseUrl}/users/${userId}/pages`,
        { pageCodes },
        { withCredentials: true }
      )
      .pipe(map((res) => res.data));
  }
}
