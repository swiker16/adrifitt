import { Component, ElementRef, OnDestroy, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { ReportService } from '../../../core/services/report.service';
import { NotifyService } from '../../../core/services/notify.service';
import { SecureImg } from '../../../shared/components/secure-img';
import { REPORT_MAX_PHOTOS, REPORT_MIN_PHOTOS, WeeklyReport } from '../../../shared/models/report.model';
import { ProgressPhoto } from '../../../shared/models/photo.model';
import { apiErrorMessage } from '../../../shared/utils/download';
import { PhotoLightbox } from './photo-lightbox';
import { hasLegacyMetrics, parseDecimal, weightDeltas } from './report-utils';

const MAX_BYTES = 10 * 1024 * 1024;
const ACCEPTED = ['image/jpeg', 'image/png', 'image/webp'];
const MIN_WEIGHT = 20;
const MAX_WEIGHT = 400;
const MAX_COMMENT = 1000;

interface PickedPhoto {
  key: number;
  file: File;
  url: string;
}

@Component({
  selector: 'app-client-report',
  imports: [MatIconModule, DatePipe, DecimalPipe, RouterLink, SecureImg, PhotoLightbox],
  templateUrl: './client-report.html',
  styleUrl: './client-report.scss',
})
export class ClientReport implements OnDestroy {
  private readonly reportService = inject(ReportService);
  private readonly notify = inject(NotifyService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly minPhotos = REPORT_MIN_PHOTOS;
  readonly maxPhotos = REPORT_MAX_PHOTOS;
  readonly maxComment = MAX_COMMENT;
  readonly hasLegacy = hasLegacyMetrics;

  // ── New check-in ────────────────────────────────────────────────────────
  readonly picked = signal<PickedPhoto[]>([]);
  readonly fileError = signal<string | null>(null);
  readonly weightText = signal('');
  readonly comments = signal('');
  readonly triedSubmit = signal(false);
  readonly weightTouched = signal(false);
  readonly saving = signal(false);
  readonly submitError = signal<string | null>(null);
  readonly submitted = signal<WeeklyReport | null>(null);
  private nextKey = 1;

  readonly weight = computed(() => parseDecimal(this.weightText()));
  readonly weightError = computed(() => {
    if (!this.weightTouched() && !this.triedSubmit()) return null;
    return this.weightValidation(this.weightText());
  });
  readonly photoCountError = computed(() => {
    const n = this.picked().length;
    if (!this.triedSubmit() && n <= this.maxPhotos) return null;
    if (n < this.minPhotos) return `Añade al menos ${this.minPhotos} fotos (te ${this.minPhotos - n === 1 ? 'falta 1' : 'faltan ' + (this.minPhotos - n)}).`;
    if (n > this.maxPhotos) return `Máximo ${this.maxPhotos} fotos.`;
    return null;
  });
  readonly photosOk = computed(() => this.picked().length >= this.minPhotos && this.picked().length <= this.maxPhotos);
  readonly canSubmit = computed(() => this.photosOk() && this.weightValidation(this.weightText()) === null && !this.saving());
  /** Progress of the 3 "steps" (photos, weight, comment) for the sticky bar. */
  readonly stepsDone = computed(
    () => (this.photosOk() ? 1 : 0) + (this.weightValidation(this.weightText()) === null ? 1 : 0) + (this.comments().trim() ? 1 : 0),
  );

  // ── History ─────────────────────────────────────────────────────────────
  readonly history = signal<WeeklyReport[]>([]);
  readonly historyLoading = signal(true);
  readonly historyError = signal<string | null>(null);
  readonly deltas = computed(() => weightDeltas(this.history()));
  readonly lastWeight = computed(() => this.history()[0]?.weight ?? null);

  readonly editId = signal<number | null>(null);
  readonly editWeight = signal('');
  readonly editComments = signal('');
  readonly editError = signal<string | null>(null);
  readonly savingEdit = signal(false);

  // ── Lightbox ────────────────────────────────────────────────────────────
  readonly lbPhotos = signal<ProgressPhoto[]>([]);
  readonly lbIndex = signal<number | null>(null);
  readonly lbTitle = signal('');

  constructor() {
    this.loadHistory();
  }

  ngOnDestroy(): void {
    this.picked().forEach((p) => URL.revokeObjectURL(p.url));
  }

  loadHistory(): void {
    this.historyLoading.set(true);
    this.historyError.set(null);
    this.reportService.findMine().subscribe({
      next: (r) => {
        this.history.set([...r].sort((a, b) => b.createdAt.localeCompare(a.createdAt) || b.id - a.id));
        this.historyLoading.set(false);
      },
      error: (err) => {
        this.historyError.set(apiErrorMessage(err, 'No se pudieron cargar tus seguimientos.'));
        this.historyLoading.set(false);
      },
    });
  }

  // ── Photos ──────────────────────────────────────────────────────────────

  onFiles(event: Event): void {
    const input = event.target as HTMLInputElement;
    const files = Array.from(input.files ?? []);
    input.value = ''; // allow picking the same file again after removing it
    if (files.length === 0) return;
    this.submitError.set(null);

    const problems: string[] = [];
    const valid: File[] = [];
    for (const f of files) {
      if (!ACCEPTED.includes(f.type)) {
        problems.push(`"${f.name}" no es JPG, PNG o WEBP.`);
      } else if (f.size > MAX_BYTES) {
        problems.push(`"${f.name}" pesa ${(f.size / 1024 / 1024).toFixed(1)} MB (máx. 10 MB).`);
      } else {
        valid.push(f);
      }
    }
    const room = this.maxPhotos - this.picked().length;
    if (valid.length > room) {
      problems.push(
        room <= 0
          ? `Ya tienes ${this.maxPhotos} fotos, el máximo. Quita alguna para añadir otra.`
          : `Solo caben ${room} ${room === 1 ? 'foto más' : 'fotos más'} (máximo ${this.maxPhotos}); se han ignorado ${valid.length - room}.`,
      );
    }
    const accepted = valid.slice(0, Math.max(room, 0)).map((file) => ({ key: this.nextKey++, file, url: URL.createObjectURL(file) }));
    if (accepted.length) this.picked.update((list) => [...list, ...accepted]);
    this.fileError.set(problems.length ? problems.join(' ') : null);
  }

  removePhoto(key: number): void {
    const p = this.picked().find((x) => x.key === key);
    if (p) URL.revokeObjectURL(p.url);
    this.picked.update((list) => list.filter((x) => x.key !== key));
    this.fileError.set(null);
  }

  // ── Fields ──────────────────────────────────────────────────────────────

  onWeight(event: Event): void {
    this.weightText.set((event.target as HTMLInputElement).value);
  }

  onComments(event: Event): void {
    this.comments.set((event.target as HTMLTextAreaElement).value);
  }

  private weightValidation(text: string): string | null {
    if (!text.trim()) return 'Indica tu peso de hoy.';
    const w = parseDecimal(text);
    if (w == null) return 'Escribe un número, por ejemplo 79,5.';
    if (w < MIN_WEIGHT || w > MAX_WEIGHT) return `El peso debe estar entre ${MIN_WEIGHT} y ${MAX_WEIGHT} kg.`;
    return null;
  }

  // ── Submit ──────────────────────────────────────────────────────────────

  submit(): void {
    this.triedSubmit.set(true);
    if (this.saving()) return;
    if (!this.canSubmit()) {
      this.submitError.set(null);
      return;
    }
    this.saving.set(true);
    this.submitError.set(null);
    this.reportService.submitMine(this.weight()!, this.comments().trim() || null, this.picked().map((p) => p.file)).subscribe({
      next: (r) => {
        this.saving.set(false);
        this.history.update((h) => [r, ...h.filter((x) => x.id !== r.id)]);
        this.submitted.set(r);
        this.clearForm();
        this.host.nativeElement.scrollIntoView?.({ behavior: 'smooth', block: 'start' });
      },
      error: (err) => {
        this.saving.set(false);
        this.submitError.set(apiErrorMessage(err, 'No se pudo enviar el seguimiento. Inténtalo de nuevo.'));
      },
    });
  }

  private clearForm(): void {
    this.picked().forEach((p) => URL.revokeObjectURL(p.url));
    this.picked.set([]);
    this.weightText.set('');
    this.comments.set('');
    this.triedSubmit.set(false);
    this.weightTouched.set(false);
    this.fileError.set(null);
    this.submitError.set(null);
  }

  newCheckIn(): void {
    this.submitted.set(null);
  }

  // ── Edit a pending check-in ─────────────────────────────────────────────

  startEdit(r: WeeklyReport): void {
    this.editId.set(r.id);
    this.editWeight.set(String(r.weight).replace('.', ','));
    this.editComments.set(r.comments ?? '');
    this.editError.set(null);
  }

  cancelEdit(): void {
    this.editId.set(null);
    this.editError.set(null);
  }

  onEditWeight(event: Event): void {
    this.editWeight.set((event.target as HTMLInputElement).value);
  }

  onEditComments(event: Event): void {
    this.editComments.set((event.target as HTMLTextAreaElement).value);
  }

  saveEdit(r: WeeklyReport): void {
    const problem = this.weightValidation(this.editWeight());
    if (problem) {
      this.editError.set(problem);
      return;
    }
    if (this.savingEdit()) return;
    this.savingEdit.set(true);
    this.editError.set(null);
    this.reportService.updateMine(r.id, parseDecimal(this.editWeight())!, this.editComments().trim() || null).subscribe({
      next: (updated) => {
        this.savingEdit.set(false);
        this.history.update((h) => h.map((x) => (x.id === updated.id ? updated : x)));
        if (this.submitted()?.id === updated.id) this.submitted.set(updated);
        this.editId.set(null);
        this.notify.success('Seguimiento actualizado');
      },
      error: (err) => {
        this.savingEdit.set(false);
        this.editError.set(apiErrorMessage(err, 'No se pudo guardar el cambio.'));
      },
    });
  }

  // ── Lightbox ────────────────────────────────────────────────────────────

  openPhotos(r: WeeklyReport, index: number): void {
    this.lbPhotos.set(r.photos ?? []);
    this.lbTitle.set('Seguimiento');
    this.lbIndex.set(index);
  }
}
