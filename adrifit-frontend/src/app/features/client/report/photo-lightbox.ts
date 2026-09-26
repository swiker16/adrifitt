import { Component, HostListener, computed, input, model, output } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { SecureImg } from '../../../shared/components/secure-img';
import { ProgressPhoto } from '../../../shared/models/photo.model';

/**
 * Full-screen viewer for the photos of a check-in (client and trainer side).
 * Navigate with the arrows, the keyboard (← → Esc) or by swiping on phones.
 */
@Component({
  selector: 'app-photo-lightbox',
  imports: [MatIconModule, DatePipe, SecureImg],
  template: `
    @if (current(); as p) {
      <div class="lb" role="dialog" aria-modal="true" [attr.aria-label]="title() || 'Fotos'" (click)="close()">
        <div class="lb-top" (click)="$event.stopPropagation()">
          <div class="lb-title">
            @if (title()) { <strong>{{ title() }}</strong> }
            <span>{{ p.takenOn | date: 'dd/MM/yyyy' }} · {{ (index() ?? 0) + 1 }} / {{ photos().length }}</span>
          </div>
          <button type="button" class="lb-btn" aria-label="Cerrar" (click)="close()"><mat-icon>close</mat-icon></button>
        </div>

        <div class="lb-stage" (touchstart)="onTouchStart($event)" (touchend)="onTouchEnd($event)">
          @if (photos().length > 1) {
            <button type="button" class="lb-btn lb-nav prev" aria-label="Foto anterior" (click)="prev($event)"><mat-icon>chevron_left</mat-icon></button>
          }
          <div class="lb-img" (click)="$event.stopPropagation()">
            <app-secure-img [src]="p.contentUrl" [alt]="'Foto ' + ((index() ?? 0) + 1)" imgClass="lb-photo" />
          </div>
          @if (photos().length > 1) {
            <button type="button" class="lb-btn lb-nav next" aria-label="Foto siguiente" (click)="next($event)"><mat-icon>chevron_right</mat-icon></button>
          }
        </div>

        @if (photos().length > 1) {
          <div class="lb-dots" (click)="$event.stopPropagation()">
            @for (ph of photos(); track ph.id; let i = $index) {
              <button type="button" [class.on]="i === index()" [attr.aria-label]="'Ver foto ' + (i + 1)" (click)="index.set(i)"></button>
            }
          </div>
        }
      </div>
    }
  `,
  styles: `
    .lb {
      position: fixed; inset: 0; z-index: 1200; display: flex; flex-direction: column;
      background: rgba(7, 9, 13, 0.94); color: #fff; animation: lbFade 0.2s ease;
      padding: env(safe-area-inset-top, 0) 0 env(safe-area-inset-bottom, 0);
    }
    .lb-top { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 14px 16px; }
    .lb-title { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
    .lb-title strong { font-size: 0.95rem; }
    .lb-title span { font-size: 0.8rem; color: var(--on-dark-3); }
    .lb-stage { position: relative; flex: 1; min-height: 0; display: flex; align-items: center; justify-content: center; padding: 0 64px; }
    .lb-img { height: 100%; width: 100%; max-width: 900px; display: flex; align-items: center; justify-content: center; }
    .lb-img app-secure-img { width: 100%; height: 100%; }
    :host ::ng-deep .lb-photo { width: 100%; height: 100%; object-fit: contain; background: transparent; }
    .lb-btn {
      width: 44px; height: 44px; border-radius: 14px; border: none; cursor: pointer; flex-shrink: 0;
      display: grid; place-items: center; color: #fff; background: rgba(255, 255, 255, 0.1);
      transition: background 0.2s var(--ease);
    }
    .lb-btn:hover { background: rgba(255, 255, 255, 0.2); }
    .lb-btn:focus-visible { outline: 2px solid var(--brand-400); outline-offset: 2px; }
    .lb-nav { position: absolute; top: 50%; transform: translateY(-50%); }
    .lb-nav.prev { left: 12px; }
    .lb-nav.next { right: 12px; }
    .lb-dots { display: flex; justify-content: center; gap: 8px; padding: 16px; }
    .lb-dots button {
      width: 8px; height: 8px; border-radius: 99px; border: none; padding: 0; cursor: pointer;
      background: rgba(255, 255, 255, 0.35); transition: width 0.2s var(--ease), background 0.2s;
    }
    .lb-dots button.on { width: 22px; background: var(--brand); }
    @keyframes lbFade { from { opacity: 0; } to { opacity: 1; } }
    @media (max-width: 640px) {
      .lb-stage { padding: 0; }
      .lb-nav { background: rgba(0, 0, 0, 0.35); }
      .lb-nav.prev { left: 8px; }
      .lb-nav.next { right: 8px; }
    }
  `,
})
export class PhotoLightbox {
  readonly photos = input.required<ProgressPhoto[]>();
  readonly title = input('');
  /** Index of the photo shown; null = closed. */
  readonly index = model<number | null>(null);
  readonly closed = output<void>();

  readonly current = computed(() => {
    const i = this.index();
    const list = this.photos();
    return i == null || list.length === 0 ? null : list[Math.min(Math.max(i, 0), list.length - 1)];
  });

  private touchX: number | null = null;

  close(): void {
    this.index.set(null);
    this.closed.emit();
  }

  prev(event?: Event): void {
    event?.stopPropagation();
    const n = this.photos().length;
    const i = this.index();
    if (i == null || n === 0) return;
    this.index.set((i - 1 + n) % n);
  }

  next(event?: Event): void {
    event?.stopPropagation();
    const n = this.photos().length;
    const i = this.index();
    if (i == null || n === 0) return;
    this.index.set((i + 1) % n);
  }

  @HostListener('document:keydown', ['$event'])
  onKey(e: KeyboardEvent): void {
    if (this.index() == null) return;
    if (e.key === 'Escape') this.close();
    else if (e.key === 'ArrowLeft') this.prev();
    else if (e.key === 'ArrowRight') this.next();
  }

  onTouchStart(e: TouchEvent): void {
    this.touchX = e.changedTouches[0]?.clientX ?? null;
  }

  onTouchEnd(e: TouchEvent): void {
    if (this.touchX == null) return;
    const dx = (e.changedTouches[0]?.clientX ?? this.touchX) - this.touchX;
    this.touchX = null;
    if (Math.abs(dx) < 50) return;
    if (dx > 0) this.prev();
    else this.next();
  }
}
