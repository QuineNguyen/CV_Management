import { UserRole } from '../enums/user-role.enum';

export interface LoginRequest {
  username: string;
  password: string;
}

// Google ID token obtained from Google Identity Services
export interface GoogleLoginRequest {
  idToken: string;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
  confirmPassword: string;
}

// User payload returned with the token; never contains the password hash
export interface AuthenticatedUser {
  id: string;
  username: string;
  fullName: string;
  email: string;
  role: UserRole;
  mustChangePassword: boolean;
}

export interface LoginResponse {
  token: string;
  user: AuthenticatedUser;
}

// Shown once in the admin dialog; also delivered by email
export interface ResetPasswordResponse {
  temporaryPassword: string;
}