import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';

export const errorInterceptor: HttpInterceptorFn = (req, next) =>
  next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      const key =
        typeof error.error === 'object' && error.error !== null && 'messageKey' in error.error
          ? String((error.error as { messageKey?: string }).messageKey ?? '')
          : '';
      if (error.status === 403) {
        console.error('Forbidden', key || 'error.security.forbidden');
      }
      return throwError(() => error);
    })
  );
