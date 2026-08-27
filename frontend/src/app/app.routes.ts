import { Routes } from '@angular/router';

import { authGuard, roleGuard } from './core/services/auth.guard';
import { mustChangePasswordGuard } from './core/services/must-change-password.guard';
import { AppRoute } from './core/enums/app-route.enum';
import { UserRole } from './core/enums/user-role.enum';

export const routes: Routes = [
  {
    path: AppRoute.Login,
    title: 'Sign in',
    loadComponent: () =>
      import('./core/pages/login/login.component').then((m) => m.LoginComponent),
  },
  {
    // Deliberately outside the shell: no sidebar or toolbar until the password is replaced.
    path: AppRoute.ChangePassword,
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
      {
        path: AppRoute.Departments,
        canActivate: [roleGuard(UserRole.Admin)],
        title: 'Departments',
        loadComponent: () =>
          import('./core/pages/departments/departments.component')
            .then((m) => m.DepartmentsComponent),
      },
      {
        path: AppRoute.Teams,
        canActivate: [roleGuard(UserRole.Admin)],
        title: 'Teams',
        loadComponent: () =>
          import('./core/pages/teams/teams.component')
            .then((m) => m.TeamsComponent),
      },
      {
        path: AppRoute.Users,
        canActivate: [roleGuard(UserRole.Admin, UserRole.HR, UserRole.TechLead)],
        title: 'Users',
        loadComponent: () =>
          import('./core/pages/users/users.component')
            .then((m) => m.UsersComponent),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];