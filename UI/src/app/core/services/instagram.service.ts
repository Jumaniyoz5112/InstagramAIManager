import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse } from '../auth/auth.models';

export type InstagramAccountStatus = 'ACTIVE' | 'NEEDS_REAUTH' | 'DISCONNECTED' | 'ERROR';
export type InstagramMediaType = 'IMAGE' | 'VIDEO' | 'CAROUSEL_ALBUM' | 'REELS' | 'UNKNOWN';
export type MediaAnalysisStatus = 'NOT_ANALYZED' | 'QUEUED' | 'RUNNING' | 'ANALYZED' | 'FAILED';
export type SyncJobStatus = 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED';

export interface InstagramAccount {
  id: string;
  tenantId: string;
  workspaceId: string;
  igUserId: string;
  username: string | null;
  accountType: string | null;
  status: InstagramAccountStatus;
  tokenExpiresAt: string | null;
  lastSyncedAt: string | null;
  createdAt: string;
}

export interface InstagramMediaChild {
  id: string;
  igMediaId: string;
  mediaType: InstagramMediaType;
  mediaUrl: string | null;
  thumbnailUrl: string | null;
  position: number;
}

export interface InstagramMedia {
  id: string;
  accountId: string;
  igMediaId: string;
  mediaType: InstagramMediaType;
  caption: string | null;
  mediaUrl: string | null;
  thumbnailUrl: string | null;
  permalink: string | null;
  publishedAt: string | null;
  analysisStatus: MediaAnalysisStatus;
  children: InstagramMediaChild[] | null;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface SyncJob {
  id: string;
  accountId: string;
  jobType: string;
  status: SyncJobStatus;
  pagesFetched: number;
  itemsUpserted: number;
  errorMessage: string | null;
  startedAt: string | null;
  finishedAt: string | null;
}

export interface OauthStartResponse {
  authorizationUrl: string;
  state: string;
}

@Injectable({ providedIn: 'root' })
export class InstagramService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/instagram`;

  startOauth(): Observable<OauthStartResponse> {
    return this.http
      .post<ApiResponse<OauthStartResponse>>(`${this.baseUrl}/oauth/start`, {}, { withCredentials: true })
      .pipe(map((res) => res.data));
  }

  listAccounts(): Observable<InstagramAccount[]> {
    return this.http
      .get<ApiResponse<InstagramAccount[]>>(`${this.baseUrl}/accounts`, { withCredentials: true })
      .pipe(map((res) => res.data));
  }

  syncAccount(accountId: string): Observable<SyncJob> {
    return this.http
      .post<ApiResponse<SyncJob>>(`${this.baseUrl}/accounts/${accountId}/sync`, {}, { withCredentials: true })
      .pipe(map((res) => res.data));
  }

  disconnectAccount(accountId: string): Observable<InstagramAccount> {
    return this.http
      .delete<ApiResponse<InstagramAccount>>(`${this.baseUrl}/accounts/${accountId}`, {
        withCredentials: true
      })
      .pipe(map((res) => res.data));
  }

  listMedia(accountId: string | null, page = 0, size = 20): Observable<PageResponse<InstagramMedia>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (accountId) {
      params = params.set('accountId', accountId);
    }
    return this.http
      .get<ApiResponse<PageResponse<InstagramMedia>>>(`${this.baseUrl}/media`, {
        params,
        withCredentials: true
      })
      .pipe(map((res) => res.data));
  }

  getMedia(mediaId: string): Observable<InstagramMedia> {
    return this.http
      .get<ApiResponse<InstagramMedia>>(`${this.baseUrl}/media/${mediaId}`, { withCredentials: true })
      .pipe(map((res) => res.data));
  }
}
