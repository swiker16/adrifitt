import { Component, inject, input, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { InstallService } from '../../core/services/install.service';
import { NotifyService } from '../../core/services/notify.service';
import { InstallGuide } from './install-guide';

/**
 * "Descargar la app". One tap installs where the browser allows it (Android, Chrome/Edge);
 * otherwise (iPhone, other browsers) opens the step-by-step guide. Hidden once installed.
 */
@Component({
  selector: 'app-install-button',
  imports: [MatIconModule, InstallGuide],
  template: `
    @if (install.available()) {
      @switch (variant()) {
        @case ('none') {
          <!-- The host page shows its own trigger and calls start(). -->
        }
        @default {
          <button type="button" class="btn" [class.btn-primary]="variant() === 'primary'" [class.btn-outline]="variant() === 'outline'"
                  [class.btn-ghost]="variant() === 'ghost'" [class.btn-sm]="small()" [class.btn-lg]="large()" [class.btn-block]="block()" (click)="start()">
            <mat-icon>{{ install.isMobile ? 'install_mobile' : 'download' }}</mat-icon> {{ label() }}
          </button>
        }
      }
    }

    @if (open()) {
      <div class="modal-backdrop" (click)="open.set(false)">
        <div class="modal" role="dialog" aria-modal="true" aria-labelledby="install-title" (click)="$event.stopPropagation()">
          <div class="modal-head">
            <div class="head-brand">
              <img src="brand/logo-96.png" alt="" width="44" height="44" />
              <div>
                <h2 id="install-title">Descarga AdriFitt</h2>
                <span class="sub">Gratis · sin tienda de apps · ocupa menos de 2 MB</span>
              </div>
            </div>
            <button type="button" class="icon-btn" (click)="open.set(false)" aria-label="Cerrar"><mat-icon>close</mat-icon></button>
          </div>
          <div class="modal-body">
            <app-install-guide (installed)="onInstalled()" />
          </div>
        </div>
      </div>
    }
  `,
  styles: `
    :host { display: contents; }
    .head-brand { display: flex; align-items: center; gap: 12px; }
    .head-brand img { border-radius: 50%; }
    .head-brand h2 { margin: 0; font-size: 1.15rem; }
    .sub { font-size: .8rem; color: var(--text-muted); }
    .modal-body { padding-bottom: 24px; }
  `,
})
export class InstallButton {
  readonly install = inject(InstallService);
  private readonly notify = inject(NotifyService);

  readonly label = input('Descargar la app');
  readonly variant = input<'primary' | 'outline' | 'ghost' | 'none'>('outline');
  readonly small = input(false);
  readonly large = input(false);
  readonly block = input(false);

  readonly open = signal(false);

  async start(): Promise<void> {
    // Phones with native install (Android Chrome, Samsung, Edge): straight to the system dialog.
    // On a computer we show the QR first (the goal is the phone), with "install here too" inside.
    if (this.install.isMobile && this.install.canPrompt() && !this.install.inAppBrowser) {
      const outcome = await this.install.promptInstall();
      if (outcome === 'accepted') this.onInstalled();
      if (outcome !== 'unavailable') return;
    }
    this.open.set(true);
  }

  onInstalled(): void {
    this.open.set(false);
    this.notify.success('¡AdriFitt instalada! Ábrela desde tu pantalla de inicio.');
  }
}
