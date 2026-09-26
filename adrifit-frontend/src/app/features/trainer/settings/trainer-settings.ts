import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { AuthService } from '../../../core/auth/auth.service';
import { NotifyService } from '../../../core/services/notify.service';
import { DeviceSettings } from '../../../shared/components/device-settings';
import { apiErrorMessage } from '../../../shared/utils/download';

@Component({
  selector: 'app-trainer-settings',
  imports: [DeviceSettings, FormsModule, MatIconModule],
  template: `
    <div class="topbar">
      <div>
        <div class="page-title">Ajustes</div>
        <div class="subtitle">Tu contraseña, apariencia, acceso con passkey y notificaciones de este dispositivo</div>
      </div>
    </div>
    <div class="content">
      <form class="panel password" (ngSubmit)="changePassword()" novalidate>
        <div class="panel-head"><h2><mat-icon>lock_reset</mat-icon> Cambiar contraseña</h2></div>
        <div class="pw-body">
          <input type="text" name="username" [value]="username" autocomplete="username" hidden />
          <div class="field">
            <label for="pw-current">Contraseña actual</label>
            <input id="pw-current" type="password" name="current" [(ngModel)]="current" autocomplete="current-password" />
          </div>
          <div class="field">
            <label for="pw-new">Nueva contraseña</label>
            <input id="pw-new" type="password" name="next" [(ngModel)]="next" autocomplete="new-password" minlength="8" />
            <span class="hint">Al menos 8 caracteres.</span>
          </div>
          <div class="field">
            <label for="pw-repeat">Repite la nueva contraseña</label>
            <input id="pw-repeat" type="password" name="repeat" [(ngModel)]="repeat" autocomplete="new-password" />
          </div>
          @if (error()) { <div class="alert error"><mat-icon>error</mat-icon><span>{{ error() }}</span></div> }
          <button type="submit" class="btn btn-primary" [disabled]="saving()">
            <mat-icon>lock_reset</mat-icon> {{ saving() ? 'Guardando…' : 'Cambiar contraseña' }}
          </button>
        </div>
      </form>

      <app-device-settings class="device-settings" [shareLink]="true" />
    </div>
  `,
  styleUrl: './trainer-settings.scss',
})
export class TrainerSettings {
  private readonly auth = inject(AuthService);
  private readonly notify = inject(NotifyService);

  readonly username = this.auth.user()?.username ?? '';
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  current = '';
  next = '';
  repeat = '';

  changePassword(): void {
    this.error.set(null);
    if (!this.current || !this.next) {
      this.error.set('Rellena la contraseña actual y la nueva.');
      return;
    }
    if (this.next.length < 8) {
      this.error.set('La nueva contraseña debe tener al menos 8 caracteres.');
      return;
    }
    if (this.next !== this.repeat) {
      this.error.set('Las contraseñas nuevas no coinciden.');
      return;
    }
    this.saving.set(true);
    this.auth.changePassword({ currentPassword: this.current, newPassword: this.next }).subscribe({
      next: () => {
        this.saving.set(false);
        this.current = this.next = this.repeat = '';
        this.notify.success('Contraseña cambiada');
      },
      error: (err) => {
        this.saving.set(false);
        this.error.set(apiErrorMessage(err, 'No se pudo cambiar la contraseña.'));
      },
    });
  }
}
