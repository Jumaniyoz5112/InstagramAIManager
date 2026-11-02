import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from '../auth/auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const token = auth.getAccessToken();

  const authReq =
    token && !req.url.includes('/auth/login') && !req.url.includes('/auth/refresh')
      ? req.clone({
          setHeaders: { Authorization: `Bearer ${token}` },
          withCredentials: true
        })
      : req.clone({ withCredentials: true });

  return next(authReq).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status !== 401 || req.url.includes('/auth/login') || req.url.includes('/auth/refresh')) {
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
