export type Role = 'TRAINER' | 'CLIENT';

export interface LoginRequest {
  username: string;
  password: string;
}

export interface AuthResponse {
  token: string;
  userId: number;
  username: string;
  role: Role;
  mustChangePassword: boolean;
}

export interface MeResponse {
  userId: number;
  username: string;
  email: string;
  role: Role;
  mustChangePassword: boolean;
  clientId: number | null;
  firstName: string | null;
  lastName: string | null;
  /** Registered passkeys of the user (0 → offer to create one). */
  passkeys: number;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}
