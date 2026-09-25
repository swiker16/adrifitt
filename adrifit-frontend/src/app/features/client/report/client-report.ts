import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { ReportService } from '../../../core/services/report.service';
import { WeeklyReport } from '../../../shared/models/report.model';
import { apiErrorMessage } from '../../../shared/utils/download';

@Component({
  selector: 'app-client-report',
  imports: [ReactiveFormsModule, MatIconModule, DatePipe, RouterLink],
  templateUrl: './client-report.html',
  styleUrl: './client-report.scss',
})
export class ClientReport {
  private readonly fb = inject(FormBuilder);
  private readonly reportService = inject(ReportService);

  readonly submitted = signal(false);
  readonly saving = signal(false);
  readonly submitError = signal<string | null>(null);
  readonly history = signal<WeeklyReport[]>([]);
  readonly historyLoaded = signal(false);
  readonly historyError = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    weight: [null as number | null, [Validators.required, Validators.min(20), Validators.max(400)]],
    waist: [null as number | null, [Validators.min(20), Validators.max(300)]],
    bodyFat: [null as number | null, [Validators.min(1), Validators.max(70)]],
    energyLevel: [7, [Validators.min(1), Validators.max(10)]],
    dietAdherence: [80, [Validators.min(0), Validators.max(100)]],
    trainingAdherence: [80, [Validators.min(0), Validators.max(100)]],
    comments: ['', [Validators.maxLength(1000)]],
  });

  constructor() {
    this.loadHistory();
  }

  private loadHistory(): void {
    this.reportService.findMine().subscribe({
      next: (r) => { this.history.set(r); this.historyLoaded.set(true); },
      error: (err) => {
        this.historyError.set(apiErrorMessage(err, 'No se pudieron cargar tus seguimientos.'));
        this.historyLoaded.set(true);
      },
    });
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    if (this.saving()) return;
    this.saving.set(true);
    this.submitError.set(null);
    const v = this.form.value;
    this.reportService.createMine({
      weight: v.weight!,
      waist: v.waist ?? undefined,
      bodyFat: v.bodyFat ?? undefined,
      energyLevel: v.energyLevel ?? undefined,
      dietAdherence: v.dietAdherence ?? undefined,
      trainingAdherence: v.trainingAdherence ?? undefined,
      comments: v.comments ?? undefined,
    }).subscribe({
      next: (r) => {
        this.history.update(h => [r, ...h]);
        this.saving.set(false);
        this.submitted.set(true);
      },
      error: (err) => {
        this.submitError.set(apiErrorMessage(err, 'No se pudo enviar el seguimiento. Inténtalo de nuevo.'));
        this.saving.set(false);
      },
    });
  }

  reset(): void {
    this.form.reset({ energyLevel: 7, dietAdherence: 80, trainingAdherence: 80 });
    this.submitted.set(false);
    this.submitError.set(null);
  }
}
