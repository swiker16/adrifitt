import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { AuthService } from '../../../core/auth/auth.service';
import { apiErrorMessage } from '../../../shared/utils/download';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink, MatIconModule],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly showPassword = signal(false);

  readonly form = this.fb.nonNullable.group({
    username: ['', Validators.required],
    password: ['', Validators.required],
  });

  constructor() {
    // Already logged in: go straight to the private area.
    if (this.auth.isAuthenticated()) {
      this.auth.redirectByRole();
    }
  }

  togglePassword(): void {
    this.showPassword.update((v) => !v);
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    if (this.loading()) return;
    this.loading.set(true);
    this.error.set(null);

    this.auth.login(this.form.getRawValue()).subscribe({
      next: () => {
        this.loading.set(false);
        this.auth.redirectByRole();
      },
      error: (err) => {
        this.loading.set(false);
        if (err?.status === 401) {
          this.error.set(apiErrorMessage(err, 'Usuario o contraseña incorrectos.'));
        } else if (err?.status === 0) {
          this.error.set('No se pudo conectar con el servidor.');
        } else {
          this.error.set(apiErrorMessage(err, 'No se pudo iniciar sesión. Inténtalo de nuevo.'));
        }
      },
    });
  }
}
