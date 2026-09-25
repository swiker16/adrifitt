import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import {
  ExerciseProgress,
  LogTemplate,
  SaveWorkoutLogRequest,
  WorkoutDay,
  WorkoutLog,
} from '../../shared/models/workout-log.model';
import { API_BASE_URL } from '../config/api.config';

@Injectable({ providedIn: 'root' })
export class WorkoutLogService {
  private readonly http = inject(HttpClient);
  private readonly base = API_BASE_URL;

  // ── Client ──────────────────────────────────────────────────────────────

  myDays(): Observable<WorkoutDay[]> {
    return this.http.get<WorkoutDay[]>(`${this.base}/workout-logs/me/days`);
  }

  myTemplate(dayNumber: number): Observable<LogTemplate> {
    return this.http.get<LogTemplate>(`${this.base}/workout-logs/me/template`, {
      params: new HttpParams().set('dayNumber', dayNumber),
    });
  }

  findMine(): Observable<WorkoutLog[]> {
    return this.http.get<WorkoutLog[]>(`${this.base}/workout-logs/me`);
  }

  findMineById(id: number): Observable<WorkoutLog> {
    return this.http.get<WorkoutLog>(`${this.base}/workout-logs/me/${id}`);
  }

  create(request: SaveWorkoutLogRequest): Observable<WorkoutLog> {
    return this.http.post<WorkoutLog>(`${this.base}/workout-logs/me`, request);
  }

  update(id: number, request: SaveWorkoutLogRequest): Observable<WorkoutLog> {
    return this.http.put<WorkoutLog>(`${this.base}/workout-logs/me/${id}`, request);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/workout-logs/me/${id}`);
  }

  myProgress(): Observable<ExerciseProgress[]> {
    return this.http.get<ExerciseProgress[]>(`${this.base}/workout-logs/me/progress`);
  }

  // ── Trainer ─────────────────────────────────────────────────────────────

  findForClient(clientId: number): Observable<WorkoutLog[]> {
    return this.http.get<WorkoutLog[]>(`${this.base}/clients/${clientId}/workout-logs`);
  }

  progressForClient(clientId: number): Observable<ExerciseProgress[]> {
    return this.http.get<ExerciseProgress[]>(`${this.base}/clients/${clientId}/workout-logs/progress`);
  }
}
