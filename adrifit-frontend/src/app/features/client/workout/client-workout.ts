import { Component, computed, inject, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { DatePipe } from '@angular/common';
import { WorkoutService } from '../../../core/services/workout.service';
import { ClientService } from '../../../core/services/client.service';
import { AuthService } from '../../../core/auth/auth.service';
import { ClientWorkout, WorkoutExercise } from '../../../shared/models/workout.model';
import { Client } from '../../../shared/models/client.model';

export interface DayGroup {
  dayNumber: number;
  dayName: string;
  exercises: WorkoutExercise[];
}

@Component({
  selector: 'app-client-workout',
  imports: [MatIconModule, DatePipe],
  templateUrl: './client-workout.html',
  styleUrl: './client-workout.scss',
})
export class ClientWorkoutView {
  private readonly workoutService = inject(WorkoutService);
  private readonly clientService = inject(ClientService);
  private readonly auth = inject(AuthService);

  readonly clientWorkout = signal<ClientWorkout | null>(null);
  readonly myProfile = signal<Client | null>(null);
  readonly loading = signal(true);
  readonly error = signal(false);
  readonly uploadingPhoto = signal(false);

  readonly groupedByDay = computed((): DayGroup[] => {
    const exercises = this.clientWorkout()?.workout.exercises ?? [];
    const hasDays = exercises.some(e => e.dayNumber != null);
    if (!hasDays) return [];
    const map = new Map<number, DayGroup>();
    for (const ex of exercises) {
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
      next: (cw) => { this.clientWorkout.set(cw); this.loading.set(false); },
      error: () => { this.error.set(true); this.loading.set(false); },
    });
  }

  onPhotoSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    const profile = this.myProfile();
    if (!profile) return;
    this.uploadingPhoto.set(true);
    this.clientService.uploadPhoto(profile.id, file).subscribe({
      next: (updated) => { this.myProfile.set(updated); this.uploadingPhoto.set(false); },
      error: () => this.uploadingPhoto.set(false),
    });
    input.value = '';
  }

  exportPdf(): void {
    const cw = this.clientWorkout();
    if (!cw) return;
    this.workoutService.downloadPdf(cw.workout.id, cw.clientId).subscribe(blob => {
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `rutina-${cw.workout.name.replace(/\s+/g, '-').toLowerCase()}.pdf`;
      a.click();
      URL.revokeObjectURL(url);
    });
  }
}
