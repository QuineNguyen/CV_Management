import { UserRole } from "../enums/user-role.enum";

export interface NavItem {
  label: string;
  icon: string;
  route: string;
  /** Roles allowed to see the entry. Omitted means everyone signed in. */
  roles?: UserRole[];
}