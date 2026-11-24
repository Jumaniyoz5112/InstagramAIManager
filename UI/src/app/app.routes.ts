import { inject } from '@angular/core';
import { Router, Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';
import { pageGuard } from './core/access/page.guard';
import { AuthService } from './core/auth/auth.service';
import { firstAllowedPath } from './core/access/page-access';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'app'
  },
  {
    path: 'auth',
    loadComponent: () => import('./layout/auth-layout/auth-layout').then((m) => m.AuthLayout),
    children: [
      {
        path: 'login',
        loadComponent: () => import('./features/auth/login/login').then((m) => m.LoginPage)
      },
      {
        path: 'register',
        loadComponent: () =>
          import('./features/auth/register/register').then((m) => m.RegisterPage)
      },
      { path: '', pathMatch: 'full', redirectTo: 'login' }
    ]
  },
  {
    path: 'app',
    canActivate: [authGuard],
    loadComponent: () => import('./layout/main-layout/main-layout').then((m) => m.MainLayout),
    children: [
      {
        path: '',
        pathMatch: 'full',
        canActivate: [
          () => {
            const auth = inject(AuthService);
            const router = inject(Router);
            return router.createUrlTree([firstAllowedPath(auth.currentUser())]);
          }
        ],
        loadComponent: () => import('./features/pending/pending').then((m) => m.PendingPage)
      },
      {
        path: 'pending',
        loadComponent: () => import('./features/pending/pending').then((m) => m.PendingPage)
      },
      {
        path: 'dashboard',
        canActivate: [pageGuard('DASHBOARD')],
        loadComponent: () => import('./features/dashboard/dashboard').then((m) => m.DashboardPage)
      },
      {
        path: 'tenants',
        canActivate: [pageGuard('TENANTS')],
        loadComponent: () => import('./features/tenants/tenants').then((m) => m.TenantsPage)
      },
      {
        path: 'workspaces',
        canActivate: [pageGuard('WORKSPACES')],
        loadComponent: () =>
          import('./features/workspaces/workspaces').then((m) => m.WorkspacesPage)
      },
      {
        path: 'access',
        canActivate: [pageGuard('ACCESS')],
        loadComponent: () => import('./features/access/access').then((m) => m.AccessPage)
      },
      {
        path: 'instagram/media',
        canActivate: [pageGuard('IG_MEDIA')],
        loadComponent: () =>
          import('./features/instagram-media/instagram-media').then((m) => m.InstagramMediaPage)
      },
      {
        path: 'instagram/publish',
        redirectTo: 'instagram/media',
        pathMatch: 'full'
      },
      {
        path: 'instagram/insights',
        redirectTo: '/app/scenario',
        pathMatch: 'full'
      },
      {
        path: 'instagram/scenario',
        redirectTo: '/app/scenario',
        pathMatch: 'full'
      },
      {
        path: 'scenario',
        canActivate: [pageGuard('IG_INSIGHTS')],
        loadComponent: () =>
          import('./features/instagram-scenario/instagram-scenario').then(
            (m) => m.InstagramScenarioPage
          )
      },
      {
        path: 'voice',
        canActivate: [pageGuard('INSTAGRAM')],
        loadComponent: () => import('./features/voice/voice').then((m) => m.VoicePage)
      },
      {
        path: 'instagram',
        canActivate: [pageGuard('INSTAGRAM')],
        loadComponent: () =>
          import('./features/instagram/instagram').then((m) => m.InstagramPage)
      }
    ]
  },
  { path: '**', redirectTo: 'app' }
];
