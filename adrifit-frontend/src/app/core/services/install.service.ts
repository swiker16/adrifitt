import { DOCUMENT } from '@angular/common';
import { Injectable, computed, inject, signal } from '@angular/core';

/** Chrome/Edge event that lets the page show the native "Install app" dialog. */
interface BeforeInstallPromptEvent extends Event {
  prompt(): Promise<void>;
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>;
}

export type InstallPlatform = 'ios' | 'android' | 'desktop';

/**
 * "Descargar la app": installs the PWA on the home screen.
 * - Android / desktop Chrome, Edge, Samsung: native one-tap install (beforeinstallprompt).
 * - iPhone / iPad: Apple has no install API, so we show the "Compartir → Añadir a pantalla de inicio" steps.
 * - Already running as an installed app: everything install-related hides itself.
 */
@Injectable({ providedIn: 'root' })
export class InstallService {
  private readonly document = inject(DOCUMENT);
  private readonly win = this.document.defaultView as (Window & { __installPrompt?: BeforeInstallPromptEvent }) | null;
  private deferred: BeforeInstallPromptEvent | null = this.win?.__installPrompt ?? null;

  private readonly ua = this.win?.navigator.userAgent ?? '';
  readonly platform: InstallPlatform = this.detectPlatform();
  /** Safari on iOS, or another iOS browser (Chrome/Firefox/Edge) — the Share button is in a different place. */
  readonly iosBrowser: 'safari' | 'other' = /CriOS|FxiOS|EdgiOS|OPiOS/.test(this.ua) ? 'other' : 'safari';
  /** Instagram/Facebook/TikTok built-in browsers cannot install apps: the user must open the link in the real browser. */
  readonly inAppBrowser = /Instagram|FBAN|FBAV|FB_IAB|TikTok|musical_ly|Line\//i.test(this.ua);
  readonly isMobile = this.platform !== 'desktop';

  readonly installed = signal(this.detectStandalone());
  // iOS has no install API (any event there would come from an emulator): always use the steps.
  readonly canPrompt = signal(!!this.deferred && this.platform !== 'ios');
  /** Show "Descargar la app" (not installed yet). */
  readonly available = computed(() => !this.installed());

  constructor() {
    this.win?.addEventListener('beforeinstallprompt', (e) => {
      e.preventDefault();
      this.deferred = e as BeforeInstallPromptEvent;
      this.canPrompt.set(this.platform !== 'ios');
    });
    this.win?.addEventListener('appinstalled', () => {
      this.deferred = null;
      this.canPrompt.set(false);
      this.installed.set(true);
    });
    this.win?.matchMedia?.('(display-mode: standalone)').addEventListener?.('change', (e) => {
      if (e.matches) this.installed.set(true);
    });
  }

  /** Opens the native install dialog when the browser supports it. */
  async promptInstall(): Promise<'accepted' | 'dismissed' | 'unavailable'> {
    const event = this.deferred;
    if (!event) return 'unavailable';
    await event.prompt();
    const { outcome } = await event.userChoice;
    // The event can only be used once; the browser fires a new one if it is still installable.
    this.deferred = null;
    this.canPrompt.set(false);
    if (outcome === 'accepted') this.installed.set(true);
    return outcome;
  }

  /** Public page with the install button, instructions and a QR code: shareable with clients. */
  appLink(): string {
    return `${this.win?.location.origin ?? ''}/app`;
  }

  private detectPlatform(): InstallPlatform {
    const nav = this.win?.navigator as (Navigator & { maxTouchPoints?: number }) | undefined;
    // iPadOS reports itself as a Mac: detect it by touch support.
    if (/iPhone|iPad|iPod/.test(this.ua) || (/Macintosh/.test(this.ua) && (nav?.maxTouchPoints ?? 0) > 1)) return 'ios';
    if (/Android/.test(this.ua)) return 'android';
    return 'desktop';
  }

  private detectStandalone(): boolean {
    const nav = this.win?.navigator as (Navigator & { standalone?: boolean }) | undefined;
    return !!(this.win?.matchMedia?.('(display-mode: standalone)').matches
      || this.win?.matchMedia?.('(display-mode: minimal-ui)').matches
      || nav?.standalone);
  }
}
