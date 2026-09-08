import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { API_BASE_URL } from '../config/api.config';
import { AuthResponse, LoginRequest, Role } from '../../shared/models/auth.model';

const TOKEN_KEY = 'adrifit_token';
const USER_KEY = 'adrifit_user';

interface StoredUser {
  userId: number;
  username: string;
  role: Role;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly _user = signal<StoredUser | null>(this.readUser());

  readonly user = this._user.asReadonly();
  readonly isAuthenticated = computed(() => this._user() !== null);
  readonly role = computed<Role | null>(() => this._user()?.role ?? null);

  login(credentials: LoginRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${API_BASE_URL}/auth/login`, credentials).pipe(
      tap((res) => this.storeSession(res))
    );
  }

  logout(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    this._user.set(null);
    this.router.navigateByUrl('/login');
  }

  getToken(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  redirectByRole(): void {
    const role = this.role();
    if (role === 'TRAINER') {
      this.router.navigateByUrl('/trainer/dashboard');
    } else if (role === 'CLIENT') {
      this.router.navigateByUrl('/client/dashboard');
    } else {
      this.router.navigateByUrl('/login');
    }
  }

  private storeSession(res: AuthResponse): void {
    localStorage.setItem(TOKEN_KEY, res.token);
    const user: StoredUser = { userId: res.userId, username: res.username, role: res.role };
    localStorage.setItem(USER_KEY, JSON.stringify(user));
    this._user.set(user);
  }

  private readUser(): StoredUser | null {
    const raw = localStorage.getItem(USER_KEY);
    if (!raw) {
      return null;
    }
    try {
      return JSON.parse(raw) as StoredUser;
    } catch {
      return null;
    }
  }
}
