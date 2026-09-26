import { Component, computed, inject, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { WorkoutService } from '../../../core/services/workout.service';
import { DashboardService } from '../../../core/services/dashboard.service';
import { NotifyService } from '../../../core/services/notify.service';
import { saveBlob } from '../../../shared/utils/download';
import { ClientWorkout, WorkoutExercise } from '../../../shared/models/workout.model';

export interface DayGroup {
  dayNumber: number;
  dayName: string;
  exercises: WorkoutExercise[];
}

@Component({
  selector: 'app-client-workout',
  imports: [MatIconModule, DatePipe, RouterLink],
  templateUrl: './client-workout.html',
  styleUrl: './client-workout.scss',
})
export class ClientWorkoutView {
  private readonly workoutService = inject(WorkoutService);
  private readonly dashboardService = inject(DashboardService);
  private readonly notify = inject(NotifyService);

  readonly clientWorkout = signal<ClientWorkout | null>(null);
  readonly loading = signal(true);
  readonly error = signal(false);
  readonly pdfExportEnabled = signal(false);
  readonly exporting = signal(false);

  readonly groupedByDay = computed((): DayGroup[] => {
    const exercises = this.clientWorkout()?.workout.exercises ?? [];
    const hasDays = exercises.some((e) => e.dayNumber != null);
    if (!hasDays) return [];
    const map = new Map<number, DayGroup>();
    for (const ex of [...exercises].sort((a, b) => a.orderIndex - b.orderIndex)) {
      const day = ex.dayNumber ?? 0;
      if (!map.has(day)) {
        map.set(day, { dayNumber: day, dayName: ex.dayName ?? `Día ${day}`, exercises: [] });
      }
      map.get(day)!.exercises.push(ex);
    }
    return Array.from(map.values()).sort((a, b) => a.dayNumber - b.dayNumber);
  });

  constructor() {
    this.workoutService.getMyWorkout().subscribe({
      next: (cw) => {
        this.clientWorkout.set(cw);
        this.loading.set(false);
      },
      error: () => {
        this.error.set(true);
        this.loading.set(false);
      },
    });
    this.dashboardService.getClientDashboard().subscribe({
      next: (d) => this.pdfExportEnabled.set(!!d.pdfExportEnabled),
      error: () => this.pdfExportEnabled.set(false),
    });
  }

  restText(seconds: number | null | undefined): string {
    if (seconds == null) return '—';
    if (seconds < 60) return `${seconds}"`;
    const m = Math.floor(seconds / 60);
    const s = seconds % 60;
    return s ? `${m}'${String(s).padStart(2, '0')}"` : `${m}'`;
  }

  exportPdf(): void {
    const cw = this.clientWorkout();
    if (!cw || !this.pdfExportEnabled() || this.exporting()) return;
    this.exporting.set(true);
    this.workoutService.downloadPdf(cw.workout.id, cw.clientId).subscribe({
      next: (blob) => {
        this.exporting.set(false);
        saveBlob(blob, `rutina-${cw.workout.name.replace(/\s+/g, '-').toLowerCase()}.pdf`);
      },
      error: (err: HttpErrorResponse) => {
        this.exporting.set(false);
        const fallback =
          err.status === 403 ? 'La exportación a PDF no está incluida en tu plan.' : 'No se pudo exportar el PDF.';
        // With responseType 'blob' the JSON error body arrives as a Blob: read it to show the backend message.
        if (err.error instanceof Blob) {
          err.error
            .text()
            .then((txt) => {
              try {
                const body = JSON.parse(txt);
                this.notify.error({ status: err.status, error: body }, fallback);
              } catch {
                this.notify.error(fallback);
              }
            })
            .catch(() => this.notify.error(fallback));
        } else {
          this.notify.error(err, fallback);
        }
      },
    });
  }
}
