import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { API_BASE_URL } from '../config/api.config';
import {
  AuthResponse,
  ChangePasswordRequest,
  LoginRequest,
  MeResponse,
  Role,
} from '../../shared/models/auth.model';

const TOKEN_KEY = 'adrifit_token';
const USER_KEY = 'adrifit_user';

interface StoredUser {
  userId: number;
  username: string;
  role: Role;
  mustChangePassword?: boolean;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly _user = signal<StoredUser | null>(this.readUser());

  readonly user = this._user.asReadonly();
  readonly isAuthenticated = computed(() => this._user() !== null && this.getToken() !== null);
  readonly role = computed<Role | null>(() => this._user()?.role ?? null);
  /** New clients log in with a temporary password and must change it first. */
  readonly mustChangePassword = computed(() => this._user()?.mustChangePassword === true);

  login(credentials: LoginRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${API_BASE_URL}/auth/login`, credentials).pipe(
      tap((res) => this.storeSession(res))
    );
  }

  me(): Observable<MeResponse> {
    return this.http.get<MeResponse>(`${API_BASE_URL}/auth/me`);
  }

  changePassword(request: ChangePasswordRequest): Observable<void> {
    return this.http.post<void>(`${API_BASE_URL}/auth/change-password`, request).pipe(
      tap(() => {
        const user = this._user();
        if (user) this.saveUser({ ...user, mustChangePassword: false });
      })
    );
  }

  /** Stores the session returned by a passkey sign-in. */
  completeLogin(res: AuthResponse): void {
    this.storeSession(res);
  }

  /**
   * Hook run right before the session is cleared (e.g. detach this device from push
   * notifications while the token is still valid). Registered by PushService consumers.
   */
  beforeLogout: (() => Promise<void>) | null = null;

  /** @param expired the token is no longer valid (401): skip the hook, just clear the session. */
  logout(expired = false): void {
    const hook = this.beforeLogout;
    if (!expired && hook && this.getToken()) {
      // Never let a slow/offline network block the logout.
      const timeout = new Promise<void>((resolve) => setTimeout(resolve, 3000));
      Promise.race([hook(), timeout]).catch(() => undefined).finally(() => this.clearSession());
      return;
    }
    this.clearSession();
  }

  private clearSession(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    this._user.set(null);
    this.router.navigateByUrl('/login');
  }

  getToken(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  homeUrl(): string {
    const role = this.role();
    if (role === 'TRAINER') return '/trainer/dashboard';
    if (role === 'CLIENT') return this.mustChangePassword() ? '/client/profile' : '/client/dashboard';
    return '/login';
  }

  redirectByRole(): void {
    this.router.navigateByUrl(this.homeUrl());
  }

  private storeSession(res: AuthResponse): void {
    localStorage.setItem(TOKEN_KEY, res.token);
    this.saveUser({
      userId: res.userId,
      username: res.username,
      role: res.role,
      mustChangePassword: res.mustChangePassword,
    });
  }

  private saveUser(user: StoredUser): void {
    localStorage.setItem(USER_KEY, JSON.stringify(user));
    this._user.set(user);
  }

  private readUser(): StoredUser | null {
    const raw = localStorage.getItem(USER_KEY);
    if (!raw || !localStorage.getItem(TOKEN_KEY)) {
      return null;
    }
    try {
      return JSON.parse(raw) as StoredUser;
    } catch {
      return null;
    }
  }
}
