import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { InstallButton } from '../../../shared/components/install-button';
import { ThemeToggle } from '../../../shared/components/theme-toggle';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { AuthService } from '../../../core/auth/auth.service';
import { isPasskeyCancel, PasskeyService } from '../../../core/services/passkey.service';
import { apiErrorMessage } from '../../../shared/utils/download';
import { TestimonialService } from '../../../core/services/testimonial.service';
import { PublicTestimonial } from '../../../shared/models/testimonial.model';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink, MatIconModule, ThemeToggle, InstallButton],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login implements OnInit, OnDestroy {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly testimonialService = inject(TestimonialService);
  private readonly passkeys = inject(PasskeyService);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly showPassword = signal(false);
  /** Passkey button: only where the browser supports WebAuthn. */
  readonly passkeySupported = this.passkeys.isSupported();
  readonly biometric = this.passkeys.biometricLabel();
  readonly passkeyLoading = signal(false);
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
    this.startAutofill();
  }

  ngOnDestroy(): void {
    this.passkeys.abortConditional();
  }

  /** Passkey autofill: the saved passkeys appear as suggestions in the username field. */
  private async startAutofill(): Promise<void> {
    if (this.auth.isAuthenticated() || !(await this.passkeys.conditionalMediationAvailable())) return;
    try {
      const res = await this.passkeys.signIn(true);
      this.finishPasskey(res);
    } catch (err) {
      if (!isPasskeyCancel(err)) this.error.set(this.passkeyError(err));
    }
  }

  /** "Entrar con Face ID / huella": opens the system passkey sheet. */
  async signInWithPasskey(): Promise<void> {
    if (this.passkeyLoading()) return;
    this.passkeyLoading.set(true);
    this.error.set(null);
    try {
      const res = await this.passkeys.signIn(false);
      this.finishPasskey(res);
    } catch (err) {
      if (!isPasskeyCancel(err)) this.error.set(this.passkeyError(err));
      // The explicit request aborted the autofill one: restart it.
      this.startAutofill();
    } finally {
      this.passkeyLoading.set(false);
    }
  }

  private finishPasskey(res: Parameters<AuthService['completeLogin']>[0]): void {
    this.auth.completeLogin(res);
    this.auth.redirectByRole();
  }

  private passkeyError(err: unknown): string {
    const status = (err as { status?: number })?.status;
    if (status === 401) return 'Esta passkey no es válida o se ha eliminado. Entra con tu contraseña.';
    if (status === 0) return 'No se pudo conectar con el servidor.';
    return apiErrorMessage(err, 'No se pudo entrar con la passkey. Inténtalo con tu contraseña.');
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
