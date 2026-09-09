import { Routes } from '@angular/router';

import { authGuard, roleGuard } from './core/services/auth.guard';
import { mustChangePasswordGuard } from './core/services/must-change-password.guard';
import { AppRoute } from './core/enums/app-route.enum';
import { UserRole } from './core/enums/user-role.enum';
import { unsavedChangesGuard } from './core/services/unsaved-changes.guard';

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
      {
        path: AppRoute.Profiles,
        loadComponent: () =>
          import('./core/pages/cv-profiles/cv-profiles.component').then(m => m.CVProfilesComponent),
        canActivate: [authGuard],
      },
      {
        path: AppRoute.CvsNew,
        canDeactivate: [unsavedChangesGuard],
        loadComponent: () =>
          import('./core/pages/cvs/cv-create/cv-create.component')
            .then(m => m.CvCreateComponent),
      },
      {
        path: AppRoute.CvsDeleted,
        canActivate: [roleGuard(UserRole.Admin, UserRole.HR)],
        loadComponent: () =>
          import('./core/pages/cvs/cv-deleted-list/cv-deleted-list.component')
            .then(m => m.CvDeletedListComponent),
      },
      {
        path: `${AppRoute.Cvs}/:id`,
        loadComponent: () =>
          import('./core/pages/cvs/cv-detail/cv-detail.component')
            .then(m => m.CvDetailComponent),
      },
      {
        path: `${AppRoute.Cvs}/:id/edit`,
        canDeactivate: [unsavedChangesGuard],
        loadComponent: () =>
          import('./core/pages/cvs/cv-edit/cv-edit.component')
            .then(m => m.CvEditComponent),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];