import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { AnalysisService } from '../../../core/services/analysis.service';
import { NotifyService } from '../../../core/services/notify.service';
import { ClientAnalysis } from '../../../shared/models/analysis.model';
import { openBlob, saveBlob } from '../../../shared/utils/download';

type Filter = 'UPLOADED' | 'REVIEWED' | 'ALL';

@Component({
  selector: 'app-trainer-analyses',
  imports: [DatePipe, RouterLink, FormsModule, MatIconModule],
  templateUrl: './trainer-analyses.html',
  styleUrl: './trainer-analyses.scss',
})
export class TrainerAnalyses {
  private readonly analysisService = inject(AnalysisService);
  private readonly notify = inject(NotifyService);

  readonly analyses = signal<ClientAnalysis[]>([]);
  readonly loading = signal(true);
  readonly error = signal(false);
  readonly filter = signal<Filter>('UPLOADED');
  readonly busyId = signal<number | null>(null);

  readonly reviewing = signal<ClientAnalysis | null>(null);
  readonly reviewNote = signal('');
  readonly savingReview = signal(false);

  readonly count = computed(() => this.analyses().length);

  constructor() {
    this.load();
  }

  setFilter(f: Filter): void {
    this.filter.set(f);
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(false);
    const f = this.filter();
    this.analysisService.findAll(f === 'ALL' ? null : f).subscribe({
      next: (list) => {
        this.analyses.set(list);
        this.loading.set(false);
      },
      error: () => {
        this.error.set(true);
        this.loading.set(false);
      },
    });
  }

  view(a: ClientAnalysis): void {
    this.busyId.set(a.id);
    this.analysisService.downloadContent(a.id, 'inline', 'trainer').subscribe({
      next: (blob) => {
        this.busyId.set(null);
        openBlob(blob);
      },
      error: (err) => {
        this.busyId.set(null);
        this.notify.error(err, 'No se pudo abrir la analítica.');
      },
    });
  }

  download(a: ClientAnalysis): void {
    this.busyId.set(a.id);
    this.analysisService.downloadContent(a.id, 'attachment', 'trainer').subscribe({
      next: (blob) => {
        this.busyId.set(null);
        saveBlob(blob, a.originalFileName || `analitica-${a.id}.pdf`);
      },
      error: (err) => {
        this.busyId.set(null);
        this.notify.error(err, 'No se pudo descargar la analítica.');
      },
    });
  }

  openReview(a: ClientAnalysis): void {
    this.reviewing.set(a);
    this.reviewNote.set(a.trainerInternalNote ?? '');
  }

  closeReview(): void {
    if (this.savingReview()) return;
    this.reviewing.set(null);
  }

  submitReview(): void {
    const a = this.reviewing();
    if (!a) return;
    this.savingReview.set(true);
    const note = this.reviewNote().trim();
    this.analysisService.review(a.id, { trainerInternalNote: note || undefined }).subscribe({
      next: (updated) => {
        this.savingReview.set(false);
        this.reviewing.set(null);
        if (this.filter() === 'UPLOADED') {
          this.analyses.update((list) => list.filter((x) => x.id !== a.id));
        } else {
          this.analyses.update((list) => list.map((x) => (x.id === a.id ? { ...x, ...updated } : x)));
        }
        this.notify.success('Analítica marcada como revisada.');
      },
      error: (err) => {
        this.savingReview.set(false);
        this.notify.error(err, 'No se pudo marcar como revisada.');
      },
    });
  }

  fileSize(bytes: number): string {
    if (!bytes) return '';
    if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  }
}
