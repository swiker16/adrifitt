import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { ReportService } from '../../../core/services/report.service';
import { WeeklyReport } from '../../../shared/models/report.model';
import { NotifyService } from '../../../core/services/notify.service';
import { apiErrorMessage } from '../../../shared/utils/download';

type FilterType = 'all' | 'pending' | 'reviewed';

@Component({
  selector: 'app-trainer-reports',
  imports: [MatIconModule, FormsModule, DatePipe, RouterLink],
  templateUrl: './trainer-reports.html',
  styleUrl: './trainer-reports.scss',
})
export class TrainerReports {
  private readonly reportService = inject(ReportService);
  private readonly notify = inject(NotifyService);

  readonly loading = signal(true);
  readonly error = signal(false);
  readonly reports = signal<WeeklyReport[]>([]);
  readonly filter = signal<FilterType>('all');

  readonly expandedId = signal<number | null>(null);
  readonly feedbackText = signal('');
  readonly savingFeedback = signal(false);
  readonly feedbackError = signal<string | null>(null);

  readonly filtered = computed(() => {
    const f = this.filter();
    const r = this.reports();
    if (f === 'pending') return r.filter(x => x.status === 'PENDING');
    if (f === 'reviewed') return r.filter(x => x.status === 'REVIEWED');
    return r;
  });

  readonly pendingCount = computed(() => this.reports().filter(r => r.status === 'PENDING').length);

  constructor() {
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.reportService.findAll().subscribe({
      next: (r) => { this.reports.set(r); this.loading.set(false); },
      error: () => { this.error.set(true); this.loading.set(false); },
    });
  }

  initials(r: WeeklyReport): string {
    return ((r.clientFirstName?.[0] ?? '') + (r.clientLastName?.[0] ?? '')).toUpperCase();
  }

  clientName(r: WeeklyReport): string {
    return `${r.clientFirstName ?? ''} ${r.clientLastName ?? ''}`.trim();
  }

  toggleExpand(id: number): void {
    if (this.expandedId() === id) {
      this.expandedId.set(null);
      this.feedbackText.set('');
      this.feedbackError.set(null);
    } else {
      this.expandedId.set(id);
      const report = this.reports().find(r => r.id === id);
      this.feedbackText.set(report?.coachFeedback ?? '');
      this.feedbackError.set(null);
    }
  }

  saveFeedback(reportId: number): void {
    const text = this.feedbackText().trim();
    if (!text) { this.feedbackError.set('El feedback no puede estar vacío.'); return; }
    this.savingFeedback.set(true);
    this.feedbackError.set(null);
    this.reportService.setFeedback(reportId, { coachFeedback: text }).subscribe({
      next: (updated) => {
        this.reports.update(list => list.map(r => r.id === reportId ? updated : r));
        this.expandedId.set(null);
        this.feedbackText.set('');
        this.savingFeedback.set(false);
        this.notify.success('Feedback enviado. Seguimiento marcado como revisado.');
      },
      error: (err) => {
        const msg = apiErrorMessage(err, 'No se pudo guardar el feedback.');
        this.feedbackError.set(msg);
        this.notify.error(msg);
        this.savingFeedback.set(false);
      },
    });
  }
}
