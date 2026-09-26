import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DatePipe } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { WorkoutService } from '../../../core/services/workout.service';
import { Workout } from '../../../shared/models/workout.model';

@Component({
  selector: 'app-workout-list',
  imports: [RouterLink, DatePipe, MatIconModule],
  templateUrl: './workout-list.html',
  styleUrl: './workout-list.scss',
})
export class WorkoutList {
  private readonly workoutService = inject(WorkoutService);

  readonly workouts = signal<Workout[]>([]);
  readonly loading = signal(true);
  readonly error = signal(false);
  readonly deletingId = signal<number | null>(null);

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(false);
    this.workoutService.findAll().subscribe({
      next: (w) => { this.workouts.set(w); this.loading.set(false); },
      error: () => { this.error.set(true); this.loading.set(false); },
    });
  }

  duplicate(id: number): void {
    this.workoutService.duplicate(id).subscribe({
      next: (w) => this.workouts.update((list) => [w, ...list]),
    });
  }

  delete(id: number): void {
    if (!confirm('¿Eliminar esta rutina? Se desasignará de los clientes que la tengan activa.')) return;
    this.deletingId.set(id);
    this.workoutService.delete(id).subscribe({
      next: () => {
        this.workouts.update((list) => list.filter((w) => w.id !== id));
        this.deletingId.set(null);
      },
      error: () => this.deletingId.set(null),
    });
  }
}
