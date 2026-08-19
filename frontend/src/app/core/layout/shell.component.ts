import { Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatSidenav, MatSidenavModule } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatMenuModule } from '@angular/material/menu';
import { MatDividerModule } from '@angular/material/divider';
import { BreakpointObserver, Breakpoints } from '@angular/cdk/layout';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { Role } from '../models/user.model';
import { UserRole } from '../enums/user-role.enum';
import { AppRoute } from '../enums/app-route.enum';

interface NavItem {
  label: string;
  icon: string;
  route: string;
  /** Roles allowed to see the entry. Omitted means everyone signed in. */
  roles?: UserRole[];
}

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

  readonly user = this.auth.user;
  readonly navOpen = signal(true);

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
    { label: 'Home', icon: 'home', route: AppRoute.Home },
    { label: 'Users', icon: 'group', route: AppRoute.Users, roles: [UserRole.Admin] },
    { label: 'Departments', icon: 'account_tree', route: AppRoute.Departments, roles: [UserRole.Admin] },
    { label: 'Teams', icon: 'groups', route: AppRoute.Teams, roles: [UserRole.Admin] },
    // Later stages add their entries here. Each one declares the roles it is offered to; the
    // server still enforces access independently.
  ];

  // Sidebar visibility is convenience only; the backend enforces scope on every query.
  readonly visibleNavItems = computed(() => {
    const role = this.auth.user()?.role;
    return this.navItems.filter((item) => !item.roles || (role && item.roles.includes(role)));
  });

  readonly roleLabel = computed(() => {
    const labels: Record<Role, string> = {
      ADMIN: 'Administrator',
      HR: 'HR',
      TECH_LEAD: 'Tech Lead',
      EMPLOYEE: 'Employee',
    };
    const role = this.auth.user()?.role;
    return role ? labels[role] : '';
  });

  toggleNav(drawer: MatSidenav): void {
    if (this.isCompact()) {
      void drawer.toggle();
    } else {
      this.navOpen.update((open) => !open);
    }
  }

  async signOut(): Promise<void> {
    await this.auth.signOut();
    await this.router.navigate([AppRoute.Login]);
  }
}