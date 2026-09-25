export interface WorkoutDay {
  dayNumber: number;
  dayName: string;
  exerciseCount: number;
  totalSets: number;
}

export interface SetValue {
  setNumber: number;
  weightKg: number;
  reps: number | null;
  rir: number;
}

export interface TemplateExercise {
  exerciseId: number;
  exerciseName: string;
  sets: number;
  targetReps: number | null;
  approxReps: string | null;
  targetRir: number | null;
  restSeconds: number | null;
  notes: string | null;
  /** Values logged the last time this day was trained. */
  previous: SetValue[];
}

export interface LogTemplate {
  workoutId: number;
  workoutName: string;
  dayNumber: number;
  dayName: string;
  exercises: TemplateExercise[];
}

export interface LogSetRequest {
  exerciseId: number;
  setNumber: number;
  weightKg: number;
  reps?: number | null;
  rir: number;
}

export interface SaveWorkoutLogRequest {
  performedOn: string;
  dayNumber: number;
  notes?: string | null;
  sets: LogSetRequest[];
}

export interface LoggedExercise {
  exerciseId: number | null;
  exerciseName: string;
  sets: SetValue[];
}

export interface WorkoutLog {
  id: number;
  clientId: number;
  workoutId: number | null;
  workoutName: string;
  dayNumber: number;
  dayName: string;
  performedOn: string;
  notes: string | null;
  totalSets: number;
  totalVolumeKg: number;
  exercises: LoggedExercise[];
  createdAt: string;
}

export interface ExerciseProgressPoint {
  date: string;
  maxWeightKg: number;
  volumeKg: number;
  estimatedOneRepMaxKg: number | null;
  bestRir: number | null;
}

export interface ExerciseProgress {
  exerciseName: string;
  points: ExerciseProgressPoint[];
}
