import { Component, inject, input, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { firstValueFrom } from 'rxjs';
import { API_BASE_URL } from '../../core/config/api.config';
import { VideoService } from '../../core/services/video.service';

/**
 * Technique video player. The (signed, short-lived) streaming link is only requested when the
 * user presses play, so a long list of videos costs nothing until one is watched.
 */
@Component({
  selector: 'app-video-player',
  imports: [MatIconModule],
  template: `
    <div class="player" [class.vertical]="vertical()">
      @if (src(); as url) {
        <video [src]="url" controls autoplay playsinline preload="metadata" (error)="onError()" (loadedmetadata)="onMeta($event)"></video>
      } @else {
        <button type="button" class="poster" (click)="play()" [disabled]="loading()" [attr.aria-label]="'Reproducir vídeo: ' + label()">
          <span class="play"><mat-icon>{{ loading() ? 'hourglass_top' : 'play_arrow' }}</mat-icon></span>
          @if (duration(); as d) { <span class="duration">{{ format(d) }}</span> }
        </button>
      }
    </div>
    @if (error()) {
      <p class="error">
        <mat-icon>info</mat-icon>
        <span>{{ error() }} @if (downloadUrl()) { <a [href]="downloadUrl()">Descargar el vídeo</a> }</span>
      </p>
    } @else if (downloadUrl()) {
      <a class="download" [href]="downloadUrl()"><mat-icon>download</mat-icon> Descargar</a>
    }
  `,
  styles: `
    :host { display: block; }
    .player {
      position: relative; width: 100%; aspect-ratio: 16 / 9; border-radius: 14px; overflow: hidden;
      background: radial-gradient(circle at 30% 20%, #2a2f3a, #0b0d12 70%);
    }
    .player.vertical { aspect-ratio: 9 / 16; max-height: 70vh; margin: 0 auto; }
    video { width: 100%; height: 100%; display: block; background: #000; object-fit: contain; }
    .poster { all: unset; position: absolute; inset: 0; display: grid; place-items: center; cursor: pointer; }
    .poster:focus-visible { outline: 3px solid var(--brand); outline-offset: -3px; }
    .play {
      width: 64px; height: 64px; border-radius: 50%; display: grid; place-items: center;
      background: var(--brand-gradient); color: #fff; box-shadow: var(--brand-glow); transition: transform .15s;
    }
    .poster:hover .play { transform: scale(1.06); }
    .play mat-icon { font-size: 36px; width: 36px; height: 36px; }
    .duration {
      position: absolute; right: 10px; bottom: 10px; padding: 3px 8px; border-radius: 8px;
      background: rgba(0, 0, 0, .6); color: #fff; font-size: .78rem; font-weight: 600;
    }
    .error { display: flex; gap: 8px; align-items: flex-start; margin: 8px 0 0; font-size: .85rem; color: var(--text-muted); }
    .error mat-icon { font-size: 18px; width: 18px; height: 18px; color: var(--info); flex-shrink: 0; }
    .error a, .download { color: var(--brand-600); font-weight: 600; }
    .download { display: inline-flex; align-items: center; gap: 4px; margin-top: 8px; font-size: .82rem; text-decoration: none; }
    .download mat-icon { font-size: 17px; width: 17px; height: 17px; }
  `,
})
export class VideoPlayer {
  private readonly videos = inject(VideoService);

  readonly videoId = input.required<number>();
  readonly label = input('');
  readonly duration = input<number | null>(null);

  readonly src = signal<string | null>(null);
  readonly downloadUrl = signal<string | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly vertical = signal(false);

  async play(): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      const link = await firstValueFrom(this.videos.link(this.videoId()));
      this.downloadUrl.set(this.absolute(link.downloadUrl));
      this.src.set(this.absolute(link.url));
    } catch {
      this.error.set('No se pudo cargar el vídeo. Revisa tu conexión.');
    } finally {
      this.loading.set(false);
    }
  }

  onMeta(event: Event): void {
    const v = event.target as HTMLVideoElement;
    this.vertical.set(v.videoHeight > v.videoWidth);
  }

  onError(): void {
    this.error.set('Este navegador no puede reproducir este formato.');
  }

  format(seconds: number): string {
    const s = Math.round(seconds);
    return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
  }

  /** The API returns /api/... paths; respect a different API base if configured. */
  private absolute(path: string): string {
    return path.startsWith('/api') ? API_BASE_URL + path.slice(4) : path;
  }
}
