import { Component, computed, effect, HostListener, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatSidenav, MatSidenavModule } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatMenuModule } from '@angular/material/menu';
import { MatDividerModule } from '@angular/material/divider';
import { BreakpointObserver } from '@angular/cdk/layout';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { catchError, EMPTY, filter, map } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { ROLE_LABELS } from '../models/user.model';
import { UserRole } from '../enums/user-role.enum';
import { AppRoute } from '../enums/app-route.enum';
import { NavIconEnum } from '../enums/nav-icon.enum';
import { ConfirmService } from '../services/confirm.service';
import { ProfileUpdateRequestService } from '../services/profile-update-request.service';
import { MyProfileService } from '../services/my-profile.service';
import { NotificationService } from '../services/notification.service';
import { ToastService } from '../services/toast.service';
import { NOTIFICATION_TYPE_ICONS } from '../enums/notification-event-type.enum';
import { badgeLabelOf } from '../models/notification.model';
import { NotificationResponse } from '../dtos/notification.dto';
import { relativeTimeOf } from '../utils/relative-time.util';
import { NgTemplateOutlet } from '@angular/common';
import { isNavGroup, NavEntry, NavGroup, NavLink } from '../models/nav-item.model';
import { NavGroupKey } from '../enums/nav-group.enum';


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
    NgTemplateOutlet,
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
  private readonly notifications = inject(NotificationService);
  private readonly toast = inject(ToastService);

  readonly user = this.auth.user;
  readonly navOpen = signal(true);
  readonly homeRoute = '/' + AppRoute.Home;
  readonly myProfileRoute = '/' + AppRoute.MyProfile;
  readonly notificationsRoute = '/' + AppRoute.Notifications;
  readonly unreadCount = this.notifications.unreadCount;
  readonly recentNotifications = this.notifications.recent;
  readonly recentLoading = this.notifications.recentLoading;
  readonly notificationIcons = NOTIFICATION_TYPE_ICONS;
  readonly unreadBadge = computed(() => badgeLabelOf(this.unreadCount()));
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

  private readonly navItems: NavEntry[] = [
    { label: 'Home', icon: NavIconEnum.Home, route: AppRoute.Home },
    {
      key: NavGroupKey.Cvs,
      label: 'CVs',
      icon: NavIconEnum.CvsGroup,
      children: [
        { label: 'Competency Profiles', icon: NavIconEnum.Profiles, route: AppRoute.Profiles },
        { label: 'Create CV', icon: NavIconEnum.AddCv, route: AppRoute.CvsNew },
        { label: 'Deleted CVs', icon: NavIconEnum.Deleted, route: AppRoute.CvsDeleted, roles: [UserRole.Admin, UserRole.HR] },
      ],
    },
    {
      key: NavGroupKey.Requests,
      label: 'Requests',
      icon: NavIconEnum.RequestsGroup,
      children: [
        { label: 'Update Requests', icon: NavIconEnum.UpdateRequests, route: AppRoute.UpdateRequests },
        { label: 'Batch Requests', icon: NavIconEnum.BatchRequest, route: AppRoute.BatchRequests, roles: [UserRole.Admin, UserRole.HR] },
        { label: 'Profile Requests', icon: NavIconEnum.PendingActions, route: AppRoute.ProfileUpdateRequests, roles: [UserRole.Admin, UserRole.HR], showsPendingCount: true },
      ],
    },
    {
      key: NavGroupKey.Approvals,
      label: 'Approvals',
      icon: NavIconEnum.ApprovalsGroup,
      children: [
        { label: 'Approval Queue', icon: NavIconEnum.ApprovalQueue, route: AppRoute.ApprovalQueue, roles: [UserRole.Admin, UserRole.HR, UserRole.TechLead] },
        { label: 'Drafts Under Review', icon: NavIconEnum.Supervision, route: AppRoute.PendingDrafts, roles: [UserRole.Admin, UserRole.HR] },
      ],
    },
    {
      key: NavGroupKey.Organization,
      label: 'Organization',
      icon: NavIconEnum.OrganizationGroup,
      children: [
        { label: 'Users', icon: NavIconEnum.People, route: AppRoute.Users, roles: [UserRole.Admin, UserRole.HR, UserRole.TechLead] },
        { label: 'Departments', icon: NavIconEnum.Departments, route: AppRoute.Departments, roles: [UserRole.Admin, UserRole.HR] },
        { label: 'Teams', icon: NavIconEnum.Teams, route: AppRoute.Teams, roles: [UserRole.Admin, UserRole.HR] },
      ],
    },
  ];

  // Only one group is open at a time, so the sidebar never grows past a single open group
  readonly expandedGroup = signal<NavGroupKey | null>(null);

  private readonly currentUrl = toSignal(
    this.router.events.pipe(
      filter((event): event is NavigationEnd => event instanceof NavigationEnd),
      map((event) => event.urlAfterRedirects),
    ),
    { initialValue: this.router.url },
  );

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

    // Polled while the shell lives; signing out destroys the shell and stops it.
    this.notifications.poll().pipe(takeUntilDestroyed()).subscribe();

    /*
     * Navigating into a group opens it, which also covers a reload or a link from a notification.
     * Pages outside the menu (CV detail, edit) leave the sidebar as the user left it.
     */
    effect(() => {
      const key = this.activeGroupKey();
      if (key) {
        this.expandedGroup.set(key);
      }
    });
  }

  /*
   * Sidebar visibility is convenience only; the backend enforces scope on every query.
   * - A group with no visible child disappears.
   * - A group with one visible child is shown as that link, so nobody clicks to open a single
   */
  readonly visibleNavItems = computed<NavEntry[]>(() => {
    const role = this.user()?.role;
    const isOffered = (link: NavLink) => !link.roles || (!!role && link.roles.includes(role));

    return this.navItems.flatMap((entry): NavEntry[] => {
      if (!isNavGroup(entry)) {
        return isOffered(entry) ? [entry] : [];
      }
      const children = entry.children.filter(isOffered);
      if (children.length <= 1) {
        return children;
      }
      return [{ ...entry, children }];
    });
  });

  // Group holding the current page; null on pages outside the menu
  readonly activeGroupKey = computed(() => {
    const url = this.currentUrl();
    const group = this.visibleNavItems()
      .filter(isNavGroup)
      .find((item) => item.children.some((child) => ShellComponent.matchesRoute(url, child.route)));
    return group?.key ?? null;
  });

  readonly isGroup = isNavGroup;
  
  trackKey(entry: NavEntry): string {
    return isNavGroup(entry) ? entry.key : entry.route;
  }

  toggleGroup(key: NavGroupKey): void {
    this.expandedGroup.update((open) => (open === key ? null : key));
  }
 
  // A closed group still shows what waits inside it
  groupPendingCount(group: NavGroup): number {
    return group.children.some((child) => child.showsPendingCount) ? this.pendingRequestCount() : 0;
  }
 
  // Same rule as routerLinkActive's default: the route itself or any page below it
  private static matchesRoute(url: string, route: AppRoute): boolean {
    const path = url.split(/[?#]/)[0].replace(/^\/+/, '');
    return path === route || path.startsWith(`${route}/`);
  }

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

  onNotificationMenuOpened(): void {
    this.notifications.refreshRecent();
  }

  // Opening an item is also reading it; navigation does not wait for the write.
  openNotification(item: NotificationResponse): void {
    if (!item.read) {
      this.notifications.markAsRead(item.id).subscribe();
    }
    void this.router.navigateByUrl('/' + item.link);
  }

  markAllNotificationsRead(event: Event): void {
    // mat-menu closes on any click inside its panel; keep it open to show the result.
    event.stopPropagation();
    this.notifications.markAllAsRead().subscribe({
      next: () => this.toast.success('All notifications marked as read'),
    });
  }

  notificationTime(item: NotificationResponse): string {
    return relativeTimeOf(item.createdAt);
  }

  // signOut returns an Observable; the session is cleared in its finalize block either way,
  // so the redirect happens on both success and failure
  signOut(): void {
    this.notifications.reset();
    this.auth.signOut().subscribe({
      next: () => this.redirectToLogin(),
      error: () => this.redirectToLogin(),
    });
  }

  private redirectToLogin(): void {
    void this.router.navigate([AppRoute.Login]);
  }
}