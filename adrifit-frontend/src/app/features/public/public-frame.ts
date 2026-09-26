import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ThemeToggle } from '../../shared/components/theme-toggle';

/** Top bar + centred column for the public pages (no login). */
@Component({
  selector: 'app-public-frame',
  imports: [RouterLink, ThemeToggle],
  template: `
    <header class="top">
      <a routerLink="/" class="brand" aria-label="AdriFitt, inicio">
        <img src="brand/logo-96.png" alt="" width="36" height="36" />
        <span>Adri<span class="dot">Fitt</span></span>
      </a>
      <app-theme-toggle variant="icon" />
    </header>
    <main class="wrap"><ng-content /></main>
  `,
  styles: `
    :host { display: block; min-height: 100dvh; background:
      radial-gradient(900px 460px at 50% -12%, rgba(255, 107, 26, 0.16), transparent 70%), var(--canvas); }
    .top { display: flex; align-items: center; justify-content: space-between; padding: 16px 20px; max-width: 1000px; margin: 0 auto; }
    .brand { display: inline-flex; align-items: center; gap: 10px; font: 800 1.25rem 'Sora', sans-serif; letter-spacing: -.03em; color: var(--text); }
    .brand img { border-radius: 50%; }
    .dot { color: var(--brand); }
    .wrap { max-width: 640px; margin: 0 auto; padding: 8px 16px 56px; display: grid; gap: 20px; }
  `,
})
export class PublicFrame {}
