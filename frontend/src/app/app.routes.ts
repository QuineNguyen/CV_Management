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
        title: 'CV Profiles',
        loadComponent: () =>
          import('./core/pages/cv-profiles/cv-profiles.component').then(m => m.CVProfilesComponent),
        canActivate: [authGuard],
      },
      {
        path: AppRoute.CvsNew,
        canDeactivate: [unsavedChangesGuard],
        title: 'Create CV',
        loadComponent: () =>
          import('./core/pages/cvs/cv-create/cv-create.component')
            .then(m => m.CvCreateComponent),
      },
      {
        path: AppRoute.CvsDeleted,
        canActivate: [roleGuard(UserRole.Admin, UserRole.HR)],
        title: 'Deleted CVs',
        loadComponent: () =>
          import('./core/pages/cvs/cv-deleted-list/cv-deleted-list.component')
            .then(m => m.CvDeletedListComponent),
      },
      {
        path: `${AppRoute.Cvs}/:id`,
        title: 'CV Detail',
        loadComponent: () =>
          import('./core/pages/cvs/cv-detail/cv-detail.component')
            .then(m => m.CvDetailComponent),
      },
      {
        path: `${AppRoute.Cvs}/:id/edit`,
        canDeactivate: [unsavedChangesGuard],
        title: 'Edit CV',
        loadComponent: () =>
          import('./core/pages/cvs/cv-edit/cv-edit.component')
            .then(m => m.CvEditComponent),
      },
      {
        path: AppRoute.MyProfile,
        title: 'My Profile',
        loadComponent: () =>
          import('./core/pages/my-profile/my-profile.component')
            .then(m => m.MyProfileComponent),
      },
      {
        path: AppRoute.ProfileUpdateRequests,
        // Convenience only: the server scopes every row by the requester's role regardless.
        canActivate: [roleGuard(UserRole.Admin, UserRole.HR)],
        title: 'Profile Update Requests',
        loadComponent: () =>
          import('./core/pages/profile-update-requests/profile-update-requests.component')
            .then(m => m.ProfileUpdateRequestsComponent),
      },
      {
        path: AppRoute.ApprovalQueue,
        canActivate: [authGuard, roleGuard(UserRole.TechLead, UserRole.HR, UserRole.Admin)],
        title: 'Approval Queue',
        loadComponent: () =>
          import('./core/pages/approvals/approval-queue/approval-queue.component')
            .then(m => m.ApprovalQueueComponent),
      },
      {
        path: AppRoute.PendingDrafts,
        canActivate: [roleGuard(UserRole.Admin)],
        title: 'Draft Under Review',
        loadComponent: () =>
          import('./core/pages/approvals/pending-drafts/pending-drafts.component')
            .then(m => m.PendingDraftsComponent),
      },
      {
        path: `approvals/drafts/:draftId/review`,
        canActivate: [authGuard, roleGuard(UserRole.TechLead, UserRole.HR, UserRole.Admin)],
        title: 'Review Draft',
        loadComponent: () =>
          import('./core/pages/approvals/draft-review/draft-review.component')
            .then(m => m.DraftReviewComponent),
      },
      {
        path: AppRoute.Notifications,
        title: 'Notifications',
        loadComponent: () =>
          import('./core/pages/notifications/notifications.component')
            .then(m => m.NotificationsComponent),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];