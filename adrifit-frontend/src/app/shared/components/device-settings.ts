import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { firstValueFrom } from 'rxjs';
import { NotifyService } from '../../core/services/notify.service';
import { isPasskeyCancel, Passkey, PasskeyService } from '../../core/services/passkey.service';
import { PushAvailability, PushService } from '../../core/services/push.service';
import { ThemeToggle } from './theme-toggle';

/** "Acceso y notificaciones": manage passkeys and push notifications on this device. */
@Component({
  selector: 'app-device-settings',
  imports: [MatIconModule, DatePipe, ThemeToggle],
  template: `
    <section class="block">
      <header class="block-head">
        <div class="ico"><mat-icon>contrast</mat-icon></div>
        <div>
          <h3>Apariencia</h3>
          <p>Modo claro, oscuro o automático (como tu dispositivo).</p>
        </div>
      </header>
      <app-theme-toggle />
    </section>

    <section class="block">
      <header class="block-head">
        <div class="ico"><mat-icon>fingerprint</mat-icon></div>
        <div>
          <h3>Passkeys</h3>
          <p>Entra con {{ biometric }} en lugar de tu contraseña.</p>
        </div>
      </header>

      @if (!supported) {
        <p class="muted">Este navegador no admite passkeys.</p>
      } @else {
        @if (passkeys().length === 0) {
          <p class="muted">Aún no tienes ninguna passkey.</p>
        } @else {
          <ul class="list">
            @for (p of passkeys(); track p.id) {
              <li>
                <mat-icon class="kind">{{ p.synced ? 'cloud_done' : 'smartphone' }}</mat-icon>
                <div class="meta">
                  <strong>{{ p.name }}</strong>
                  <span>
                    Creada {{ p.createdAt | date: 'd MMM y' }}
                    · {{ p.lastUsedAt ? 'último uso ' + (p.lastUsedAt | date: 'd MMM, HH:mm') : 'sin usar todavía' }}
                    @if (p.synced) { · sincronizada }
                  </span>
                </div>
                <button type="button" class="btn btn-light btn-sm" (click)="remove(p)" [attr.aria-label]="'Eliminar ' + p.name">
                  <mat-icon>delete</mat-icon>
                </button>
              </li>
            }
          </ul>
        }
        <button type="button" class="btn btn-primary" (click)="addPasskey()" [disabled]="busy()">
          <mat-icon>add</mat-icon> Añadir passkey en este dispositivo
        </button>
      }
    </section>

    <section class="block">
      <header class="block-head">
        <div class="ico"><mat-icon>notifications_active</mat-icon></div>
        <div>
          <h3>Notificaciones</h3>
          <p>Mensajes, recordatorios de revisión y pagos, al instante.</p>
        </div>
      </header>

      @switch (availability()) {
        @case ('unsupported') {
          <p class="muted">Las notificaciones no están disponibles aquí. Usa la app instalada o un navegador compatible (Chrome, Edge, Firefox, Safari 16.4+).</p>
        }
        @case ('ios-needs-install') {
          <p class="muted">En iPhone/iPad primero añade AdriFitt a la pantalla de inicio (<mat-icon class="inline">ios_share</mat-icon> Compartir → «Añadir a pantalla de inicio») y ábrela desde el icono.</p>
        }
        @case ('denied') {
          <p class="muted">Has bloqueado las notificaciones para AdriFitt. Actívalas desde los ajustes del navegador o del sistema.</p>
        }
        @default {
          <div class="toggle-row">
            <div>
              <strong>{{ subscribed() ? 'Activadas en este dispositivo' : 'Desactivadas en este dispositivo' }}</strong>
              @if (devices() > 0) { <span class="muted small">{{ devices() }} dispositivo{{ devices() === 1 ? '' : 's' }} con avisos</span> }
            </div>
            <button type="button" class="switch" role="switch" [attr.aria-checked]="subscribed()" [class.on]="subscribed()"
                    (click)="togglePush()" [disabled]="busy()" aria-label="Notificaciones en este dispositivo">
              <span></span>
            </button>
          </div>
          @if (subscribed()) {
            <button type="button" class="btn btn-outline btn-sm" (click)="test()" [disabled]="busy()">
              <mat-icon>send</mat-icon> Enviar notificación de prueba
            </button>
          }
        }
      }
    </section>
  `,
  styles: `
    :host { display: grid; gap: 16px; }
    .block { min-width: 0; padding: 20px; display: grid; gap: 14px; background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius); box-shadow: var(--shadow-sm); }
    .block-head { display: flex; gap: 14px; align-items: center; }
    .block-head h3 { margin: 0; font-size: 1.05rem; }
    .block-head p { margin: 2px 0 0; color: var(--text-muted); font-size: 0.9rem; }
    .ico { width: 42px; height: 42px; border-radius: 12px; display: grid; place-items: center; background: var(--brand-50); color: var(--brand); flex-shrink: 0; }
    .muted { color: var(--text-muted); margin: 0; font-size: 0.92rem; line-height: 1.5; }
    .small { display: block; font-size: 0.82rem; }
    .inline { font-size: 17px; width: 17px; height: 17px; vertical-align: -3px; }
    .list { list-style: none; margin: 0; padding: 0; display: grid; gap: 8px; }
    .list li { display: flex; gap: 12px; align-items: center; padding: 10px 12px; border: 1px solid var(--border); border-radius: 12px; }
    .kind { color: var(--text-subtle); }
    .meta { flex: 1; min-width: 0; display: grid; }
    .meta strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .meta span { color: var(--text-subtle); font-size: 0.8rem; }
    .block > .btn { justify-self: start; max-width: 100%; white-space: normal; text-align: left; }
    .toggle-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
    .switch { width: 50px; height: 30px; border-radius: 99px; border: 0; background: var(--ink-200); position: relative; cursor: pointer; transition: background .2s; flex-shrink: 0; }
    .switch span { position: absolute; top: 3px; left: 3px; width: 24px; height: 24px; border-radius: 50%; background: #fff; box-shadow: 0 1px 3px rgba(0,0,0,.25); transition: transform .2s; }
    .switch.on { background: var(--success); }
    .switch.on span { transform: translateX(20px); }
    .switch:disabled { opacity: .6; cursor: default; }
  `,
})
export class DeviceSettings implements OnInit {
  private readonly passkeyService = inject(PasskeyService);
  private readonly push = inject(PushService);
  private readonly notify = inject(NotifyService);

  readonly supported = this.passkeyService.isSupported();
  readonly biometric = this.passkeyService.biometricLabel();
  readonly passkeys = signal<Passkey[]>([]);
  readonly availability = signal<PushAvailability>('unsupported');
  readonly subscribed = signal(false);
  readonly devices = signal(0);
  readonly busy = signal(false);

  ngOnInit(): void {
    this.loadPasskeys();
    this.refreshPush();
  }

  private loadPasskeys(): void {
    if (!this.supported) return;
    this.passkeyService.list().subscribe({ next: (l) => this.passkeys.set(l), error: () => undefined });
  }

  private async refreshPush(): Promise<void> {
    this.availability.set(this.push.availability());
    try {
      const status = await this.push.status();
      this.subscribed.set(status.subscribed && this.push.permission() === 'granted');
      this.devices.set(status.devices);
    } catch {
      /* ignore */
    }
  }

  async addPasskey(): Promise<void> {
    this.busy.set(true);
    try {
      await this.passkeyService.register();
      this.notify.success(`Passkey creada. Ya puedes entrar con ${this.biometric}.`);
      this.loadPasskeys();
    } catch (err) {
      if (!isPasskeyCancel(err)) this.notify.error(err, 'No se pudo crear la passkey.');
    } finally {
      this.busy.set(false);
    }
  }

  async remove(p: Passkey): Promise<void> {
    if (!confirm(`¿Eliminar la passkey «${p.name}»? Ya no podrás entrar con ella.`)) return;
    try {
      await firstValueFrom(this.passkeyService.delete(p.id));
      this.passkeys.update((l) => l.filter((x) => x.id !== p.id));
      this.notify.success('Passkey eliminada');
    } catch (err) {
      this.notify.error(err, 'No se pudo eliminar la passkey.');
    }
  }

  async togglePush(): Promise<void> {
    this.busy.set(true);
    try {
      if (this.subscribed()) {
        await this.push.disable();
        this.notify.success('Notificaciones desactivadas en este dispositivo');
      } else {
        await this.push.enable();
        this.notify.success('Notificaciones activadas');
      }
    } catch (err) {
      this.notify.error(err, 'No se pudieron cambiar las notificaciones. Revisa los permisos del navegador.');
    } finally {
      this.busy.set(false);
      await this.refreshPush();
    }
  }

  async test(): Promise<void> {
    this.busy.set(true);
    try {
      const { delivered } = await this.push.sendTest();
      if (delivered > 0) this.notify.success('Notificación de prueba enviada');
      else this.notify.error('No se pudo entregar la notificación. Vuelve a activarlas.');
    } catch (err) {
      this.notify.error(err, 'No se pudo enviar la prueba.');
    } finally {
      this.busy.set(false);
    }
  }
}
