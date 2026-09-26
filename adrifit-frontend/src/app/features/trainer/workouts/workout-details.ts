import { Component, computed, inject, input, signal } from '@angular/core';
import { DatePipe, NgTemplateOutlet } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { WorkoutService } from '../../../core/services/workout.service';
import { ClientService } from '../../../core/services/client.service';
import { Workout, WorkoutExercise } from '../../../shared/models/workout.model';
import { Client } from '../../../shared/models/client.model';
import { FormsModule } from '@angular/forms';

export interface DayGroup { dayNumber: number; dayName: string; exercises: WorkoutExercise[]; }

@Component({
  selector: 'app-workout-details',
  imports: [RouterLink, DatePipe, NgTemplateOutlet, FormsModule, MatIconModule],
  templateUrl: './workout-details.html',
  styleUrl: './workout-details.scss',
})
export class WorkoutDetails {
  private readonly workoutService = inject(WorkoutService);
  private readonly clientService = inject(ClientService);

  readonly id = input.required<string>();

  readonly workout = signal<Workout | null>(null);
  readonly loading = signal(true);
  readonly error = signal(false);

  readonly groupedByDay = computed((): DayGroup[] => {
    const exercises = this.workout()?.exercises ?? [];
    if (!exercises.some(e => e.dayNumber)) return [];
    const map = new Map<number, DayGroup>();
    for (const ex of exercises) {
      const n = ex.dayNumber ?? 1;
      if (!map.has(n)) map.set(n, { dayNumber: n, dayName: ex.dayName ?? `Día ${n}`, exercises: [] });
      map.get(n)!.exercises.push(ex);
    }
    return Array.from(map.values()).sort((a, b) => a.dayNumber - b.dayNumber);
  });

  readonly totalSets = computed(() =>
    (this.workout()?.exercises ?? []).reduce((sum, e) => sum + (e.sets || 0), 0));

  readonly clients = signal<Client[]>([]);
  readonly selectedClientId = signal<number | null>(null);
  readonly assigning = signal(false);
  readonly assignMessage = signal<string | null>(null);

  constructor() {
    queueMicrotask(() => this.load());
  }

  private load(): void {
    this.workoutService.findById(Number(this.id())).subscribe({
      next: (w) => { this.workout.set(w); this.loading.set(false); },
      error: () => { this.error.set(true); this.loading.set(false); },
    });
    this.clientService.findAll().subscribe({
      next: (c) => this.clients.set(c),
      error: () => {},
    });
  }

  assignToClient(): void {
    const clientId = this.selectedClientId();
    if (!clientId) return;
    this.assigning.set(true);
    this.assignMessage.set(null);
    this.workoutService.assignToClient(clientId, { workoutId: Number(this.id()) }).subscribe({
      next: () => {
        this.assigning.set(false);
        this.assignMessage.set('Rutina asignada correctamente.');
      },
      error: () => {
        this.assigning.set(false);
        this.assignMessage.set('No se pudo asignar la rutina.');
      },
    });
  }

  exportPdf(clientId: number): void {
    const w = this.workout();
    if (!w) return;
    this.workoutService.downloadPdf(w.id, clientId).subscribe(blob => {
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `rutina-${w.name.replace(/\s+/g, '-').toLowerCase()}.pdf`;
      a.click();
      URL.revokeObjectURL(url);
    });
  }
}
