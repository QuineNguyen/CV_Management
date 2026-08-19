import { Routes } from '@angular/router';

import { authGuard } from './core/services/auth.guard';
import { mustChangePasswordGuard } from './core/services/must-change-password.guard';

export const routes: Routes = [
  {
    path: 'login',
    title: 'Sign in',
    loadComponent: () =>
      import('./core/pages/login/login.component').then((m) => m.LoginComponent),
  },
  {
    // Deliberately outside the shell: no sidebar or toolbar until the password is replaced.
    path: 'account/change-password',
    canActivate: [authGuard],
    title: 'Change Password',
    loadComponent: () =>
      import('./core/pages/change-password/change-password.component')
        .then((m) => m.ChangePasswordComponent),
  },
  {
    path: '',
    canActivate: [authGuard, mustChangePasswordGuard],
    loadComponent: () =>
      import('./core/layout/shell.component').then((m) => m.ShellComponent),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'home' },
      {
        path: 'home',
        title: 'Home',
        loadComponent: () =>
          import('./core/pages/home/home.component').then((m) => m.HomeComponent),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];