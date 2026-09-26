import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { AuthService } from '../../../core/auth/auth.service';
import { apiErrorMessage } from '../../../shared/utils/download';
import { TestimonialService } from '../../../core/services/testimonial.service';
import { PublicTestimonial } from '../../../shared/models/testimonial.model';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink, MatIconModule],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly testimonialService = inject(TestimonialService);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly showPassword = signal(false);
  /** Best public review, shown on the brand panel (desktop only). */
  readonly quote = signal<PublicTestimonial | null>(null);

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

  ngOnInit(): void {
    this.testimonialService.findPublic().subscribe({
      next: (list) => {
        const best = [...list]
          .filter((t) => t.content.length <= 220)
          .sort((a, b) => b.rating - a.rating)[0];
        this.quote.set(best ?? null);
      },
      error: () => this.quote.set(null),
    });
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
