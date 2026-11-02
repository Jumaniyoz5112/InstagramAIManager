import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../auth/auth.service';
import { firstAllowedPath, hasPage } from './page-access';

export const pageGuard = (pageCode: string): CanActivateFn => {
  return () => {
    const auth = inject(AuthService);
    const router = inject(Router);
    const user = auth.currentUser();

    if (!auth.isAuthenticated()) {
      return router.createUrlTree(['/auth/login']);
    }

    if (hasPage(user, pageCode)) {
      return true;
    }

    return router.createUrlTree([firstAllowedPath(user)]);
  };
};
