import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from '../auth/auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const token = auth.getAccessToken();

  const isPublicAuth =
    req.url.includes('/auth/login') ||
    req.url.includes('/auth/register') ||
    req.url.includes('/auth/refresh');

  const authReq =
    token && !isPublicAuth
      ? req.clone({
          setHeaders: { Authorization: `Bearer ${token}` },
          withCredentials: true
        })
      : req.clone({ withCredentials: true });

  return next(authReq).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status !== 401 || isPublicAuth) {
        return throwError(() => error);
      }

      return auth.refresh().pipe(
        switchMap((newToken) =>
          next(
            req.clone({
              setHeaders: { Authorization: `Bearer ${newToken}` },
              withCredentials: true
            })
          )
        ),
        catchError((refreshError) => throwError(() => refreshError))
      );
    })
  );
};
