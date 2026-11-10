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
  likeCount: number | null;
  commentsCount: number | null;
  sharesCount: number | null;
  savesCount: number | null;
  children: InstagramMediaChild[] | null;
}

export interface InstagramMediaComment {
  id: string;
  igCommentId: string;
  parentIgCommentId: string | null;
  username: string | null;
  text: string | null;
  likeCount: number | null;
  publishedAt: string | null;
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

export type PublishType = 'FEED_IMAGE' | 'REELS' | 'STORY_IMAGE' | 'STORY_VIDEO' | 'CAROUSEL';
export type PublishJobStatus =
  | 'PENDING'
  | 'UPLOADING'
  | 'CONTAINER_CREATED'
  | 'PROCESSING'
  | 'PUBLISHING'
  | 'PUBLISHED'
  | 'FAILED';

export interface InstagramPublishJob {
  id: string;
  accountId: string;
  publishType: PublishType;
  status: PublishJobStatus;
  caption: string | null;
  containerId: string | null;
  publishedIgMediaId: string | null;
  errorMessage: string | null;
  startedAt: string | null;
  finishedAt: string | null;
  createdAt: string;
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

  listMedia(
    accountId: string | null,
    page = 0,
    size = 20,
    mediaType: InstagramMediaType | null = null
  ): Observable<PageResponse<InstagramMedia>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (accountId) {
      params = params.set('accountId', accountId);
    }
    if (mediaType) {
      params = params.set('mediaType', mediaType);
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

  listComments(mediaId: string): Observable<InstagramMediaComment[]> {
    return this.http
      .get<ApiResponse<InstagramMediaComment[]>>(`${this.baseUrl}/media/${mediaId}/comments`, {
        withCredentials: true
      })
      .pipe(map((res) => res.data));
  }

  publish(params: {
    accountId: string;
    publishType: PublishType;
    caption?: string;
    files: File[];
    idempotencyKey?: string;
  }): Observable<InstagramPublishJob> {
    const form = new FormData();
    form.append('accountId', params.accountId);
    form.append('publishType', params.publishType);
    if (params.caption?.trim()) {
      form.append('caption', params.caption.trim());
    }
    if (params.idempotencyKey) {
      form.append('idempotencyKey', params.idempotencyKey);
    }
    for (const file of params.files) {
      form.append('files', file, file.name);
    }
    return this.http
      .post<ApiResponse<InstagramPublishJob>>(`${this.baseUrl}/publish`, form, { withCredentials: true })
      .pipe(map((res) => res.data));
  }

  listPublishJobs(accountId: string): Observable<InstagramPublishJob[]> {
    const params = new HttpParams().set('accountId', accountId);
    return this.http
      .get<ApiResponse<InstagramPublishJob[]>>(`${this.baseUrl}/publish`, {
        params,
        withCredentials: true
      })
      .pipe(map((res) => res.data));
  }
}
