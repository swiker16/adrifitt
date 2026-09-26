import { HttpEventType } from '@angular/common/http';
import { Component, OnDestroy, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { Subscription } from 'rxjs';
import { VideoService } from '../../core/services/video.service';
import { TechniqueVideo } from '../models/video.model';
import { apiErrorMessage } from '../utils/download';

export const MAX_VIDEO_MB = 200;

export interface VideoClientOption {
  id: number;
  name: string;
}

/**
 * Send a technique video. Client: their execution for the trainer to correct.
 * Trainer: a demonstration for one client. Records with the camera on phones.
 */
@Component({
  selector: 'app-video-upload-dialog',
  imports: [FormsModule, MatIconModule],
  template: `
    <div class="modal-backdrop" (click)="close()">
      <div class="modal" role="dialog" aria-modal="true" aria-labelledby="video-upload-title" (click)="$event.stopPropagation()">
        <div class="modal-head">
          <h2 id="video-upload-title">{{ mode() === 'client' ? 'Enviar vídeo de técnica' : 'Enviar vídeo a un cliente' }}</h2>
          <button type="button" class="icon-btn" (click)="close()" aria-label="Cerrar" [disabled]="uploading()"><mat-icon>close</mat-icon></button>
        </div>

        <div class="modal-body">
          @if (!file()) {
            @if (mode() === 'client') {
              <div class="alert info tips">
                <mat-icon>tips_and_updates</mat-icon>
                <div>
                  <strong>Para que tu entrenador pueda corregirte:</strong>
                  <ul>
                    <li>Graba de lado (o en diagonal) y que se vea todo el cuerpo.</li>
                    <li>1 serie completa basta: mejor menos de 1 minuto.</li>
                    <li>Buena luz y el móvil apoyado, sin moverlo.</li>
                  </ul>
                </div>
              </div>
            }
            <div class="pickers">
              @if (touch) {
                <label class="picker primary">
                  <input type="file" accept="video/*" capture="environment" (change)="onFile($event)" />
                  <mat-icon>videocam</mat-icon>
                  <span>Grabar vídeo</span>
                </label>
              }
              <label class="picker">
                <input type="file" accept="video/mp4,video/quicktime,video/webm,video/*" (change)="onFile($event)" data-testid="video-file" />
                <mat-icon>video_library</mat-icon>
                <span>{{ touch ? 'Elegir de la galería' : 'Elegir vídeo' }}</span>
              </label>
            </div>
            <p class="hint center">MP4, MOV (iPhone) o WEBM · máximo {{ maxMb }} MB</p>
          } @else {
            <div class="preview">
              <video [src]="previewUrl()" controls playsinline muted preload="metadata" (loadedmetadata)="onMeta($event)"></video>
              <div class="file-meta">
                <span><mat-icon>movie</mat-icon> {{ file()!.name }}</span>
                <span>{{ sizeLabel() }}@if (duration()) { · {{ durationLabel() }} }</span>
                @if (!uploading()) {
                  <button type="button" class="btn btn-light btn-sm" (click)="clearFile()"><mat-icon>swap_horiz</mat-icon> Cambiar</button>
                }
              </div>
            </div>
          }

          @if (fileError()) {
            <div class="alert error"><mat-icon>error</mat-icon><span>{{ fileError() }}</span></div>
          }

          <div class="form-grid">
            @if (mode() === 'trainer') {
              <div class="field full">
                <label for="vu-client">Cliente</label>
                <select id="vu-client" [(ngModel)]="clientId" name="clientId" [disabled]="uploading()">
                  <option [ngValue]="null" disabled>Selecciona un cliente</option>
                  @for (c of clients(); track c.id) { <option [ngValue]="c.id">{{ c.name }}</option> }
                </select>
              </div>
            }
            <div class="field full">
              <label for="vu-exercise">Ejercicio</label>
              <input id="vu-exercise" list="vu-exercises" [(ngModel)]="exercise" name="exercise" maxlength="150"
                     placeholder="p. ej. Sentadilla con barra" autocomplete="off" [disabled]="uploading()" />
              <datalist id="vu-exercises">
                @for (e of exercises(); track e) { <option [value]="e"></option> }
              </datalist>
            </div>
            <div class="field full">
              <label for="vu-note">{{ mode() === 'client' ? '¿Qué quieres que revise? (opcional)' : 'Puntos clave (opcional)' }}</label>
              <textarea id="vu-note" [(ngModel)]="note" name="note" maxlength="1000" rows="3" [disabled]="uploading()"
                        [placeholder]="mode() === 'client' ? 'p. ej. Noto molestia en la rodilla al bajar' : 'p. ej. Cadera atrás, espalda neutra y barra pegada a las piernas'"></textarea>
            </div>
          </div>

          @if (uploading()) {
            <div class="upload-progress" role="status" aria-live="polite">
              <div class="progress"><span [style.width.%]="progress()"></span></div>
              <span>{{ progress() < 100 ? 'Subiendo… ' + progress() + ' %' : 'Procesando…' }}</span>
            </div>
          }
          @if (error()) {
            <div class="alert error"><mat-icon>error</mat-icon><span>{{ error() }}</span></div>
          }
        </div>

        <div class="modal-foot">
          <button type="button" class="btn btn-outline" (click)="close()">{{ uploading() ? 'Cancelar subida' : 'Cancelar' }}</button>
          <button type="button" class="btn btn-primary" (click)="send()" [disabled]="!canSend()">
            <mat-icon>send</mat-icon> {{ uploading() ? 'Enviando…' : 'Enviar vídeo' }}
          </button>
        </div>
      </div>
    </div>
  `,
  styles: `
    .modal-body { display: grid; gap: 16px; }
    .tips ul { margin: 6px 0 0; padding-left: 18px; }
    .tips li { margin: 2px 0; }
    .pickers { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 12px; }
    .picker {
      position: relative; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 8px;
      min-height: 130px; border: 2px dashed var(--border-strong); border-radius: 16px; cursor: pointer;
      font-weight: 600; color: var(--text); background: var(--surface-2); transition: border-color .2s, background .2s;
    }
    .picker:hover, .picker:focus-within { border-color: var(--brand); background: var(--brand-50); }
    .picker.primary { border-style: solid; border-color: var(--brand); background: var(--brand-50); }
    .picker mat-icon { font-size: 34px; width: 34px; height: 34px; color: var(--brand); }
    .picker input { position: absolute; inset: 0; opacity: 0; cursor: pointer; }
    .hint { color: var(--text-subtle); font-size: .8rem; margin: -6px 0 0; }
    .center { text-align: center; }
    .preview { display: grid; gap: 10px; }
    .preview video { width: 100%; max-height: 46vh; border-radius: 14px; background: #000; }
    .file-meta { display: flex; flex-wrap: wrap; gap: 8px 14px; align-items: center; font-size: .84rem; color: var(--text-muted); }
    .file-meta span { display: inline-flex; align-items: center; gap: 4px; min-width: 0; }
    .file-meta span:first-child { flex: 1 1 200px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-weight: 600; color: var(--text); }
    .file-meta mat-icon { font-size: 18px; width: 18px; height: 18px; }
    .upload-progress { display: grid; gap: 6px; font-size: .85rem; color: var(--text-muted); }
    .upload-progress .progress { height: 10px; }
    .upload-progress .progress span { transition: width .2s; }
  `,
})
export class VideoUploadDialog implements OnInit, OnDestroy {
  private readonly videos = inject(VideoService);

  readonly mode = input<'client' | 'trainer'>('client');
  readonly exercises = input<string[]>([]);
  readonly clients = input<VideoClientOption[]>([]);
  readonly initialExercise = input<string | null>(null);
  readonly initialClientId = input<number | null>(null);

  readonly closed = output<void>();
  readonly uploaded = output<TechniqueVideo>();

  readonly maxMb = MAX_VIDEO_MB;
  /** Phones/tablets: offer "record now" (camera) besides the gallery. */
  readonly touch = typeof window !== 'undefined' && window.matchMedia?.('(pointer: coarse)').matches;

  readonly file = signal<File | null>(null);
  readonly previewUrl = signal<string | null>(null);
  readonly duration = signal<number | null>(null);
  readonly fileError = signal<string | null>(null);
  readonly error = signal<string | null>(null);
  readonly uploading = signal(false);
  readonly progress = signal(0);

  exercise = '';
  note = '';
  clientId: number | null = null;

  private upload?: Subscription;

  readonly sizeLabel = computed(() => {
    const f = this.file();
    return f ? `${(f.size / 1024 / 1024).toFixed(1)} MB` : '';
  });
  readonly durationLabel = computed(() => {
    const d = Math.round(this.duration() ?? 0);
    return `${Math.floor(d / 60)}:${String(d % 60).padStart(2, '0')} min`;
  });

  ngOnInit(): void {
    this.exercise = this.initialExercise() ?? '';
    this.clientId = this.initialClientId();
  }

  ngOnDestroy(): void {
    this.upload?.unsubscribe();
    this.revoke();
  }

  canSend(): boolean {
    return !!this.file() && !this.fileError() && !this.uploading() && this.exercise.trim().length > 0
      && (this.mode() === 'client' || this.clientId != null);
  }

  onFile(event: Event): void {
    const inputEl = event.target as HTMLInputElement;
    const f = inputEl.files?.[0];
    inputEl.value = '';
    if (!f) return;
    this.error.set(null);
    if (f.type && !f.type.startsWith('video/')) {
      this.fileError.set('Ese archivo no es un vídeo.');
      return;
    }
    if (f.size > MAX_VIDEO_MB * 1024 * 1024) {
      this.fileError.set(`El vídeo pesa ${(f.size / 1024 / 1024).toFixed(0)} MB y el máximo son ${MAX_VIDEO_MB} MB. Recórtalo o grábalo más corto.`);
      return;
    }
    this.fileError.set(null);
    this.revoke();
    this.file.set(f);
    this.duration.set(null);
    this.previewUrl.set(URL.createObjectURL(f));
  }

  onMeta(event: Event): void {
    const d = (event.target as HTMLVideoElement).duration;
    this.duration.set(Number.isFinite(d) ? d : null);
  }

  clearFile(): void {
    this.revoke();
    this.file.set(null);
    this.duration.set(null);
    this.fileError.set(null);
  }

  send(): void {
    const file = this.file();
    if (!file || !this.canSend()) return;
    const payload = { file, exerciseName: this.exercise.trim(), note: this.note.trim() || null, durationSeconds: this.duration() };
    const request = this.mode() === 'client'
      ? this.videos.uploadMine(payload)
      : this.videos.uploadForClient(this.clientId!, payload);
    this.uploading.set(true);
    this.progress.set(0);
    this.error.set(null);
    this.upload = request.subscribe({
      next: (event) => {
        if (event.type === HttpEventType.UploadProgress && event.total) {
          this.progress.set(Math.min(100, Math.round((event.loaded / event.total) * 100)));
        } else if (event.type === HttpEventType.Response && event.body) {
          this.uploading.set(false);
          this.uploaded.emit(event.body);
        }
      },
      error: (err) => {
        this.uploading.set(false);
        this.error.set(err?.status === 413
          ? `El vídeo supera el máximo de ${MAX_VIDEO_MB} MB.`
          : apiErrorMessage(err, 'No se pudo enviar el vídeo. Revisa tu conexión e inténtalo de nuevo.'));
      },
    });
  }

  close(): void {
    // Closing while uploading cancels the request (unsubscribing aborts the XHR).
    this.upload?.unsubscribe();
    this.uploading.set(false);
    this.closed.emit();
  }

  private revoke(): void {
    const url = this.previewUrl();
    if (url) URL.revokeObjectURL(url);
    this.previewUrl.set(null);
  }
}
