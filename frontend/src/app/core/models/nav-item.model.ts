import { AppRoute } from "../enums/app-route.enum";
import { NavGroupKey } from "../enums/nav-group.enum";
import { NavIconEnum } from "../enums/nav-icon.enum";
import { UserRole } from "../enums/user-role.enum";

// A page in the sidebar, at the top level or inside a group
export interface NavLink {
  label: string;
  // Also shown when its group is flattened because it is the only visible child
  icon: NavIconEnum;
  route: AppRoute;
  roles?: UserRole[];
  showsPendingCount?: boolean;
  // Active only on the exact route, not on routes nested under it
  exact?: boolean;
}

// A heading that opens and closes; it is never a page of its own
export interface NavGroup {
  key: NavGroupKey;
  label: string;
  icon: NavIconEnum;
  // No roles here: a group is visible when at least one child is
  children: NavLink[];
}

export type NavEntry = NavLink | NavGroup;

export function isNavGroup(entry: NavEntry): entry is NavGroup {
  return 'children' in entry;
}