import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { firstValueFrom } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { NotifyService } from '../../core/services/notify.service';
import { isPasskeyCancel, PasskeyService } from '../../core/services/passkey.service';
import { PushService } from '../../core/services/push.service';
import { InstallService } from '../../core/services/install.service';

type Step = 'passkey' | 'push' | 'install' | null;

/**
 * Shown once the user is inside the app: offers (1) a passkey so next time they can sign in
 * with Face ID / fingerprint, then (2) push notifications. "Ahora no" snoozes for 7 days,
 * "No volver a preguntar" hides it for good (per user and device).
 */
@Component({
  selector: 'app-device-setup-prompt',
  imports: [MatIconModule],
  template: `
    @if (step(); as s) {
      <div class="modal-backdrop" (click)="later()">
        <div class="modal setup" role="dialog" aria-modal="true" [attr.aria-labelledby]="'setup-title'" (click)="$event.stopPropagation()">
          <div class="modal-body">
            @switch (s) {
              @case ('passkey') {
                <div class="hero-icon"><mat-icon>fingerprint</mat-icon></div>
                <h2 id="setup-title">¿Entrar con {{ biometric }}?</h2>
                <p>Crea una <strong>passkey</strong> y la próxima vez entrarás a AdriFitt en un segundo, sin escribir tu contraseña.</p>
                <ul class="perks">
                  <li><mat-icon>bolt</mat-icon> Más rápido que la contraseña</li>
                  <li><mat-icon>shield</mat-icon> Más seguro: tu huella o tu cara nunca salen del dispositivo</li>
                  <li><mat-icon>sync</mat-icon> Se sincroniza con tus otros dispositivos</li>
                </ul>
              }
              @case ('push') {
                <div class="hero-icon"><mat-icon>notifications_active</mat-icon></div>
                <h2 id="setup-title">Activa las notificaciones</h2>
                <p>Te avisaremos al momento de lo importante:</p>
                <ul class="perks">
                  @if (isTrainer()) {
                    <li><mat-icon>chat_bubble</mat-icon> Mensajes de tus clientes</li>
                    <li><mat-icon>photo_camera</mat-icon> Seguimientos nuevos para revisar</li>
                    <li><mat-icon>payments</mat-icon> Pagos recibidos</li>
                  } @else {
                    <li><mat-icon>chat_bubble</mat-icon> Cuando tu entrenador te escribe</li>
                    <li><mat-icon>event_available</mat-icon> Recordatorios de revisión</li>
                    <li><mat-icon>credit_card</mat-icon> Pagos pendientes y feedback de tus seguimientos</li>
                  }
                </ul>
              }
              @case ('install') {
                <div class="hero-icon"><mat-icon>install_mobile</mat-icon></div>
                <h2 id="setup-title">Descarga la app en tu móvil</h2>
                @if (installer.canPrompt()) {
                  <p>Instala AdriFitt en tu pantalla de inicio: se abre al instante, a pantalla completa, y te avisa de mensajes y revisiones.</p>
                } @else {
                  <p>Añádela a tu pantalla de inicio{{ installer.platform === 'ios' ? ' (en iPhone es necesario para recibir notificaciones)' : '' }}:</p>
                  <ol class="steps">
                    @if (installer.platform === 'ios') {
                      <li>Pulsa <mat-icon>ios_share</mat-icon> <strong>Compartir</strong> {{ installer.iosBrowser === 'other' ? 'junto a la barra de direcciones' : 'en la barra de Safari' }}.</li>
                      <li>Elige <strong>«Añadir a pantalla de inicio»</strong> y pulsa <strong>«Añadir»</strong>.</li>
                    } @else {
                      <li>Abre el menú <strong>⋮</strong> del navegador.</li>
                      <li>Elige <strong>«Instalar aplicación»</strong> o <strong>«Añadir a pantalla de inicio»</strong>.</li>
                    }
                    <li>Abre AdriFitt desde el icono de tu pantalla de inicio.</li>
                  </ol>
                }
              }
            }
          </div>
          <div class="modal-foot">
            <button type="button" class="btn btn-outline" (click)="later()" [disabled]="busy()">Ahora no</button>
            @if (s === 'install' && installer.canPrompt()) {
              <button type="button" class="btn btn-primary" (click)="accept()" [disabled]="busy()">
                <mat-icon>install_mobile</mat-icon> {{ busy() ? 'Un momento…' : 'Instalar' }}
              </button>
            } @else if (s !== 'install') {
              <button type="button" class="btn btn-primary" (click)="accept()" [disabled]="busy()">
                <mat-icon>{{ s === 'passkey' ? 'fingerprint' : 'notifications' }}</mat-icon>
                {{ busy() ? 'Un momento…' : s === 'passkey' ? 'Crear passkey' : 'Activar' }}
              </button>
            } @else {
              <button type="button" class="btn btn-primary" (click)="later()">Entendido</button>
            }
            <button type="button" class="never" (click)="never()">No volver a preguntar</button>
          </div>
        </div>
      </div>
    }
  `,
  styles: `
    .setup { max-width: 440px; }
    .modal-body { text-align: center; padding-top: 28px; }
    .hero-icon {
      width: 72px; height: 72px; margin: 0 auto 16px; border-radius: 22px; display: grid; place-items: center;
      background: var(--brand-gradient); color: #fff; box-shadow: var(--brand-glow);
    }
    .hero-icon mat-icon { font-size: 38px; width: 38px; height: 38px; }
    h2 { font-size: 1.3rem; margin-bottom: 8px; }
    p { color: var(--text-muted); margin: 0 0 14px; line-height: 1.5; }
    .perks, .steps { text-align: left; margin: 0 auto; padding: 0; max-width: 340px; display: grid; gap: 10px; }
    .perks { list-style: none; }
    .perks li { display: flex; gap: 10px; align-items: flex-start; font-size: 0.92rem; }
    .perks mat-icon { color: var(--brand); font-size: 20px; width: 20px; height: 20px; flex-shrink: 0; }
    .steps { padding-left: 20px; font-size: 0.92rem; }
    .steps mat-icon { font-size: 18px; width: 18px; height: 18px; vertical-align: -4px; color: var(--info); }
    .modal-foot { display: grid; grid-template-columns: 1fr 1.4fr; gap: 10px; }
    .modal-foot .btn { width: 100%; justify-content: center; }
    .never {
      grid-column: 1 / -1; justify-self: center; border: 0; background: none; padding: 6px 10px;
      color: var(--text-subtle); font: inherit; font-size: 0.85rem; cursor: pointer; text-decoration: underline; text-underline-offset: 3px;
    }
    .never:hover { color: var(--text-muted); }
  `,
})
export class DeviceSetupPrompt implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly passkeys = inject(PasskeyService);
  private readonly push = inject(PushService);
  readonly installer = inject(InstallService);
  private readonly notify = inject(NotifyService);
  private readonly destroyRef = inject(DestroyRef);

  readonly step = signal<Step>(null);
  readonly busy = signal(false);
  readonly isTrainer = computed(() => this.auth.role() === 'TRAINER');
  readonly biometric = this.passkeys.biometricLabel();

  private timer?: ReturnType<typeof setTimeout>;

  ngOnInit(): void {
    // Logging out detaches this device from the account (no notifications for the old user).
    this.auth.beforeLogout = () => this.push.detach();
    this.destroyRef.onDestroy(() => {
      clearTimeout(this.timer);
      this.auth.beforeLogout = null;
    });
    // Re-attach silently if notifications were already allowed on this device.
    this.push.resync().catch(() => undefined);
    // Let the page settle before asking anything.
    this.timer = setTimeout(() => this.next(), 2500);
  }

  private async next(): Promise<void> {
    if (this.auth.mustChangePassword() || !this.auth.isAuthenticated()) {
      this.timer = setTimeout(() => this.next(), 5000);
      return;
    }
    const candidate = await this.pickStep();
    this.step.set(candidate);
  }

  private async pickStep(): Promise<Step> {
    if (!this.snoozed('passkey') && (await this.passkeys.hasPlatformAuthenticator())) {
      try {
        const me = await firstValueFrom(this.auth.me());
        if (me.passkeys === 0) return 'passkey';
      } catch {
        /* offline: ask later */
      }
    }
    // Phones without the installed app: offer it (on iPhone it is also required for notifications).
    if (this.installer.isMobile && this.installer.available() && !this.installer.inAppBrowser) {
      if (!this.snoozed('install')) return 'install';
      if (this.installer.platform === 'ios') return null;
    }
    const availability = this.push.availability();
    if (availability === 'available' && this.push.permission() === 'default' && !this.snoozed('push')) {
      return 'push';
    }
    return null;
  }

  async accept(): Promise<void> {
    const s = this.step();
    this.busy.set(true);
    try {
      if (s === 'passkey') {
        await this.passkeys.register();
        this.notify.success(`¡Listo! La próxima vez entra con ${this.biometric}.`);
      } else if (s === 'install') {
        const outcome = await this.installer.promptInstall();
        if (outcome !== 'accepted') return;
        this.notify.success('¡AdriFitt instalada! Ábrela desde tu pantalla de inicio.');
      } else if (s === 'push') {
        await this.push.enable();
        await this.push.sendTest().catch(() => undefined);
        this.notify.success('Notificaciones activadas en este dispositivo');
      }
      this.snooze(s!, 3650);
      this.step.set(null);
      if (s === 'passkey' || s === 'install') {
        this.timer = setTimeout(() => this.next(), 600);
      }
    } catch (err) {
      if (!isPasskeyCancel(err)) {
        this.notify.error(err, s === 'push'
          ? 'No se pudieron activar las notificaciones. Revisa los permisos del navegador.'
          : 'No se pudo crear la passkey.');
      }
    } finally {
      this.busy.set(false);
    }
  }

  later(): void {
    const s = this.step();
    if (!s || this.busy()) return;
    this.snooze(s, 7);
    this.step.set(null);
    if (s === 'passkey' || s === 'install') this.timer = setTimeout(() => this.next(), 400);
  }

  never(): void {
    const s = this.step();
    if (!s) return;
    this.snooze(s, 3650);
    this.step.set(null);
    if (s === 'passkey' || s === 'install') this.timer = setTimeout(() => this.next(), 400);
  }

  private key(step: string): string {
    return `adrifit_prompt_${step}_${this.auth.user()?.userId ?? 'anon'}`;
  }

  private snoozed(step: string): boolean {
    try {
      const until = Number(localStorage.getItem(this.key(step)) ?? 0);
      return until > Date.now();
    } catch {
      return false;
    }
  }

  private snooze(step: string, days: number): void {
    try {
      localStorage.setItem(this.key(step), String(Date.now() + days * 86_400_000));
    } catch {
      /* storage unavailable */
    }
  }
}
