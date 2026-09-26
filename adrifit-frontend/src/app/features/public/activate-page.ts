import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { AuthService } from '../../core/auth/auth.service';
import { LeadService } from '../../core/services/lead.service';
import { ActivationInfo } from '../../shared/models/lead.model';
import { apiErrorMessage } from '../../shared/utils/download';
import { PublicFrame } from './public-frame';

/** Account activation for accepted clients: choose a password and enter the app. */
@Component({
  selector: 'app-activate-page',
  imports: [FormsModule, RouterLink, MatIconModule, PublicFrame],
  template: `
    <app-public-frame>
      @if (loading()) {
        <div class="card"><div class="skeleton" style="height: 260px"></div></div>
      } @else if (invalid()) {
        <div class="card"><div class="state">
          <div class="big-icon muted"><mat-icon>link_off</mat-icon></div>
          <h2>Enlace no disponible</h2>
          <p>{{ invalid() }}</p>
          <a routerLink="/login" class="btn btn-primary">Ir a iniciar sesión</a>
        </div></div>
      } @else if (info(); as i) {
        <div class="page-head">
          <h1>¡Bienvenido/a, <span class="accent">{{ i.firstName }}</span>!</h1>
          <p>Ya formas parte de AdriFitt. Elige tu contraseña para activar tu cuenta.</p>
        </div>
        <form class="card" (ngSubmit)="activate()" novalidate>
          <div class="user-box">
            <mat-icon>person</mat-icon>
            <div><span>Tu usuario</span><strong>{{ i.username }}</strong></div>
          </div>
          <div class="field">
            <label for="ac-pass">Contraseña</label>
            <div class="pw">
              <input id="ac-pass" [type]="show() ? 'text' : 'password'" name="password" [(ngModel)]="password"
                     autocomplete="new-password" minlength="8" maxlength="100" placeholder="Mínimo 8 caracteres" />
              <button type="button" class="toggle" (click)="show.set(!show())" [attr.aria-label]="show() ? 'Ocultar contraseña' : 'Mostrar contraseña'">
                <mat-icon>{{ show() ? 'visibility_off' : 'visibility' }}</mat-icon>
              </button>
            </div>
            <div class="strength" [attr.data-level]="strength()"><span></span><span></span><span></span></div>
          </div>
          <div class="field">
            <label for="ac-repeat">Repite la contraseña</label>
            <input id="ac-repeat" [type]="show() ? 'text' : 'password'" name="repeat" [(ngModel)]="repeat" autocomplete="new-password" />
          </div>
          <!-- Lets password managers save the username with the new password -->
          <input type="text" name="username" [value]="i.username" autocomplete="username" hidden />

          @if (error()) { <div class="alert error"><mat-icon>error</mat-icon><span>{{ error() }}</span></div> }

          <button type="submit" class="btn btn-primary btn-lg btn-block" [disabled]="sending()">
            @if (sending()) { Activando… } @else { Activar mi cuenta y entrar <mat-icon>arrow_forward</mat-icon> }
          </button>
        </form>
      }
    </app-public-frame>
  `,
  styleUrl: './activate-page.scss',
})
export class ActivatePage implements OnInit {
  private readonly leads = inject(LeadService);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly token = this.route.snapshot.paramMap.get('token') ?? '';
  readonly info = signal<ActivationInfo | null>(null);
  readonly loading = signal(true);
  readonly invalid = signal<string | null>(null);
  readonly error = signal<string | null>(null);
  readonly sending = signal(false);
  readonly show = signal(false);
  private readonly pw = signal('');

  get password(): string { return this.pw(); }
  set password(v: string) { this.pw.set(v); }
  repeat = '';

  /** 0-3 bars: length, mix of letters/numbers, symbols or 12+ chars. */
  readonly strength = computed(() => {
    const p = this.pw();
    if (p.length < 8) return 0;
    let s = 1;
    if (/[a-zA-Z]/.test(p) && /\d/.test(p)) s++;
    if (/[^a-zA-Z0-9]/.test(p) || p.length >= 12) s++;
    return s;
  });

  ngOnInit(): void {
    this.leads.activationInfo(this.token).subscribe({
      next: (i) => {
        this.info.set(i);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.invalid.set(apiErrorMessage(err, 'Este enlace no es válido o ya se ha usado. Si ya activaste tu cuenta, inicia sesión.'));
      },
    });
  }

  activate(): void {
    if (this.password.length < 8) {
      this.error.set('La contraseña debe tener al menos 8 caracteres.');
      return;
    }
    if (this.password !== this.repeat) {
      this.error.set('Las contraseñas no coinciden.');
      return;
    }
    this.sending.set(true);
    this.error.set(null);
    this.leads.activate(this.token, this.password).subscribe({
      next: (res) => {
        this.auth.completeLogin(res);
        this.router.navigateByUrl('/client/dashboard', { replaceUrl: true });
      },
      error: (err) => {
        this.sending.set(false);
        this.error.set(apiErrorMessage(err, 'No se pudo activar la cuenta. Inténtalo de nuevo.'));
      },
    });
  }
}
