import { Component, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { InstallPlatform, InstallService } from '../../core/services/install.service';

/**
 * Step-by-step "install the app" guide for iPhone, Android and computer (auto-selects the
 * current device). On a computer it shows a QR code to open the app link on the phone.
 */
@Component({
  selector: 'app-install-guide',
  imports: [MatIconModule],
  template: `
    @if (install.inAppBrowser) {
      <div class="alert warn in-app">
        <mat-icon>open_in_browser</mat-icon>
        <span>Estás dentro de otra app (Instagram, Facebook…). Toca <strong>⋯</strong> y elige
          <strong>«Abrir en el navegador»</strong> para poder instalar AdriFitt.</span>
      </div>
    }

    <div class="segmented tabs" role="tablist" aria-label="Tu dispositivo">
      @for (t of tabs; track t.id) {
        <button type="button" role="tab" [attr.aria-selected]="tab() === t.id" [class.active]="tab() === t.id" (click)="tab.set(t.id)">
          <mat-icon>{{ t.icon }}</mat-icon> {{ t.label }}
        </button>
      }
    </div>

    @switch (tab()) {
      @case ('ios') {
        <ol class="steps">
          <li>
            <span class="num">1</span>
            <div>Toca el botón <strong>Compartir</strong> <mat-icon class="inline">ios_share</mat-icon>
              {{ install.platform === 'ios' && install.iosBrowser === 'other' ? 'junto a la barra de direcciones (arriba).' : 'en la barra inferior de Safari.' }}</div>
          </li>
          <li>
            <span class="num">2</span>
            <div>Desliza hacia abajo y elige <strong>«Añadir a pantalla de inicio»</strong> <mat-icon class="inline">add_box</mat-icon>.</div>
          </li>
          <li>
            <span class="num">3</span>
            <div>Pulsa <strong>«Añadir»</strong>. AdriFitt aparecerá en tu pantalla de inicio como una app más.</div>
          </li>
        </ol>
        <p class="note"><mat-icon>info</mat-icon> Necesitas iOS 16.4 o posterior para recibir notificaciones. Si no ves la opción, abre el enlace en Safari.</p>
      }
      @case ('android') {
        @if (install.platform === 'android' && install.canPrompt()) {
          <button type="button" class="btn btn-primary btn-lg btn-block" (click)="nativeInstall()">
            <mat-icon>install_mobile</mat-icon> Instalar AdriFitt
          </button>
          <p class="note center">Un toque y la tendrás en tu pantalla de inicio.</p>
        } @else {
          <ol class="steps">
            <li><span class="num">1</span><div>Abre el menú del navegador <strong>⋮</strong> (arriba a la derecha).</div></li>
            <li><span class="num">2</span><div>Elige <strong>«Instalar aplicación»</strong> o <strong>«Añadir a pantalla de inicio»</strong>.</div></li>
            <li><span class="num">3</span><div>Confirma con <strong>«Instalar»</strong>. La encontrarás junto a tus apps.</div></li>
          </ol>
        }
      }
      @case ('desktop') {
        <div class="qr-block">
          @if (qr()) {
            <img [src]="qr()" width="200" height="200" alt="Código QR para abrir AdriFitt en el móvil" />
          } @else {
            <div class="qr-placeholder skeleton"></div>
          }
          <div>
            <strong>Escanéalo con la cámara del móvil</strong>
            <p>Se abrirá AdriFitt y podrás instalarla en tu pantalla de inicio.</p>
            <code>{{ link }}</code>
          </div>
        </div>
        @if (install.platform === 'desktop') {
          @if (install.canPrompt()) {
            <button type="button" class="btn btn-outline btn-block" (click)="nativeInstall()">
              <mat-icon>install_desktop</mat-icon> Instalar también en este ordenador
            </button>
          } @else {
            <p class="note"><mat-icon>install_desktop</mat-icon> En el ordenador: en Chrome o Edge pulsa el icono de instalar de la barra de direcciones.</p>
          }
        }
      }
    }
  `,
  styles: `
    :host { display: grid; gap: 16px; }
    .tabs { display: flex; width: 100%; flex-wrap: nowrap; }
    .tabs button { flex: 1 1 0; min-width: 0; display: inline-flex; align-items: center; justify-content: center; gap: 5px; min-height: 38px; padding: 6px 8px; white-space: nowrap; }
    @media (max-width: 420px) { .tabs button { font-size: .82rem; gap: 3px; } .tabs mat-icon { display: none; } }
    .tabs mat-icon { font-size: 18px; width: 18px; height: 18px; }
    .steps { list-style: none; margin: 0; padding: 0; display: grid; gap: 12px; }
    .steps li { display: flex; gap: 12px; align-items: flex-start; line-height: 1.5; font-size: .95rem; }
    .num {
      flex-shrink: 0; width: 28px; height: 28px; border-radius: 50%; display: grid; place-items: center;
      background: var(--brand-gradient); color: #fff; font-weight: 700; font-size: .85rem;
    }
    .inline { font-size: 19px; width: 19px; height: 19px; vertical-align: -4px; color: var(--info); }
    .note { display: flex; gap: 8px; align-items: flex-start; margin: 0; font-size: .84rem; color: var(--text-muted); line-height: 1.5; }
    .note mat-icon { font-size: 18px; width: 18px; height: 18px; flex-shrink: 0; color: var(--text-subtle); }
    .note.center { justify-content: center; text-align: center; }
    .qr-block { display: flex; gap: 18px; align-items: center; }
    .qr-block img, .qr-placeholder { width: 168px; height: 168px; border-radius: 14px; background: #fff; padding: 8px; flex-shrink: 0; border: 1px solid var(--border); }
    .qr-block p { margin: 4px 0 8px; color: var(--text-muted); font-size: .9rem; }
    .qr-block code { font-size: .8rem; color: var(--brand-fg); word-break: break-all; }
    .in-app { align-items: center; }
    @media (max-width: 520px) { .qr-block { flex-direction: column; text-align: center; } }
  `,
})
export class InstallGuide implements OnInit {
  readonly install = inject(InstallService);
  /** Forces a tab (otherwise the current device's). */
  readonly initial = input<InstallPlatform | null>(null);
  readonly installed = output<void>();

  readonly tabs: { id: InstallPlatform; label: string; icon: string }[] = [
    { id: 'ios', label: 'iPhone', icon: 'phone_iphone' },
    { id: 'android', label: 'Android', icon: 'phone_android' },
    { id: 'desktop', label: 'Ordenador', icon: 'computer' },
  ];
  readonly tab = signal<InstallPlatform>(this.install.platform);
  readonly qr = signal<string | null>(null);
  readonly link = this.install.appLink();
  readonly isDesktopTab = computed(() => this.tab() === 'desktop');

  async ngOnInit(): Promise<void> {
    if (this.initial()) this.tab.set(this.initial()!);
    try {
      // Loaded on demand: the QR library is only needed here.
      const QRCode = (await import('qrcode')).default;
      this.qr.set(await QRCode.toDataURL(this.link, { margin: 1, width: 336, color: { dark: '#0C0F14', light: '#FFFFFF' } }));
    } catch {
      this.qr.set(null);
    }
  }

  async nativeInstall(): Promise<void> {
    if ((await this.install.promptInstall()) === 'accepted') this.installed.emit();
  }
}
