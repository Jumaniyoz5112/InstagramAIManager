import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, finalize, map, of, shareReplay, tap, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  ApiResponse,
  AuthTokensResponse,
  LoginRequest,
  UserResponse
} from './auth.models';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly baseUrl = environment.apiBaseUrl;

  private readonly accessTokenSignal = signal<string | null>(null);
  private readonly userSignal = signal<UserResponse | null>(null);
  private refreshInFlight$: Observable<string> | null = null;

  readonly accessToken = this.accessTokenSignal.asReadonly();
  readonly currentUser = this.userSignal.asReadonly();
  readonly isAuthenticated = computed(() => !!this.accessTokenSignal());

  login(payload: LoginRequest): Observable<UserResponse> {
    return this.http
      .post<ApiResponse<AuthTokensResponse>>(`${this.baseUrl}/auth/login`, payload, {
        withCredentials: true
      })
      .pipe(
        tap((res) => this.applySession(res.data)),
        map((res) => res.data.user)
      );
  }

  refresh(): Observable<string> {
    if (this.refreshInFlight$) {
      return this.refreshInFlight$;
    }

    this.refreshInFlight$ = this.http
      .post<ApiResponse<AuthTokensResponse>>(`${this.baseUrl}/auth/refresh`, {}, { withCredentials: true })
      .pipe(
        tap((res) => this.applySession(res.data)),
        map((res) => res.data.accessToken),
        catchError((err) => {
          this.clearSession();
          return throwError(() => err);
        }),
        finalize(() => {
          this.refreshInFlight$ = null;
        }),
        shareReplay(1)
      );

    return this.refreshInFlight$;
  }

  logout(): Observable<void> {
    return this.http
      .post<ApiResponse<null>>(`${this.baseUrl}/auth/logout`, {}, { withCredentials: true })
      .pipe(
        catchError(() => of(null)),
        tap(() => {
          this.clearSession();
          void this.router.navigateByUrl('/auth/login');
        }),
        map(() => void 0)
      );
  }

  me(): Observable<UserResponse> {
    return this.http
      .get<ApiResponse<UserResponse>>(`${this.baseUrl}/auth/me`, { withCredentials: true })
      .pipe(
        tap((res) => this.userSignal.set(res.data)),
        map((res) => res.data)
      );
  }

  activateWorkspace(workspaceId: string): Observable<UserResponse> {
    return this.http
      .post<ApiResponse<AuthTokensResponse>>(
        `${this.baseUrl}/auth/workspaces/${workspaceId}/activate`,
        {},
        { withCredentials: true }
      )
      .pipe(
        tap((res) => this.applySession(res.data)),
        map((res) => res.data.user)
      );
  }

  getAccessToken(): string | null {
    return this.accessTokenSignal();
  }

  private applySession(tokens: AuthTokensResponse): void {
    this.accessTokenSignal.set(tokens.accessToken);
    this.userSignal.set(tokens.user);
  }

  private clearSession(): void {
    this.accessTokenSignal.set(null);
    this.userSignal.set(null);
  }
}
