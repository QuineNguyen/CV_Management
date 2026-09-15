import { Component, computed, HostListener, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatSidenav, MatSidenavModule } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatMenuModule } from '@angular/material/menu';
import { MatDividerModule } from '@angular/material/divider';
import { BreakpointObserver } from '@angular/cdk/layout';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { catchError, EMPTY, map } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { ROLE_LABELS } from '../models/user.model';
import { UserRole } from '../enums/user-role.enum';
import { AppRoute } from '../enums/app-route.enum';
import { NavItem } from '../models/nav-item.model';
import { NavIconEnum } from '../enums/nav-icon.enum';
import { ConfirmService } from '../services/confirm.service';
import { ProfileUpdateRequestService } from '../services/profile-update-request.service';
import { MyProfileService } from '../services/my-profile.service';


/**
 * Application shell: toolbar, navigation and the outlet every screen renders into.
 *
 * <p>The navigation is filtered by role, and that filtering is presentational only. The server
 * answers 403 for anything outside the caller's scope whether or not a menu entry was hidden, so
 * hiding an entry is about not offering a dead end — never about enforcement. The same principle
 * applies to lists: this shell never trims rows.
 */
@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatToolbarModule,
    MatSidenavModule,
    MatListModule,
    MatIconModule,
    MatButtonModule,
    MatMenuModule,
    MatDividerModule,
  ],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.css',
})
export class ShellComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly breakpoints = inject(BreakpointObserver);
  private readonly confirm = inject(ConfirmService);
  private readonly profileUpdateRequests = inject(ProfileUpdateRequestService);
  private readonly myProfile = inject(MyProfileService);

  readonly user = this.auth.user;
  readonly navOpen = signal(true);
  readonly homeRoute = '/' + AppRoute.Home;
  readonly myProfileRoute = '/' + AppRoute.MyProfile;
  private confirmBackdropMouseDownTarget: EventTarget | null = null;

  /**
   * Drawer switches to overlay mode below 1024px, which covers phones and iPad in both
   * orientations. Breakpoints.Handset alone leaves iPad portrait (768px) with a permanent
   * drawer and roughly 500px of usable content.
   */
  readonly isCompact = toSignal(
    this.breakpoints.observe('(max-width: 1024px)').pipe(map((result) => result.matches)),
    { initialValue: false },
  );

  private readonly navItems: NavItem[] = [
    { label: 'Home', icon: NavIconEnum.Home, route: AppRoute.Home },
    { label: 'Users', icon: NavIconEnum.People, route: AppRoute.Users, roles: [UserRole.Admin, UserRole.HR, UserRole.TechLead] },
    { label: 'Profile Requests', icon: NavIconEnum.PendingActions, route: AppRoute.ProfileUpdateRequests, roles: [UserRole.Admin, UserRole.HR], showsPendingCount: true },
    { label: 'Departments', icon: NavIconEnum.Departments, route: AppRoute.Departments, roles: [UserRole.Admin] },
    { label: 'Teams', icon: NavIconEnum.Teams, route: AppRoute.Teams, roles: [UserRole.Admin] },
    { label: 'Competency Profiles', icon: NavIconEnum.Profiles, route: AppRoute.Profiles },
    { label: 'Create CV', icon: NavIconEnum.AddCv, route: AppRoute.CvsNew },
    { label: 'Deleted CVs', icon: NavIconEnum.Deleted, route: AppRoute.CvsDeleted, roles: [UserRole.Admin, UserRole.HR] },
    // Later stages add their entries here. Each one declares the roles it is offered to; the
    // server still enforces access independently.
  ];

  readonly pendingRequestCount = this.profileUpdateRequests.pendingCount;

  constructor() {
    /*
     * The count is scoped server-side, so an HR sees a number that excluded Admin and HR
     * requests - a badge you tap into an empty list is worse than no badge.
     * 
     * Loaded once on start rather than polled: at this scale a stale count until the next
     * navigation costs nothing and a timer here would outlive every screen.
     */
    if (this.auth.hasRole(UserRole.Admin, UserRole.HR)) {
      this.profileUpdateRequests.refreshPendingCount();
    }

    /*
     * Refreshes the cached session from the server once per app start. The signed avatar URL
     * expires and an approved profile update changes values this session copy was taken before.
     */
    this.myProfile.getMyProfile()
      .pipe(catchError(() => EMPTY), takeUntilDestroyed())
      .subscribe(profile => this.auth.patchUser({
        fullName: profile.fullName,
        avatarImageId: profile.avatarImageId,
        avatarUrl: profile.avatarUrl,
      }));
  }

  // Sidebar visibility is convenience only; the backend enforces scope on every query.
  readonly visibleNavItems = computed(() => {
    const role = this.user()?.role;
    return this.navItems.filter((item) => !item.roles || (role && item.roles.includes(role)));
  });

  readonly roleLabel = computed(() => {
    const role = this.user()?.role;
    return role ? ROLE_LABELS[role] : '';
  });

  /*
   * The shell draws prompts raised from outside any page - route guards have no template of their
   * own. Nothing about navigation depends on this; it lives here because the shell is the one
   * component always on screen.
   */
  readonly pendingConfirm = this.confirm.pending;

  onConfirmBackdropMouseDown(event: MouseEvent): void {
    this.confirmBackdropMouseDownTarget = event.target;
  }

  onConfirmBackdropClick(event: MouseEvent): void {
    if (event.target === event.currentTarget && this.confirmBackdropMouseDownTarget === event.currentTarget) {
      this.answerConfirm(false);
    }
    this.confirmBackdropMouseDownTarget = null;
  }

  answerConfirm(result: boolean): void {
    this.confirm.answer(result);
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.pendingConfirm()) {
      // Escape means "no": the safe answer is always the one that keeps the work.
      this.confirm.answer(false);
    }
  }

  toggleNav(drawer: MatSidenav): void {
    if (this.isCompact()) {
      void drawer.toggle();
    } else {
      this.navOpen.update((open) => !open);
    }
  }

  // signOut returns an Observable; the session is cleared in its finalize block either way,
  // so the redirect happens on both success and failure
  signOut(): void {
    this.auth.signOut().subscribe({
      next: () => this.redirectToLogin(),
      error: () => this.redirectToLogin(),
    });
  }

  private redirectToLogin(): void {
    void this.router.navigate([AppRoute.Login]);
  }
}