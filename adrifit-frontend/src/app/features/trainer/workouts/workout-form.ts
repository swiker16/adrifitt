import { Component, inject, input, signal } from '@angular/core';
import { FormArray, FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { WorkoutService } from '../../../core/services/workout.service';
import { WorkoutExercise } from '../../../shared/models/workout.model';

@Component({
  selector: 'app-workout-form',
  imports: [ReactiveFormsModule, RouterLink, MatIconModule],
  templateUrl: './workout-form.html',
  styleUrl: './workout-form.scss',
})
export class WorkoutForm {
  private readonly fb = inject(FormBuilder);
  private readonly workoutService = inject(WorkoutService);
  private readonly router = inject(Router);

  readonly id = input<string | undefined>(undefined);

  readonly saving = signal(false);
  readonly loadingEdit = signal(false);
  readonly serverError = signal<string | null>(null);

  readonly form: FormGroup = this.fb.group({
    name: ['', [Validators.required, Validators.maxLength(120)]],
    description: [''],
    objective: [''],
    daysPerWeek: [null],
    days: this.fb.array([]),
  });

  get days(): FormArray {
    return this.form.get('days') as FormArray;
  }

  dayExercises(dayIndex: number): FormArray {
    return this.days.at(dayIndex).get('exercises') as FormArray;
  }

  get isEdit(): boolean {
    return !!this.id();
  }

  constructor() {
    queueMicrotask(() => {
      if (this.isEdit) {
        this.loadForEdit(Number(this.id()));
      } else {
        this.addDay();
      }
    });
  }

  private loadForEdit(id: number): void {
    this.loadingEdit.set(true);
    this.workoutService.findById(id).subscribe({
      next: (w) => {
        this.form.patchValue({ name: w.name, description: w.description, objective: w.objective, daysPerWeek: w.daysPerWeek });
        if (w.exercises.length > 0) {
          // Group by dayNumber
          const grouped = new Map<number, WorkoutExercise[]>();
          for (const ex of w.exercises) {
            const day = ex.dayNumber ?? 1;
            if (!grouped.has(day)) grouped.set(day, []);
            grouped.get(day)!.push(ex);
          }
          const sorted = Array.from(grouped.entries()).sort((a, b) => a[0] - b[0]);
          for (const [dayNum, exs] of sorted) {
            const dayName = exs[0].dayName ?? `Día ${dayNum}`;
            const dayGroup = this.buildDayGroup(dayName);
            const exArray = dayGroup.get('exercises') as FormArray;
            exs.forEach(e => exArray.push(this.buildExerciseGroup(e)));
            this.days.push(dayGroup);
          }
        } else {
          this.addDay();
        }
        this.loadingEdit.set(false);
      },
      error: () => { this.loadingEdit.set(false); this.serverError.set('No se pudo cargar la rutina.'); },
    });
  }

  private buildDayGroup(dayName = ''): FormGroup {
    return this.fb.group({
      dayName: [dayName, Validators.required],
      exercises: this.fb.array([]),
    });
  }

  private buildExerciseGroup(data?: Partial<WorkoutExercise>): FormGroup {
    return this.fb.group({
      exerciseName: [data?.exerciseName ?? '', Validators.required],
      warmUpSets:   [data?.warmUpSets ?? ''],
      approxReps:   [data?.approxReps ?? ''],
      sets:         [data?.sets ?? 3, [Validators.required, Validators.min(1)]],
      reps:         [data?.reps ?? null],
      notes:        [data?.notes ?? ''],
      orderIndex:   [0],
    });
  }

  addDay(): void {
    const dayGroup = this.buildDayGroup();
    const exArr = dayGroup.get('exercises') as FormArray;
    exArr.push(this.buildExerciseGroup());
    this.days.push(dayGroup);
  }

  removeDay(i: number): void {
    if (this.days.length <= 1) return;
    this.days.removeAt(i);
  }

  addExerciseToDay(dayIndex: number): void {
    this.dayExercises(dayIndex).push(this.buildExerciseGroup());
  }

  removeExerciseFromDay(dayIndex: number, exIndex: number): void {
    this.dayExercises(dayIndex).removeAt(exIndex);
  }

  moveUp(dayIndex: number, i: number): void {
    if (i === 0) return;
    const arr = this.dayExercises(dayIndex);
    const a = arr.at(i); const b = arr.at(i - 1);
    arr.setControl(i, b); arr.setControl(i - 1, a);
  }

  moveDown(dayIndex: number, i: number): void {
    const arr = this.dayExercises(dayIndex);
    if (i === arr.length - 1) return;
    const a = arr.at(i); const b = arr.at(i + 1);
    arr.setControl(i, b); arr.setControl(i + 1, a);
  }

  submit(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    this.saving.set(true);
    this.serverError.set(null);
    const raw = this.form.getRawValue();
    // Flatten days → exercises with dayNumber + dayName
    const exercises: WorkoutExercise[] = [];
    let globalOrder = 0;
    raw.days.forEach((day: { dayName: string; exercises: Partial<WorkoutExercise>[] }, dayIdx: number) => {
      day.exercises.forEach((ex: Partial<WorkoutExercise>) => {
        exercises.push({
          ...ex,
          rir: null,
          restSeconds: null,
          dayNumber: dayIdx + 1,
          dayName: day.dayName,
          orderIndex: globalOrder++,
        } as WorkoutExercise);
      });
    });
    const payload = { name: raw.name, description: raw.description, objective: raw.objective, daysPerWeek: raw.days.length, exercises };
    const obs = this.isEdit
      ? this.workoutService.update(Number(this.id()), payload)
      : this.workoutService.create(payload);
    obs.subscribe({
      next: (w) => this.router.navigate(['/trainer/workouts', w.id]),
      error: () => { this.saving.set(false); this.serverError.set('No se pudo guardar la rutina.'); },
    });
  }
}
