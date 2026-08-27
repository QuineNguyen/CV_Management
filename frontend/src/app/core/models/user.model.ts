import { UserRole } from "../enums/user-role.enum";

export interface CurrentUser {
  id: string;
  fullName: string;
  email: string;
  role: UserRole;
  /**
   * True for seeded accounts and after an administrator resets a password. The caller must route
   * to the password-change screen before anything else — see `LoginComponent.submit()`.
   */
  mustChangePassword: boolean;
}
 
/** Human-readable label per role, for menus and account summaries. */
export const ROLE_LABELS: Readonly<Record<UserRole, string>> = {
  ADMIN: 'Administrator',
  HR: 'HR',
  TECH_LEAD: 'Tech Lead',
  EMPLOYEE: 'Employee',
};