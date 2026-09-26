import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { ReportService } from '../../../core/services/report.service';
import { WeeklyReport } from '../../../shared/models/report.model';
import { ProgressPhoto } from '../../../shared/models/photo.model';
import { NotifyService } from '../../../core/services/notify.service';
import { SecureImg } from '../../../shared/components/secure-img';
import { apiErrorMessage } from '../../../shared/utils/download';
import { PhotoLightbox } from '../../client/report/photo-lightbox';
import { hasLegacyMetrics, weightDeltas } from '../../client/report/report-utils';

type FilterType = 'all' | 'pending' | 'reviewed';

@Component({
  selector: 'app-trainer-reports',
  imports: [MatIconModule, DatePipe, DecimalPipe, RouterLink, SecureImg, PhotoLightbox],
  templateUrl: './trainer-reports.html',
  styleUrl: './trainer-reports.scss',
})
export class TrainerReports {
  private readonly reportService = inject(ReportService);
  private readonly notify = inject(NotifyService);

  readonly hasLegacy = hasLegacyMetrics;

  readonly loading = signal(true);
  readonly error = signal(false);
  readonly reports = signal<WeeklyReport[]>([]);
  readonly filter = signal<FilterType>('all');

  /** Feedback being written, per report id. */
  readonly drafts = signal<Record<number, string>>({});
  /** Reviewed reports whose feedback is being edited again. */
  readonly editing = signal<Set<number>>(new Set());
  readonly savingId = signal<number | null>(null);
  readonly feedbackErrors = signal<Record<number, string>>({});

  readonly lbPhotos = signal<ProgressPhoto[]>([]);
  readonly lbTitle = signal('');
  readonly lbIndex = signal<number | null>(null);

  /** Newest first. */
  readonly sorted = computed(() =>
    [...this.reports()].sort((a, b) => b.createdAt.localeCompare(a.createdAt) || b.id - a.id),
  );

  readonly filtered = computed(() => {
    const f = this.filter();
    const r = this.sorted();
    if (f === 'pending') return r.filter((x) => x.status === 'PENDING');
    if (f === 'reviewed') return r.filter((x) => x.status === 'REVIEWED');
    return r;
  });

  readonly deltas = computed(() => weightDeltas(this.reports()));
  readonly pendingCount = computed(() => this.reports().filter((r) => r.status === 'PENDING').length);

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(false);
    this.reportService.findAll().subscribe({
      next: (r) => {
        this.reports.set(r);
        this.loading.set(false);
      },
      error: () => { this.error.set(true); this.loading.set(false); },
    });
  }

  initials(r: WeeklyReport): string {
    return ((r.clientFirstName?.[0] ?? '') + (r.clientLastName?.[0] ?? '')).toUpperCase() || '?';
  }

  clientName(r: WeeklyReport): string {
    return `${r.clientFirstName ?? ''} ${r.clientLastName ?? ''}`.trim() || `Cliente #${r.clientId}`;
  }

  // ── Feedback ────────────────────────────────────────────────────────────

  showForm(r: WeeklyReport): boolean {
    return r.status === 'PENDING' || this.editing().has(r.id);
  }

  draftOf(r: WeeklyReport): string {
    return this.drafts()[r.id] ?? r.coachFeedback ?? '';
  }

  onDraft(id: number, event: Event): void {
    const value = (event.target as HTMLTextAreaElement).value;
    this.drafts.update((d) => ({ ...d, [id]: value }));
    if (this.feedbackErrors()[id]) this.setError(id, null);
  }

  editFeedback(r: WeeklyReport): void {
    this.editing.update((s) => new Set(s).add(r.id));
    this.drafts.update((d) => ({ ...d, [r.id]: r.coachFeedback ?? '' }));
  }

  cancelEdit(r: WeeklyReport): void {
    this.editing.update((s) => { const n = new Set(s); n.delete(r.id); return n; });
    this.drafts.update((d) => { const n = { ...d }; delete n[r.id]; return n; });
    this.setError(r.id, null);
  }

  saveFeedback(r: WeeklyReport): void {
    const text = this.draftOf(r).trim();
    if (!text) { this.setError(r.id, 'El feedback no puede estar vacío.'); return; }
    if (this.savingId() !== null) return;
    this.savingId.set(r.id);
    this.setError(r.id, null);
    this.reportService.setFeedback(r.id, { coachFeedback: text }).subscribe({
      next: (updated) => {
        this.reports.update((list) => list.map((x) => (x.id === r.id ? { ...updated, photos: updated.photos ?? x.photos } : x)));
        this.cancelEdit(r);
        this.savingId.set(null);
        this.notify.success('Feedback enviado. Seguimiento marcado como revisado.');
      },
      error: (err) => {
        const msg = apiErrorMessage(err, 'No se pudo guardar el feedback.');
        this.setError(r.id, msg);
        this.notify.error(msg);
        this.savingId.set(null);
      },
    });
  }

  private setError(id: number, msg: string | null): void {
    this.feedbackErrors.update((e) => {
      const n = { ...e };
      if (msg) n[id] = msg;
      else delete n[id];
      return n;
    });
  }

  // ── Photos ──────────────────────────────────────────────────────────────

  openPhotos(r: WeeklyReport, index: number): void {
    this.lbPhotos.set(r.photos ?? []);
    this.lbTitle.set(this.clientName(r));
    this.lbIndex.set(index);
  }
}
