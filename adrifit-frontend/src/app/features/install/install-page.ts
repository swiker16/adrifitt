import { Component, OnInit, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { InstallService } from '../../core/services/install.service';
import { NotifyService } from '../../core/services/notify.service';
import { InstallGuide } from '../../shared/components/install-guide';
import { ThemeToggle } from '../../shared/components/theme-toggle';

/**
 * Public download page (/app): the link the trainer shares with clients (WhatsApp, email,
 * Instagram bio). Installs the PWA in one tap where possible, or explains how on iPhone.
 */
@Component({
  selector: 'app-install-page',
  imports: [RouterLink, MatIconModule, InstallGuide, ThemeToggle],
  template: `
    <div class="page">
      <header class="top">
        <a routerLink="/" class="brand">
          <img src="brand/logo-96.png" alt="" width="36" height="36" /> <span>Adri<span class="dot">Fitt</span></span>
        </a>
        <app-theme-toggle variant="icon" />
      </header>

      <main class="wrap">
        <section class="hero">
          <img class="logo" src="brand/logo-192.png" alt="Logo de AdriFitt" width="132" height="132" />
          <h1>Descarga <span class="accent">AdriFitt</span></h1>
          <p class="lead">Tu rutina, tu dieta, tus seguimientos y el chat con tu entrenador, en una app en tu móvil.</p>
          <ul class="perks">
            <li><mat-icon>bolt</mat-icon> Se abre al instante, a pantalla completa</li>
            <li><mat-icon>notifications_active</mat-icon> Avisos de mensajes, revisiones y pagos</li>
            <li><mat-icon>fingerprint</mat-icon> Entra con Face ID o tu huella</li>
            <li><mat-icon>cloud_off</mat-icon> Sin tienda de apps y ocupa menos de 2 MB</li>
          </ul>
        </section>

        <section class="card">
          <h2>{{ install.platform === 'desktop' ? 'Instálala en tu móvil' : 'Instálala en 10 segundos' }}</h2>
          <app-install-guide (installed)="onInstalled()" />
        </section>

        <p class="login-link">¿Ya la tienes instalada? <a routerLink="/login">Entrar</a></p>
      </main>
    </div>
  `,
  styles: `
    :host { display: block; min-height: 100dvh; background:
      radial-gradient(900px 480px at 50% -10%, rgba(255, 107, 26, 0.18), transparent 70%), var(--canvas); }
    .top { display: flex; align-items: center; justify-content: space-between; padding: 16px 20px; max-width: 980px; margin: 0 auto; }
    .brand { display: inline-flex; align-items: center; gap: 10px; font: 800 1.25rem 'Sora', sans-serif; letter-spacing: -.03em; color: var(--text); }
    .brand img { border-radius: 50%; }
    .dot, .accent { color: var(--brand); }
    .wrap { max-width: 560px; margin: 0 auto; padding: 8px 20px 48px; display: grid; gap: 22px; }
    .hero { text-align: center; }
    .logo { border-radius: 50%; box-shadow: 0 20px 60px -20px rgba(255, 107, 26, .55); }
    h1 { font-size: clamp(2rem, 7vw, 2.6rem); font-weight: 800; letter-spacing: -.04em; margin: 18px 0 10px; }
    .lead { color: var(--text-muted); font-size: 1.05rem; line-height: 1.6; margin: 0 auto; max-width: 440px; }
    .perks { list-style: none; padding: 0; margin: 18px auto 0; display: grid; gap: 8px; max-width: 360px; text-align: left; }
    .perks li { display: flex; gap: 10px; align-items: center; font-size: .93rem; }
    .perks mat-icon { color: var(--brand); font-size: 20px; width: 20px; height: 20px; }
    .card {
      background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius-xl);
      box-shadow: var(--shadow-md); padding: 22px; display: grid; gap: 16px;
    }
    .card h2 { font-size: 1.15rem; margin: 0; }
    .login-link { text-align: center; color: var(--text-muted); margin: 0; }
    .login-link a { color: var(--brand-600); font-weight: 600; }
  `,
})
export class InstallPage implements OnInit {
  readonly install = inject(InstallService);
  private readonly router = inject(Router);
  private readonly notify = inject(NotifyService);

  ngOnInit(): void {
    // Opened from the installed app: nothing to install, go to the app.
    if (this.install.installed()) this.router.navigateByUrl('/login', { replaceUrl: true });
  }

  onInstalled(): void {
    this.notify.success('¡AdriFitt instalada! Ábrela desde tu pantalla de inicio.');
  }
}
