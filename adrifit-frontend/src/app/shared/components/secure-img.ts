import { Component, OnDestroy, effect, inject, input, signal, untracked } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Subscription } from 'rxjs';

/**
 * <img> for API resources that require the Authorization header (progress photos).
 * `src` is the API path returned by the backend, e.g. "/api/photos/3/content".
 */
@Component({
  selector: 'app-secure-img',
  template: `
    @if (url()) {
      <img [src]="url()" [alt]="alt()" [class]="imgClass()" loading="lazy" />
    } @else if (failed()) {
      <div class="secure-img-placeholder" [class]="imgClass()">Sin imagen</div>
    } @else {
      <div class="secure-img-placeholder loading" [class]="imgClass()"></div>
    }
  `,
  styles: `
    :host { display: block; }
    img { display: block; width: 100%; height: 100%; object-fit: cover; }
    .secure-img-placeholder {
      width: 100%; height: 100%; min-height: 80px; display: grid; place-items: center;
      background: #eef2f7; color: #94a3b8; font-size: 0.8rem;
    }
    .loading { animation: pulse 1.2s ease-in-out infinite; }
    @keyframes pulse { 50% { opacity: 0.55; } }
  `,
})
export class SecureImg implements OnDestroy {
  private readonly http = inject(HttpClient);

  readonly src = input.required<string>();
  readonly alt = input('');
  readonly imgClass = input('');

  readonly url = signal<string | null>(null);
  readonly failed = signal(false);
  private sub?: Subscription;

  constructor() {
    effect(() => {
      // Only `src` is a dependency: reading/writing url/failed must not re-trigger the effect
      // (that would re-download the image in a loop).
      const path = this.src();
      untracked(() => {
        this.release();
        this.failed.set(false);
        this.sub = this.http.get(path, { responseType: 'blob' }).subscribe({
          next: (blob) => this.url.set(URL.createObjectURL(blob)),
          error: () => this.failed.set(true),
        });
      });
    });
  }

  ngOnDestroy(): void {
    this.release();
  }

  private release(): void {
    this.sub?.unsubscribe();
    const current = this.url();
    if (current) URL.revokeObjectURL(current);
    this.url.set(null);
  }
}
