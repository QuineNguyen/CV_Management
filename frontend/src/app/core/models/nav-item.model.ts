import { UserRole } from "../enums/user-role.enum";

export interface NavItem {
  label: string;
  icon: string;
  route: string;
  // Roles allowed to see the entry. Omitted means everyone signed in.
  roles?: UserRole[];
  // Draws the pending-request count next to the label. Only one entry uses it today.
  showsPendingCount?: boolean;
}