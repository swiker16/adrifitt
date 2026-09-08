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
}
