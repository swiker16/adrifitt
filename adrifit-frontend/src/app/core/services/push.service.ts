import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { SwPush } from '@angular/service-worker';
import { firstValueFrom } from 'rxjs';
import { API_BASE_URL } from '../config/api.config';

export interface PushStatus {
  enabled: boolean;
  subscribed: boolean;
  devices: number;
}

export type PushAvailability =
  /** Ready to ask for permission / already working. */
  | 'available'
  /** iPhone/iPad in Safari: notifications need the app installed on the home screen. */
  | 'ios-needs-install'
  /** Development server (no service worker) or unsupported browser. */
  | 'unsupported'
  /** The user blocked notifications for this site. */
  | 'denied';

/** Web Push for the installed PWA (Angular service worker handles display and taps). */
@Injectable({ providedIn: 'root' })
export class PushService {
  private readonly http = inject(HttpClient);
  private readonly swPush = inject(SwPush);
  private readonly base = `${API_BASE_URL}/push`;

  availability(): PushAvailability {
    const ios = /iPhone|iPad|iPod/.test(navigator.userAgent);
    const standalone = window.matchMedia?.('(display-mode: standalone)').matches || (navigator as any).standalone === true;
    if (ios && !standalone) return 'ios-needs-install';
    if (!this.swPush.isEnabled || !('Notification' in window) || !('PushManager' in window)) return 'unsupported';
    if (Notification.permission === 'denied') return 'denied';
    return 'available';
  }

  permission(): NotificationPermission | 'unsupported' {
    return 'Notification' in window ? Notification.permission : 'unsupported';
  }

  /** Current browser subscription (null if this device is not subscribed). */
  async currentSubscription(): Promise<PushSubscription | null> {
    if (!this.swPush.isEnabled) return null;
    try {
      return await firstValueFrom(this.swPush.subscription);
    } catch {
      return null;
    }
  }

  async status(): Promise<PushStatus> {
    const sub = await this.currentSubscription();
    const q = sub ? `?endpoint=${encodeURIComponent(sub.endpoint)}` : '';
    return firstValueFrom(this.http.get<PushStatus>(`${this.base}/status${q}`));
  }

  /** Asks for permission (system prompt) and registers this device. Must run from a user tap. */
  async enable(): Promise<PushStatus> {
    const { publicKey } = await firstValueFrom(this.http.get<{ publicKey: string }>(`${this.base}/public-key`));
    // Start from a fresh subscription: an old one may have expired on the push service (410)
    // or belong to a previous VAPID key.
    const existing = await this.currentSubscription();
    if (existing) await existing.unsubscribe().catch(() => undefined);
    const sub = await this.swPush.requestSubscription({ serverPublicKey: publicKey });
    const json = sub.toJSON();
    return firstValueFrom(
      this.http.post<PushStatus>(`${this.base}/subscriptions`, {
        endpoint: json.endpoint,
        keys: { p256dh: json.keys?.['p256dh'], auth: json.keys?.['auth'] },
        userAgent: navigator.userAgent,
      })
    );
  }

  /** Re-sends the current browser subscription (e.g. after logging in with another account). */
  async resync(): Promise<void> {
    const sub = await this.currentSubscription();
    if (!sub || Notification.permission !== 'granted') return;
    const json = sub.toJSON();
    await firstValueFrom(
      this.http.post(`${this.base}/subscriptions`, {
        endpoint: json.endpoint,
        keys: { p256dh: json.keys?.['p256dh'], auth: json.keys?.['auth'] },
        userAgent: navigator.userAgent,
      })
    );
  }

  /**
   * Detaches this device from the current account (on logout) but keeps the browser
   * subscription, so the next login can re-attach it silently without asking again.
   */
  async detach(): Promise<void> {
    const sub = await this.currentSubscription();
    if (!sub) return;
    await firstValueFrom(this.http.post(`${this.base}/subscriptions/remove`, { endpoint: sub.endpoint }));
  }

  async disable(): Promise<void> {
    const sub = await this.currentSubscription();
    if (!sub) return;
    await firstValueFrom(this.http.post(`${this.base}/subscriptions/remove`, { endpoint: sub.endpoint }));
    await this.swPush.unsubscribe().catch(() => undefined);
  }

  sendTest(): Promise<{ delivered: number }> {
    return firstValueFrom(this.http.post<{ delivered: number }>(`${this.base}/test`, {}));
  }
}
