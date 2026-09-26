export interface WorkoutExercise {
  id?: number;
  exerciseName: string;
  sets: number;
  reps: number | null;
  rir?: number | null;
  restSeconds?: number | null;
  notes?: string | null;
  orderIndex: number;
  dayNumber?: number | null;
  dayName?: string | null;
  warmUpSets?: string | null;
  approxReps?: string | null;
}

export interface Workout {
  id: number;
  name: string;
  description?: string | null;
  objective?: string | null;
  daysPerWeek?: number | null;
  createdAt: string;
  exercises: WorkoutExercise[];
}

export interface WorkoutRequest {
  name: string;
  description?: string | null;
  objective?: string | null;
  daysPerWeek?: number | null;
  exercises: WorkoutExercise[];
}

export interface ClientWorkout {
  id: number;
  clientId: number;
  workout: Workout;
  assignedAt: string;
  endDate: string | null;
  active: boolean;
}

export interface AssignWorkoutRequest {
  workoutId: number;
}
