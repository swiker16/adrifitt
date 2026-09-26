import { Component } from '@angular/core';
import { DeviceSettings } from '../../../shared/components/device-settings';

@Component({
  selector: 'app-trainer-settings',
  imports: [DeviceSettings],
  template: `
    <div class="topbar">
      <div>
        <div class="page-title">Ajustes</div>
        <div class="subtitle">Acceso con passkey y notificaciones de este dispositivo</div>
      </div>
    </div>
    <div class="content">
      <app-device-settings class="device-settings" />
    </div>
  `,
  styleUrl: './trainer-settings.scss',
})
export class TrainerSettings {}
